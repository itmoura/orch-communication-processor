package com.company.communication.service;

import com.company.communication.domain.enums.CommunicationAction;
import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.CommunicationReasonCode;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerContext;
import com.company.communication.domain.model.EligibilityResult;
import com.company.communication.domain.model.TemplateDefinition;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validador deterministico das decisoes recomendadas pelo AI Orchestrator (Stage 10).
 * A IA recomenda; o Java valida estruturalmente e reaplica as regras de negocio.
 */
@Service
public class CommunicationDecisionValidator {

    private static final LocalTime POLICY_START = LocalTime.of(8, 0);
    private static final LocalTime POLICY_END = LocalTime.of(20, 0);
    private static final Pattern CPF_PATTERN = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b|\\b\\d{11}\\b");
    private static final Pattern BARCODE_PATTERN = Pattern.compile("\\d{44,48}");
    private static final Pattern DUE_DATE_BR_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");

    private final TemplateCatalogService templateCatalogService;

    public CommunicationDecisionValidator(TemplateCatalogService templateCatalogService) {
        this.templateCatalogService = templateCatalogService;
    }

    public List<String> validate(CommunicationDecision decision, CustomerContext context) {
        List<String> violations = new ArrayList<>();

        if (decision == null) {
            violations.add("Decisao nao pode ser nula.");
            return violations;
        }
        if (decision.action() == null) {
            violations.add("Campo obrigatorio 'action' ausente.");
        }
        if (decision.reasonCode() == null) {
            violations.add("Campo obrigatorio 'reasonCode' ausente.");
        }
        if (!violations.isEmpty()) {
            return violations;
        }

        EligibilityResult eligibility = context.eligibility();

        if (decision.action() == CommunicationAction.SUPPRESS) {
            validateSuppressDecision(decision, violations);
            return violations;
        }

        // Reaplicar elegibilidade deterministica: se supressao for obrigatoria, nao pode enviar
        if (eligibility.suppressionRequired()) {
            violations.add("Cliente nao elegivel para envio (" + eligibility.suppressionReason()
                    + "); acao obrigatoria deve ser SUPPRESS.");
            return violations;
        }

        validateSendDecision(decision, context, violations);
        return violations;
    }

    private void validateSuppressDecision(CommunicationDecision decision, List<String> violations) {
        if (decision.channel() != null) {
            violations.add("Para action=SUPPRESS, o campo 'channel' deve ser nulo.");
        }
        if (decision.sendAt() != null) {
            violations.add("Para action=SUPPRESS, o campo 'sendAt' deve ser nulo.");
        }
        if (decision.templateId() != null && !decision.templateId().isBlank()) {
            violations.add("Para action=SUPPRESS, o campo 'templateId' deve ser nulo.");
        }
        if (decision.fallbackChannel() != null) {
            violations.add("Para action=SUPPRESS, o campo 'fallbackChannel' deve ser nulo.");
        }
    }

