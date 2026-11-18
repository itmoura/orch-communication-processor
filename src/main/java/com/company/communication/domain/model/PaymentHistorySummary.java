package com.company.communication.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Historico resumido de pagamentos do cliente.
 */
public record PaymentHistorySummary(
        String customerId,
        int recentPaymentsCount,
        int onTimeCount,
        int lateCount,
        int maxDelayDays,
        int averageDelayDays,
        List<PaymentEntry> recentPayments
) {
    /**
     * Registro resumido de um pagamento recente de boleto.
     */
    public record PaymentEntry(
            String boletoId,
            LocalDate dueDate,
            OffsetDateTime paidAt,
            int delayDays,
            String status
    ) {
    }
}
