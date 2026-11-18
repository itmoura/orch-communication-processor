package com.company.communication.application.port;

import com.company.communication.domain.model.PaymentHistorySummary;

import java.util.Optional;

/**
 * Porta de acesso ao historico resumido de pagamentos do cliente.
 */
public interface PaymentHistoryPort {

    Optional<PaymentHistorySummary> findByCustomerId(String customerId);
}