    private void validateSendDecision(
            CommunicationDecision decision,
            CustomerContext context,
            List<String> violations
    ) {
        List<CommunicationChannel> allowedChannels = context.eligibility().allowedChannels();
        ProductType productType = context.boleto().productType();

        // Canal principal
        if (decision.channel() == null) {
            violations.add("Para action=SEND, o campo 'channel' e obrigatorio.");
        } else if (!allowedChannels.contains(decision.channel())) {
            violations.add("Canal recomendado '" + decision.channel()
                    + "' nao e permitido pela elegibilidade. Canais permitidos: " + allowedChannels);
        }

        // Canal alternativo (fallback)
        if (decision.fallbackChannel() != null) {
            if (!allowedChannels.contains(decision.fallbackChannel())) {
                violations.add("fallbackChannel '" + decision.fallbackChannel()
                        + "' nao esta entre os canais permitidos: " + allowedChannels);
            }
            if (decision.fallbackChannel() == decision.channel()) {
                violations.add("fallbackChannel deve ser diferente do canal principal.");
            }
        }

        // Horario de envio
        validateSendAt(decision.sendAt(), context.boleto().occurredAt(), violations);

        // Template existente, aprovado e compativel com canal e produto
        TemplateDefinition template = null;
        if (decision.templateId() == null || decision.templateId().isBlank()) {
            violations.add("Para action=SEND, o campo 'templateId' e obrigatorio.");
        } else {
            template = templateCatalogService.findById(decision.templateId()).orElse(null);
            if (template == null || !template.approved()) {
                violations.add("Template '" + decision.templateId()
                        + "' nao existe ou nao esta aprovado. Templates aprovados: "
                        + templateCatalogService.getApprovedTemplateIds());
            } else {
                if (decision.channel() != null && !template.supportedChannels().contains(decision.channel())) {
                    violations.add("Template '" + template.templateId()
                            + "' nao suporta o canal '" + decision.channel() + "'.");
                }
                if (!template.supportedProducts().contains(productType)) {
                    violations.add("Template '" + template.templateId()
                            + "' nao e aplicavel ao produto '" + productType + "'.");
                }
            }
        }

        // Variaveis obrigatorias e ausencia de dados sensiveis
        validateVariables(decision.variables(), template, violations);

        // Regra de negocio: boleto nao vencido nao pode usar template/motivo de atraso
        if (context.boleto().daysUntilDue() >= 0) {
            if ("OVERDUE_SUPPORT_NOTICE".equals(decision.templateId())) {
                violations.add("Template 'OVERDUE_SUPPORT_NOTICE' nao pode ser usado para boleto nao vencido (daysUntilDue="
                        + context.boleto().daysUntilDue() + "). Use BOLETO_REMINDER, NEAR_DUE_DATE_ALERT ou PAYMENT_GUIDANCE_EMAIL.");
            }
            if (decision.reasonCode() == CommunicationReasonCode.OVERDUE_GUIDANCE) {
                violations.add("reasonCode 'OVERDUE_GUIDANCE' nao pode ser usado para boleto nao vencido (daysUntilDue="
                        + context.boleto().daysUntilDue() + ").");
            }
        }
    }

    private void validateSendAt(OffsetDateTime sendAt, OffsetDateTime occurredAt, List<String> violations) {
        if (sendAt == null) {
            violations.add("Para action=SEND, o campo 'sendAt' e obrigatorio.");
            return;
        }
        if (sendAt.isBefore(occurredAt)) {
            violations.add("Horario 'sendAt' (" + sendAt + ") nao pode ser anterior a ocorrencia do boleto (" + occurredAt + ").");
        }
        ZoneOffset expectedOffset = occurredAt.getOffset();
        LocalTime localTimeInEventOffset = sendAt.withOffsetSameInstant(expectedOffset).toLocalTime();
        if (localTimeInEventOffset.isBefore(POLICY_START) || localTimeInEventOffset.isAfter(POLICY_END)) {
            violations.add("Horario 'sendAt' (" + localTimeInEventOffset + ") fora da janela permitida pelas politicas (08:00 as 20:00).");
        }
    }

    private void validateVariables(
            Map<String, String> variables,
            TemplateDefinition template,
            List<String> violations
    ) {
        if (variables == null || variables.isEmpty()) {
            violations.add("Para action=SEND, o mapa 'variables' e obrigatorio.");
            return;
        }

        if (template != null) {
            for (String requiredVar : template.requiredVariables()) {
                String value = variables.get(requiredVar);
                if (value == null || value.isBlank()) {
                    violations.add("Variavel obrigatoria '" + requiredVar + "' ausente para o template '" + template.templateId() + "'.");
                }
            }
        }

        String dueDateVar = variables.get("dueDate");
        if (dueDateVar != null && !dueDateVar.isBlank() && !DUE_DATE_BR_PATTERN.matcher(dueDateVar.trim()).matches()) {
            violations.add("Variavel 'dueDate' deve estar formatada como dd/MM/yyyy, recebido: '" + dueDateVar + "'.");
        }

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (CPF_PATTERN.matcher(value).find()) {
                violations.add("Variavel '" + entry.getKey() + "' contem padrao semelhante a CPF, proibido pelos guardrails.");
            }
            if (BARCODE_PATTERN.matcher(value.replaceAll("[.\\s-]", "")).find()) {
                violations.add("Variavel '" + entry.getKey() + "' contem padrao de codigo de barras/linha digitavel, proibido pelos guardrails.");
            }
        }
    }
}
