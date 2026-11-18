package com.company.communication.application.port;

import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerContext;

import java.util.List;

/**
 * Abstracao de guardrails de entrada e saida.
 * No profile local utiliza implementacoes Java; no profile aws pode delegar ao Amazon Bedrock Guardrails.
 */
public interface GuardrailPort {

    void validateInput(CustomerContext context);

    ValidationOutcome validateOutput(CommunicationDecision decision, CustomerContext context);

    /**
     * Resultado estruturado da validacao de saida.
     */
    record ValidationOutcome(boolean valid, List<String> violations) {
        public static ValidationOutcome ok() {
            return new ValidationOutcome(true, List.of());
        }

        public static ValidationOutcome failed(List<String> violations) {
            return new ValidationOutcome(false, List.copyOf(violations));
        }
    }
}
