package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationChannel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Resultado deterministico das regras de elegibilidade de comunicacao para o cliente e boleto.
 */
public record EligibilityResult(
        boolean eligible,
        List<CommunicationChannel> allowedChannels,
        Map<CommunicationChannel, String> blockedChannels,
        boolean suppressionRequired,
        String suppressionReason,
        OffsetDateTime recommendedWindowStart,
        OffsetDateTime recommendedWindowEnd
) {
}
