package com.company.communication.infrastructure.guardrails;

import com.company.communication.application.port.GuardrailPort;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementacao local dos guardrails em Java (profile local).
 * No profile aws, pode ser substituida por integracao com Amazon Bedrock Guardrails.
 */
@Service
@Profile({"local", "default"})
public class GuardrailService implements GuardrailPort {

    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;

    public GuardrailService(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail) {
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    @Override
    public void validateInput(CustomerContext context) {
        inputGuardrail.validate(context);
    }

    @Override
    public ValidationOutcome validateOutput(CommunicationDecision decision, CustomerContext context) {
        return outputGuardrail.validate(decision, context);
    }
}
