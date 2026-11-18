package com.company.communication.infrastructure.guardrails;

import com.company.communication.application.port.GuardrailPort.ValidationOutcome;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerContext;
import com.company.communication.service.CommunicationDecisionValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Guardrail de saida que aplica validacao linguistica de compliance e delega as regras de negocio
 * deterministicas para {@link CommunicationDecisionValidator}.
 */
@Component
public class OutputGuardrail {

    private static final Pattern THREATENING_LANGUAGE = Pattern.compile(
            "(?i)\\b(negativacao|serasa|spc|bloqueio imediato|acao judicial|penhora|protesto imediato|ameaca)\\b"
    );

    private final CommunicationDecisionValidator decisionValidator;

    public OutputGuardrail(CommunicationDecisionValidator decisionValidator) {
        this.decisionValidator = decisionValidator;
    }

    public ValidationOutcome validate(CommunicationDecision decision, CustomerContext context) {
        List<String> violations = new ArrayList<>(decisionValidator.validate(decision, context));

        if (decision != null && decision.variables() != null) {
            for (Map.Entry<String, String> entry : decision.variables().entrySet()) {
                if (entry.getValue() != null && THREATENING_LANGUAGE.matcher(entry.getValue()).find()) {
                    violations.add("Variavel '" + entry.getKey() + "' contem linguagem ameacadora ou coercitiva proibida.");
                }
            }
        }

        return violations.isEmpty() ? ValidationOutcome.ok() : ValidationOutcome.failed(violations);
    }
}
