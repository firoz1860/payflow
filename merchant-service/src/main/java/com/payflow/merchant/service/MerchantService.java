package com.payflow.merchant.service;
import com.payflow.common.error.ErrorCode;
import com.payflow.common.error.PayFlowException;
import com.payflow.common.event.Topics;
import com.payflow.common.outbox.OutboxRecorder;
import com.payflow.merchant.domain.Merchant;
import com.payflow.merchant.dto.MerchantDtos;
import com.payflow.merchant.repository.MerchantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
@Service
public class MerchantService {
    private static final Logger log = LoggerFactory.getLogger(MerchantService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no I/O/0/1
    private final MerchantRepository merchantRepository;
    private final ApiKeyService apiKeyService;
    private final OutboxRecorder outbox;
    public MerchantService(MerchantRepository merchantRepository, ApiKeyService apiKeyService,
                           OutboxRecorder outbox) {
        this.merchantRepository = merchantRepository;
        this.apiKeyService = apiKeyService;
        this.outbox = outbox;
    }
    @Transactional
    public MerchantDtos.MerchantResponse create(MerchantDtos.CreateMerchantRequest request) {
        if (merchantRepository.existsByEmailIgnoreCase(request.email())) {
            throw PayFlowException.conflict(ErrorCode.CONFLICT,
                    "A merchant already exists for this email address");
        }
        Merchant merchant = new Merchant(generateMerchantCode(), request.businessName().trim(),
                request.email(), request.phone(), request.country(), request.defaultCurrency());
        merchantRepository.save(merchant);
        outbox.record("Merchant", merchant.getId().toString(), Topics.NOTIFICATION_REQUESTED, 1,
                Map.of("template", "MERCHANT_REGISTERED",
                        "channel", "EMAIL",
                        "recipient", merchant.getEmail(),
                        "variables", Map.of("businessName", merchant.getBusinessName(),
                                "merchantCode", merchant.getMerchantCode())));
        audit(null, merchant.getId(), "MERCHANT_CREATED", merchant.getId().toString());
        log.info("Created merchant {} ({})", merchant.getMerchantCode(), merchant.getId());
        return toResponse(merchant);
    }
    @Transactional(readOnly = true)
    public MerchantDtos.MerchantResponse get(UUID merchantId) {
        return toResponse(load(merchantId));
    }
    @Transactional(readOnly = true)
    public Page<MerchantDtos.MerchantResponse> list(Pageable pageable) {
        return merchantRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }
    @Transactional
    public MerchantDtos.MerchantResponse update(UUID merchantId, UUID actorId,
                                                MerchantDtos.UpdateMerchantRequest request) {
        Merchant merchant = load(merchantId);
        merchant.updateProfile(request.businessName(), request.phone(), request.defaultCurrency());
        audit(actorId, merchantId, "MERCHANT_UPDATED", merchantId.toString());
        return toResponse(merchant);
    }
    @Transactional
    public MerchantDtos.MerchantResponse changeStatus(UUID merchantId, UUID actorId,
                                                      MerchantDtos.ChangeStatusRequest request) {
        Merchant merchant = load(merchantId);
        Merchant.Status previous = merchant.getStatus();
        merchant.changeStatus(request.status(), request.reason());
        if (request.status() == Merchant.Status.SUSPENDED || request.status() == Merchant.Status.BLOCKED) {
            int revoked = apiKeyService.revokeAllForMerchant(merchantId);
            log.warn("Merchant {} moved {} -> {}; revoked {} API keys",
                    merchantId, previous, request.status(), revoked);
        }
        outbox.record("Merchant", merchantId.toString(), Topics.NOTIFICATION_REQUESTED, 1,
                Map.of("template", "MERCHANT_STATUS_CHANGED",
                        "channel", "EMAIL",
                        "recipient", merchant.getEmail(),
                        "variables", Map.of("previousStatus", previous.name(),
                                "newStatus", request.status().name(),
                                "reason", request.reason() == null ? "" : request.reason())));
        audit(actorId, merchantId, "MERCHANT_STATUS_CHANGED", merchantId.toString());
        return toResponse(merchant);
    }
    @Transactional
    public MerchantDtos.MerchantResponse enableLiveMode(UUID merchantId, UUID actorId) {
        Merchant merchant = load(merchantId);
        if (merchant.getStatus() != Merchant.Status.ACTIVE) {
            throw PayFlowException.unprocessable(ErrorCode.MERCHANT_NOT_ACTIVE,
                    "Merchant must be ACTIVE before live mode can be enabled");
        }
        merchant.enableLiveMode();
        audit(actorId, merchantId, "LIVE_MODE_ENABLED", merchantId.toString());
        return toResponse(merchant);
    }
    @Transactional
    public MerchantDtos.MerchantResponse updatePricing(UUID merchantId, UUID actorId,
                                                       MerchantDtos.UpdatePricingRequest request) {
        Merchant merchant = load(merchantId);
        merchant.updatePricing(request.feePercentage(), request.fixedFee(), request.settlementDelayDays());
        audit(actorId, merchantId, "MERCHANT_PRICING_UPDATED", merchantId.toString());
        return toResponse(merchant);
    }
    private Merchant load(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> PayFlowException.notFound("Merchant not found"));
    }
    private String generateMerchantCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder builder = new StringBuilder("MRC_");
            for (int i = 0; i < 10; i++) {
                builder.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String code = builder.toString();
            if (!merchantRepository.existsByMerchantCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate a unique merchant code");
    }
    private void audit(UUID actorId, UUID merchantId, String action, String entityId) {
        outbox.record("Merchant", entityId, Topics.AUDIT_EVENT, 1, merchantId.toString(),
                Map.of("actorType", actorId == null ? "SYSTEM" : "USER",
                        "actorId", actorId == null ? "system" : actorId.toString(),
                        "merchantId", merchantId.toString(),
                        "action", action,
                        "entityType", "Merchant",
                        "entityId", entityId));
    }
    private MerchantDtos.MerchantResponse toResponse(Merchant m) {
        return new MerchantDtos.MerchantResponse(m.getId(), m.getMerchantCode(), m.getBusinessName(),
                m.getEmail(), m.getPhone(), m.getStatus().name(), m.getCountry(), m.getDefaultCurrency(),
                m.getFeePercentage(), m.getFixedFee(), m.getSettlementDelayDays(),
                m.isLiveModeEnabled(), m.getCreatedAt(), m.getUpdatedAt());
    }
}
