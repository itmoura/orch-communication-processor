package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.CommunicationTone;
import com.company.communication.domain.enums.PreferredPeriod;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Perfil comportamental do cliente recuperado da fonte de dados de relacionamento.
 */
public record CustomerBehaviorProfile(
        String customerId,
        String firstName,
        CommunicationChannel preferredChannel,
        PreferredPeriod preferredPeriod,
        CommunicationTone preferredTone,
        int onTimePayments,
        int latePayments,
        int averageDelayDays,
        OffsetDateTime lastPaymentAt,
        List<CommunicationChannel> eligibleChannels,
        Map<CommunicationChannel, String> blockedChannels,
        boolean optedOut,
        int recentCommunicationsCount,
        OffsetDateTime lastCommunicationAt,
        CommunicationChannel lastCommunicationChannel
) {
}
