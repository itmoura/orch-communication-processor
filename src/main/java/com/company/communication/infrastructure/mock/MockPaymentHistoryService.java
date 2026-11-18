package com.company.communication.infrastructure.mock;

import com.company.communication.application.port.PaymentHistoryPort;
import com.company.communication.domain.model.PaymentHistorySummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador local que carrega o historico de pagamentos ficticio a partir de JSON.
 */
@Service
@Profile({"local", "default"})
public class MockPaymentHistoryService implements PaymentHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentHistoryService.class);

    private final ObjectMapper objectMapper;
    private final Resource paymentHistoryResource;
    private Map<String, PaymentHistorySummary> historyByCustomer = Map.of();

    public MockPaymentHistoryService(
            ObjectMapper objectMapper,
            @Value("${app.mock.payment-history-path}") Resource paymentHistoryResource
    ) {
        this.objectMapper = objectMapper;
        this.paymentHistoryResource = paymentHistoryResource;
    }

    @PostConstruct
    void loadPaymentHistory() throws IOException {
        try (InputStream is = paymentHistoryResource.getInputStream()) {
            List<PaymentHistorySummary> entries = objectMapper.readValue(
                    is,
                    new TypeReference<>() {}
            );
            this.historyByCustomer = entries.stream()
                    .collect(Collectors.toUnmodifiableMap(PaymentHistorySummary::customerId, Function.identity()));
            log.info("Carregados {} historicos de pagamento mockados de {}", historyByCustomer.size(), paymentHistoryResource.getFilename());
        }
    }

    @Override
    public Optional<PaymentHistorySummary> findByCustomerId(String customerId) {
        return Optional.ofNullable(historyByCustomer.get(customerId));
    }
}
