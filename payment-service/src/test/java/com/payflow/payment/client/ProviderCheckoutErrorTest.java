package com.payflow.payment.client;
import com.payflow.common.client.ServiceClientFactory;
import com.payflow.common.error.PayFlowException;
import com.payflow.payment.dto.CheckoutDtos;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ProviderCheckoutErrorTest {
    @Test void badProviderSignatureIsForbiddenWithoutRefreshingUserSession() {
        var factory=mock(ServiceClientFactory.class);
        when(factory.create("http://provider")).thenReturn(WebClient.builder().baseUrl("http://provider").exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build())).build());
        var client=new ProviderClient(factory,"http://provider");
        var error=assertThrows(PayFlowException.class,()->client.verifyCheckout(new CheckoutDtos.VerificationRequest("order_one","pay_real","a".repeat(64))));
        assertEquals(403,error.getStatus().value());
    }
}
