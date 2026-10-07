package com.payflow.provider.controller;
import com.payflow.provider.domain.ProviderEvent;
import com.payflow.provider.repository.ProviderEventRepository;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;
@RestController
@RequestMapping("/internal/ai/providers")
@Hidden
public class InternalAiProviderController {
    private final ProviderEventRepository eventRepository;
    public InternalAiProviderController(ProviderEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    @GetMapping("/payments/{providerPaymentId}")
    public ProviderEventsEvidence events(@PathVariable String providerPaymentId,
                                         @RequestParam(required = false) String provider) {
        List<ProviderEvent> events = provider == null
                ? eventRepository.findByProviderPaymentIdOrderByReceivedAtDesc(providerPaymentId)
                : eventRepository.findByProviderAndProviderPaymentIdOrderByReceivedAtDesc(
                        provider, providerPaymentId);
        List<ProviderEventEvidence> eventEvidence = events.stream()
                .map(this::toEventEvidence)
                .toList();
        return new ProviderEventsEvidence(provider, providerPaymentId, eventEvidence);
    }
    private ProviderEventEvidence toEventEvidence(ProviderEvent event) {
        return new ProviderEventEvidence(
                event.getProviderEventId(),
                event.getEventType(),
                event.getProcessingStatus().name(),
                event.getReceivedAt(),
                event.getProcessedAt(),
                event.getFailureReason());
    }
    public record ProviderEventsEvidence(
            String provider,
            String providerPaymentId,
            List<ProviderEventEvidence> events
    ) {
    }
    public record ProviderEventEvidence(
            String providerEventId,
            String eventType,
            String processingStatus,
            Instant receivedAt,
            Instant processedAt,
            String failureReason
    ) {
    }
}
