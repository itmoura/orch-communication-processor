package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.CommunicationTone;
import com.company.communication.domain.enums.PreferredPeriod;
import com.company.communication.domain.enums.ProductType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Contexto tipado e sanitizado enviado ao AI Orchestrator para tomada de decisao.
 * Nao contem identificadores pessoais desnecessarios, codigo de barras ou linha digitavel.
 */
public record CustomerContext(
        BoletoSummary boleto,
        CustomerReference customer,
        BehaviorSummary behaviorProfile,
        PaymentMetrics paymentHistory,
        CommunicationHistorySummary communicationHistory,
        ChannelPreferences channelPreferences,
        ProductSummary productSummary,
        EligibilityResult eligibility
) {
    public record BoletoSummary(
            String eventId,
            String boletoId,
            Long amountInCents,
            LocalDate dueDate,
            long daysUntilDue,
            ProductType productType,
            OffsetDateTime occurredAt
    ) {
    }

    public record CustomerReference(
            String customerId,
            String firstName
    ) {
    }

    public record BehaviorSummary(
            int onTimePayments,
            int latePayments,
            int averageDelayDays,
            OffsetDateTime lastPaymentAt,
            CommunicationTone preferredTone
    ) {
    }

    public record PaymentMetrics(
            int recentPaymentsCount,
            int onTimeCount,
            int lateCount
    ) {
    }

    public record ProductSummary(
            ProductType productType,
            String displayName,
            List<CommunicationChannel> allowedChannels
    ) {
    }

    public record CommunicationHistorySummary(
            int recentCommunicationsCount,
            OffsetDateTime lastCommunicationAt,
            CommunicationChannel lastCommunicationChannel
    ) {
    }

    public record ChannelPreferences(
            CommunicationChannel preferredChannel,
            PreferredPeriod preferredPeriod,
            List<CommunicationChannel> eligibleChannels
    ) {
    }
}
