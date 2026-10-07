package com.payflow.ai.investigation;

import com.payflow.ai.capability.PayFlowCapabilities;
import com.payflow.ai.tools.GetLedgerEvidenceTool;
import com.payflow.ai.tools.GetPaymentTool;
import com.payflow.ai.tools.GetProviderEvidenceTool;
import com.payflow.ai.tools.ToolContext;
import com.payflow.ai.tools.ToolResult;
import com.payflow.common.security.PayFlowPrincipal;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Deterministic aggregator that turns a payment reference into a structured
 * {@link InvestigationResult} by orchestrating the read-only evidence tools. All
 * reasoning that decides fact vs inference vs missing, and the confidence level, is
 * done HERE in code — the LLM only narrates the result and never queries a service.
 *
 * <p>Tenancy is enforced inside each tool (payment/ledger evidence must belong to
 * the caller's merchant); a cross-tenant reference comes back as "not found" so the
 * existence of another merchant's payment is never disclosed.
 */
@Service
public class PaymentInvestigationService {

    private static final String PAYMENT_SERVICE = "payment-service";
    private static final String PROVIDER_SERVICE = "provider-service";
    private static final String LEDGER_SERVICE = "ledger-service";

    private final GetPaymentTool getPaymentTool;
    private final GetProviderEvidenceTool getProviderEvidenceTool;
    private final GetLedgerEvidenceTool getLedgerEvidenceTool;

    public PaymentInvestigationService(GetPaymentTool getPaymentTool,
                                       GetProviderEvidenceTool getProviderEvidenceTool,
                                       GetLedgerEvidenceTool getLedgerEvidenceTool) {
        this.getPaymentTool = getPaymentTool;
        this.getProviderEvidenceTool = getProviderEvidenceTool;
        this.getLedgerEvidenceTool = getLedgerEvidenceTool;
    }

    public InvestigationResult investigate(PayFlowPrincipal principal, String paymentReference) {
        return investigate(principal, paymentReference, null);
    }

    public InvestigationResult investigate(PayFlowPrincipal principal, String paymentReference,
                                           Consumer<ToolResult> progress) {
        List<Fact> facts = new ArrayList<>();
        List<String> inferences = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<EvidenceRef> refs = new ArrayList<>();
        List<ToolResult> executed = new ArrayList<>();

        // 1) Payment (authoritative, tenant-enforced).
        ToolResult paymentResult = run(getPaymentTool.run(
                ToolContext.of(principal, Map.of("reference", nullSafe(paymentReference)))), executed, progress);

        if (!paymentResult.hasData()) {
            // Covers genuine 404 AND cross-tenant denial — never reveal cross-tenant existence.
            missing.add("payment not found for this merchant");
            return new InvestigationResult(paymentReference, false, List.of(), List.of(),
                    List.copyOf(missing), List.of(), Confidence.UNKNOWN, List.of(), List.copyOf(executed));
        }

        Map<String, Object> payment = asMap(paymentResult.data());
        String status = str(payment, "status");
        String provider = str(payment, "provider");
        String providerPaymentId = str(payment, "providerPaymentId");

        boolean statusPresent = status != null && !status.isBlank();
        if (statusPresent) {
            facts.add(new Fact("PAYMENT_STATUS", status, PAYMENT_SERVICE));
        }
        if (provider != null && !provider.isBlank()) {
            facts.add(new Fact("PROVIDER", provider, PAYMENT_SERVICE));
        }
        refs.add(new EvidenceRef(PAYMENT_SERVICE, "payment", paymentReference, true));

        // 2) Provider evidence (keyed by the provider payment id from the caller's own payment).
        boolean providerEventFound = false;
        ToolResult providerResult = run(getProviderEvidenceTool.run(ToolContext.of(principal, Map.of(
                "providerPaymentId", nullSafe(providerPaymentId),
                "provider", nullSafe(provider)))), executed, progress);
        if (providerResult.hasData()) {
            List<Object> events = asList(asMap(providerResult.data()).get("events"));
            if (!events.isEmpty()) {
                providerEventFound = true;
                Map<String, Object> latest = asMap(events.get(0));
                String processingStatus = str(latest, "processingStatus");
                String eventType = str(latest, "eventType");
                if (processingStatus != null) {
                    facts.add(new Fact("PROVIDER_EVENT",
                            (eventType == null ? "" : eventType + " ") + "processingStatus=" + processingStatus,
                            PROVIDER_SERVICE));
                }
                refs.add(new EvidenceRef(PROVIDER_SERVICE, "provider_event", providerPaymentId, true));
            }
        }
        if (!providerEventFound) {
            missing.add("No provider event evidence found for this payment.");
        }

        // 3) Ledger evidence (tenant-enforced).
        boolean ledgerPostingFound = false;
        Boolean balanced = null;
        ToolResult ledgerResult = run(getLedgerEvidenceTool.run(
                ToolContext.of(principal, Map.of("reference", nullSafe(paymentReference)))), executed, progress);
        if (ledgerResult.hasData()) {
            List<Object> postings = asList(asMap(ledgerResult.data()).get("postings"));
            if (!postings.isEmpty()) {
                ledgerPostingFound = true;
                Map<String, Object> posting = asMap(postings.get(0));
                balanced = asBool(posting.get("balanced"));
                facts.add(new Fact("LEDGER_POSTING", "balanced=" + balanced, LEDGER_SERVICE));
                refs.add(new EvidenceRef(LEDGER_SERVICE, "ledger_posting", str(posting, "postingId"), true));
                if (Boolean.FALSE.equals(balanced)) {
                    warnings.add("A ledger posting exists for this payment but is NOT balanced — escalate to finance.");
                }
            }
        }
        if (!ledgerPostingFound) {
            missing.add("No ledger posting found for this payment.");
        }

        // 4) Planned-but-unbuilt services: state them as not implemented, never fabricate.
        addPlannedServiceGap(missing, "settlement",
                "Settlement status unavailable — settlement-service is not implemented in this deployment.");
        addPlannedServiceGap(missing, "reconciliation",
                "Reconciliation status unavailable — reconciliation-service is not implemented in this deployment.");

        // 5) Inferences — derived, clearly labelled, never promoted to facts.
        if ("CAPTURED".equalsIgnoreCase(status) && providerEventFound && Boolean.TRUE.equals(balanced)) {
            inferences.add("Provider confirmed the payment and a balanced ledger posting exists "
                    + "-> the payment most likely completed normally.");
        } else if ("CAPTURED".equalsIgnoreCase(status) && !ledgerPostingFound) {
            inferences.add("Payment status is CAPTURED but no ledger posting was found "
                    + "-> ledger write may be pending or missing; verify before treating funds as settled.");
        } else if ("FAILED".equalsIgnoreCase(status)) {
            inferences.add("Payment is in a FAILED state -> inspect the latest attempt's failure code/message "
                    + "to explain the decline.");
        } else if (statusPresent && !providerEventFound) {
            inferences.add("No provider event has been recorded for this payment "
                    + "-> the provider may not have reported yet, or the webhook was not received.");
        }

        Confidence confidence = confidenceFor(statusPresent, providerEventFound, ledgerPostingFound);

        return new InvestigationResult(paymentReference, true,
                List.copyOf(facts), List.copyOf(inferences), List.copyOf(missing), List.copyOf(warnings),
                confidence, List.copyOf(refs), List.copyOf(executed));
    }

    private static Confidence confidenceFor(boolean status, boolean providerEvent, boolean ledger) {
        if (!status) {
            return Confidence.UNKNOWN;
        }
        if (providerEvent && ledger) {
            return Confidence.HIGH;
        }
        if (providerEvent || ledger) {
            return Confidence.MEDIUM;
        }
        return Confidence.LOW;
    }

    private static void addPlannedServiceGap(List<String> missing, String key, String message) {
        PayFlowCapabilities.Capability capability = PayFlowCapabilities.byKey(key);
        if (capability != null && capability.status() == PayFlowCapabilities.Status.PLANNED) {
            missing.add(message);
        }
    }

    private static ToolResult run(ToolResult result, List<ToolResult> executed, Consumer<ToolResult> progress) {
        executed.add(result);
        if (progress != null) {
            progress.accept(result);
        }
        return result;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : List.of();
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }
}
