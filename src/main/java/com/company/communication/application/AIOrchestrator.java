package com.company.communication.application;

import com.company.communication.application.port.FoundationModelPort;
import com.company.communication.application.port.FoundationModelPort.ModelExecutionResult;
import com.company.communication.application.port.FoundationModelPort.ToolCallTrace;
import com.company.communication.application.port.GuardrailPort;
import com.company.communication.application.port.GuardrailPort.ValidationOutcome;
import com.company.communication.application.port.KnowledgeRetrieverPort;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerContext;
import com.company.communication.domain.model.KnowledgeChunk;
import com.company.communication.exception.InvalidDecisionException;
import com.company.communication.infrastructure.ai.AgentPromptBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orquestrador de IA responsavel por consultar a base de conhecimento (RAG),
 * invocar o Foundation Model com Tool Calling e garantir validacao com no maximo
 * uma tentativa controlada de correcao.
 */
@Service
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private final KnowledgeRetrieverPort knowledgeRetriever;
    private final AgentPromptBuilder promptBuilder;
    private final FoundationModelPort foundationModel;
    private final GuardrailPort guardrailPort;
    private final ObjectMapper objectMapper;

    public AIOrchestrator(
            KnowledgeRetrieverPort knowledgeRetriever,
            AgentPromptBuilder promptBuilder,
            FoundationModelPort foundationModel,
            GuardrailPort guardrailPort,
            ObjectMapper objectMapper
    ) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.promptBuilder = promptBuilder;
        this.foundationModel = foundationModel;
        this.guardrailPort = guardrailPort;
        this.objectMapper = objectMapper;
    }

    public CommunicationDecision orchestrate(String executionId, CustomerContext context) {
        String ragQuery = buildRagQuery(context);
        List<KnowledgeChunk> retrievedChunks = knowledgeRetriever.retrieveRelevantKnowledge(
                ragQuery,
                context.boleto().productType(),
                context.eligibility().allowedChannels()
        );

        String docRefs = retrievedChunks.stream()
                .map(c -> c.sourceFile() + "(v" + c.documentVersion() + ")")
                .distinct()
                .collect(Collectors.joining(", "));
        log.info("executionId={} stage=STEP_5_RAG_RETRIEVAL docsRetrieved={} refs=[{}]",
                executionId, retrievedChunks.size(), docRefs);

        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(context, retrievedChunks);

        List<ToolCallTrace> allToolTraces = new ArrayList<>();
        CommunicationDecision candidateDecision = null;
        List<String> firstAttemptErrors;
        String firstAttemptRaw = "";

        try {
            ModelExecutionResult firstResult = foundationModel.generateDecision(systemPrompt, userPrompt);
            allToolTraces.addAll(firstResult.toolCalls());
            candidateDecision = firstResult.decision();
            firstAttemptRaw = serializeSafely(candidateDecision);

            ValidationOutcome outcome = guardrailPort.validateOutput(candidateDecision, context);
            if (outcome.valid()) {
                logModelExecution(executionId, allToolTraces, true, false, List.of());
                return candidateDecision;
            }
            firstAttemptErrors = outcome.violations();
        } catch (InvalidDecisionException ex) {
            firstAttemptErrors = ex.getViolations().isEmpty() ? List.of(ex.getMessage()) : ex.getViolations();
            if (firstAttemptRaw.isBlank()) {
                firstAttemptRaw = ex.getMessage();
            }
        }

        // Uma unica tentativa controlada de correcao indicando os erros estruturais/negocio
        log.warn("executionId={} stage=STEP_10_RETRY_CORRECTION violations={}", executionId, firstAttemptErrors);
        String correctionPrompt = promptBuilder.buildCorrectionUserPrompt(
                context,
                retrievedChunks,
                firstAttemptRaw,
                firstAttemptErrors
        );

        ModelExecutionResult retryResult = foundationModel.generateDecision(systemPrompt, correctionPrompt);
        allToolTraces.addAll(retryResult.toolCalls());
        CommunicationDecision correctedDecision = retryResult.decision();

        ValidationOutcome retryOutcome = guardrailPort.validateOutput(correctedDecision, context);
        if (!retryOutcome.valid()) {
            logModelExecution(executionId, allToolTraces, false, true, retryOutcome.violations());
            throw new InvalidDecisionException(
                    "Decisao da IA invalida apos tentativa controlada de correcao.",
                    retryOutcome.violations()
            );
        }

        logModelExecution(executionId, allToolTraces, true, true, List.of());
        return correctedDecision;
    }

    private String buildRagQuery(CustomerContext context) {
        return "Politicas de comunicacao, templates aprovados, regras do produto "
                + context.boleto().productType()
                + ", canal preferencial " + context.channelPreferences().preferredChannel()
                + ", elegibilidade " + context.eligibility().allowedChannels()
                + ", vencimento em " + context.boleto().daysUntilDue() + " dias"
                + ", tom " + context.behaviorProfile().preferredTone();
    }

    private void logModelExecution(
            String executionId,
            List<ToolCallTrace> toolTraces,
            boolean valid,
            boolean retried,
            List<String> violations
    ) {
        String toolsSummary = toolTraces.isEmpty()
                ? "none"
                : toolTraces.stream()
                        .map(t -> t.toolName() + "(" + t.argumentsSummary() + ")->" + t.durationMs() + "ms")
                        .collect(Collectors.joining("; "));
        log.info("executionId={} stage=STEP_10_OUTPUT_VALIDATION valid={} retried={} tools=[{}] violations={}",
                executionId, valid, retried, toolsSummary, violations);
    }

    private String serializeSafely(CommunicationDecision decision) {
        if (decision == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(decision);
        } catch (JsonProcessingException ex) {
            return decision.toString();
        }
    }
}
