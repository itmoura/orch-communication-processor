package com.company.communication.infrastructure.guardrails;

import com.company.communication.domain.model.CustomerContext;
import com.company.communication.exception.GuardrailViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Guardrail de entrada executado antes de consultar o modelo de IA.
 * Valida integridade do contexto, bloqueia vazamento de dados sensiveis e mitiga prompt injection.
 */
@Component
public class InputGuardrail {

    private static final Pattern CPF_PATTERN = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b|\\b\\d{11}\\b");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("\\d{44,48}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|above)\\s+instructions"),
            Pattern.compile("(?i)disregard\\s+(all\\s+)?(previous|system)\\s+instructions"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+"),
            Pattern.compile("(?i)act\\s+as\\s+system"),
            Pattern.compile("(?i)<\\|im_start\\|>|<<SYS>>|\\[INST\\]")
    );

    public void validate(CustomerContext context) {
        if (context == null || context.boleto() == null || context.customer() == null || context.eligibility() == null) {
            throw new GuardrailViolationException("CustomerContext invalido ou incompleto.");
        }

        if (context.boleto().amountInCents() == null || context.boleto().amountInCents() <= 0) {
            throw new GuardrailViolationException("Valor do boleto invalido no contexto.");
        }

        if (context.customer().firstName() == null || context.customer().firstName().isBlank()) {
            throw new GuardrailViolationException("Primeiro nome do cliente ausente no contexto.");
        }

        checkSanitizedField("customerId", context.customer().customerId());
        checkSanitizedField("firstName", context.customer().firstName());
        checkSanitizedField("boletoId", context.boleto().boletoId());
        checkSanitizedField("eventId", context.boleto().eventId());
    }

    private void checkSanitizedField(String fieldName, String value) {
        if (value == null) {
            throw new GuardrailViolationException("Campo obrigatorio '" + fieldName + "' ausente no contexto.");
        }
        String compactDigits = value.replaceAll("[.\\s-]", "");
        if (BARCODE_PATTERN.matcher(compactDigits).find()) {
            throw new GuardrailViolationException(
                    "Campo '" + fieldName + "' contem possivel codigo de barras ou linha digitavel, bloqueado pelo guardrail."
            );
        }
        if (CPF_PATTERN.matcher(value).find()) {
            throw new GuardrailViolationException(
                    "Campo '" + fieldName + "' contem possivel CPF, bloqueado pelo guardrail de entrada."
            );
        }
        if (EMAIL_PATTERN.matcher(value).find()) {
            throw new GuardrailViolationException(
                    "Campo '" + fieldName + "' contem endereco de e-mail pessoal, bloqueado pelo guardrail de entrada."
            );
        }
        for (Pattern injection : INJECTION_PATTERNS) {
            if (injection.matcher(value).find()) {
                throw new GuardrailViolationException(
                        "Possivel tentativa de prompt injection detectada no campo '" + fieldName + "'."
                );
            }
        }
    }
}
