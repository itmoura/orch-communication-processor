package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationAction;
import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.CommunicationReasonCode;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Decisao estruturada de comunicacao produzida pelo AI Orchestrator e validada pelo backend Java.
 */
public record CommunicationDecision(
        @NotNull(message = "action e obrigatorio")
        CommunicationAction action,
        CommunicationChannel channel,
        OffsetDateTime sendAt,
        String templateId,
        Map<String, String> variables,
        CommunicationChannel fallbackChannel,
        @NotNull(message = "reasonCode e obrigatorio")
        CommunicationReasonCode reasonCode
) {
}
