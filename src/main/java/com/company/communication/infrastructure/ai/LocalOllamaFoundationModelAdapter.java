package com.company.communication.infrastructure.ai;

import com.company.communication.application.port.FoundationModelPort;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.exception.InvalidDecisionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador local do Foundation Model usando Ollama via Spring AI {@link ChatClient}.
 * Gerencia explicitamente o ciclo de Tool Calling (whitelist, limite de chamadas e timeout)
 * e converte a resposta final no DTO tipado {@link CommunicationDecision}.
 */
@Service
@Profile({"local", "default"})
public class LocalOllamaFoundationModelAdapter implements FoundationModelPort {

    private static final Logger log = LoggerFactory.getLogger(LocalOllamaFoundationModelAdapter.class);

    private final ChatClient chatClient;
    private final ToolCallingManager toolCallingManager;
    private final ToolCallingConfiguration toolConfig;
    private final AgentPromptBuilder promptBuilder;
    private final String modelName;
    private final double temperature;
    private final int maxTokens;

    public LocalOllamaFoundationModelAdapter(
            ChatClient chatClient,
            ToolCallingManager toolCallingManager,
            ToolCallingConfiguration toolConfig,
            AgentPromptBuilder promptBuilder,
            @Value("${spring.ai.ollama.chat.options.model}") String modelName,
            @Value("${app.ai.temperature:0.1}") double temperature,
            @Value("${app.ai.max-tokens:600}") int maxTokens
    ) {
        this.chatClient = chatClient;
        this.toolCallingManager = toolCallingManager;
        this.toolConfig = toolConfig;
        this.promptBuilder = promptBuilder;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    @Override
    public ModelExecutionResult generateDecision(String systemPrompt, String userPrompt) {
        OllamaChatOptions toolEnabledOptions = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .numPredict(maxTokens)
                .numCtx(8192)
                .toolCallbacks(toolConfig.getRegisteredCallbacks())
                .internalToolExecutionEnabled(false)
                .build();

        OllamaChatOptions finalJsonOptions = OllamaChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .numPredict(maxTokens)
                .numCtx(8192)
                .internalToolExecutionEnabled(false)
                .build();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        messages.add(new UserMessage(userPrompt));

        boolean isCorrectionRetry = userPrompt.contains("<validation_feedback>");
        List<ToolCallTrace> executedTraces = new ArrayList<>();

        if (isCorrectionRetry) {
            Prompt retryPrompt = new Prompt(messages, finalJsonOptions);
            ChatResponse retryResponse = chatClient.prompt(retryPrompt).call().chatResponse();
            String rawContent = extractAssistantText(retryResponse);
            return new ModelExecutionResult(parseDecision(rawContent), executedTraces);
        }

        Prompt currentPrompt = new Prompt(messages, toolEnabledOptions);
        ChatResponse response = chatClient.prompt(currentPrompt).call().chatResponse();
        log.info("Ollama response received: hasToolCalls={}, registeredTools={}",
                response != null && response.hasToolCalls(), toolConfig.getAllowedToolNames());

        int toolCallCount = 0;

        while (response != null && response.hasToolCalls()) {
            List<AssistantMessage.ToolCall> requestedCalls = response.getResult().getOutput().getToolCalls();

            for (AssistantMessage.ToolCall call : requestedCalls) {
                if (!toolConfig.isToolAllowed(call.name())) {
                    throw new InvalidDecisionException(
                            "Modelo tentou invocar ferramenta nao autorizada: " + call.name(),
                            List.of("Ferramenta nao registrada na whitelist: " + call.name())
                    );
                }
            }

            int remainingBudget = toolConfig.getMaxToolCalls() - toolCallCount;
            if (remainingBudget <= 0) {
                log.warn("Limite de chamadas de ferramentas atingido ({}/{}). Encerrando fase de ferramentas.",
                        toolCallCount, toolConfig.getMaxToolCalls());
                break;
            }

            List<AssistantMessage.ToolCall> allowedBatch = selectWithinBudget(requestedCalls, remainingBudget);
            ChatResponse boundedResponse = allowedBatch.size() == requestedCalls.size()
                    ? response
                    : rebuildResponseWithToolCalls(response, allowedBatch);

            long start = System.currentTimeMillis();
            ToolExecutionResult executionResult = executeToolsWithTimeout(currentPrompt, boundedResponse);
            long durationMs = System.currentTimeMillis() - start;

            toolCallCount += allowedBatch.size();
            recordToolTraces(allowedBatch, executionResult, durationMs, executedTraces);

            messages = new ArrayList<>(executionResult.conversationHistory());
            if (toolCallCount >= toolConfig.getMaxToolCalls()) {
                break;
            }

            currentPrompt = new Prompt(messages, toolEnabledOptions);
            response = chatClient.prompt(currentPrompt).call().chatResponse();
        }

        // Se o modelo ja devolveu um JSON valido diretamente na Fase 1 (sem tools), aproveita sem chamada extra
        if (executedTraces.isEmpty() && response != null && !response.hasToolCalls()) {
            String candidateText = response.getResult().getOutput().getText();
            CommunicationDecision directDecision = tryParseDecision(candidateText);
            if (directDecision != null) {
                return new ModelExecutionResult(directDecision, executedTraces);
            }
        }

        // Fase 2: consolidacao final em JSON estruturado com schema explicito (sem competicao de tool calling)
        if (response != null && !response.hasToolCalls() && response.getResult() != null
                && response.getResult().getOutput() != null && executedTraces.isEmpty()) {
            messages.add(response.getResult().getOutput());
        }
        messages.add(new UserMessage("""
                Agora consolide a decisao final respondendo EXCLUSIVAMENTE com um JSON valido aderente ao schema abaixo.
                Regras criticas do JSON:
                - Se `suppressionRequired` for true, `action` DEVE ser "SUPPRESS" e `channel`, `sendAt`, `templateId`, `fallbackChannel` DEVEM ser null, com `variables` vazio {}.
                - Se `action`="SEND": use apenas canais de `allowedChannels`, `fallbackChannel` deve ser null se houver apenas 1 canal permitido, e `sendAt` DEVE estar dentro de `recommendedSendAtWindow` mantendo exatamente o offset -03:00 (nao use Z).
                %s
                """.formatted(promptBuilder.getOutputConverter().getFormat())));

        ChatResponse finalResponse = chatClient.prompt(new Prompt(messages, finalJsonOptions)).call().chatResponse();
        String rawContent = extractAssistantText(finalResponse);
        CommunicationDecision decision = parseDecision(rawContent);
        return new ModelExecutionResult(decision, executedTraces);
    }

    private CommunicationDecision tryParseDecision(String rawText) {
        if (rawText == null || !rawText.contains("{")) {
            return null;
        }
        try {
            CommunicationDecision parsed = promptBuilder.getOutputConverter().convert(extractJsonObject(rawText));
            return parsed != null && parsed.action() != null && parsed.reasonCode() != null ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<AssistantMessage.ToolCall> selectWithinBudget(
            List<AssistantMessage.ToolCall> requestedCalls,
            int remainingBudget
    ) {
        Map<String, AssistantMessage.ToolCall> deduplicated = new LinkedHashMap<>();
        for (AssistantMessage.ToolCall call : requestedCalls) {
            String key = call.name() + "|" + (call.arguments() != null ? call.arguments() : "");
            deduplicated.putIfAbsent(key, call);
        }
        List<AssistantMessage.ToolCall> unique = new ArrayList<>(deduplicated.values());
        return unique.size() <= remainingBudget ? unique : unique.subList(0, remainingBudget);
    }

    private ChatResponse rebuildResponseWithToolCalls(
            ChatResponse original,
            List<AssistantMessage.ToolCall> allowedCalls
    ) {
        AssistantMessage origOutput = original.getResult().getOutput();
        AssistantMessage trimmedAssistant = AssistantMessage.builder()
                .content(origOutput.getText() != null ? origOutput.getText() : "")
                .properties(origOutput.getMetadata())
                .toolCalls(allowedCalls)
                .build();
        Generation trimmedGeneration = new Generation(trimmedAssistant, original.getResult().getMetadata());
        return new ChatResponse(List.of(trimmedGeneration), original.getMetadata());
    }

    private ToolExecutionResult executeToolsWithTimeout(Prompt prompt, ChatResponse response) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> toolCallingManager.executeToolCalls(prompt, response))
                    .get(toolConfig.getToolTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            throw new InvalidDecisionException(
                    "Erro ou timeout ao executar ferramenta Java solicitada pelo modelo: " + ex.getMessage(),
                    List.of("Falha na execucao de Tool: " + ex.getMessage())
            );
        }
    }

    private void recordToolTraces(
            List<AssistantMessage.ToolCall> executedCalls,
            ToolExecutionResult executionResult,
            long durationMs,
            List<ToolCallTrace> executedTraces
    ) {
        List<Message> history = executionResult.conversationHistory();
        String responseSummary = "executed";
        if (!history.isEmpty() && history.getLast() instanceof ToolResponseMessage toolMsg) {
            responseSummary = toolMsg.getResponses().stream()
                    .map(r -> r.name() + "->len=" + (r.responseData() != null ? r.responseData().length() : 0))
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("executed");
        }
        for (AssistantMessage.ToolCall call : executedCalls) {
            executedTraces.add(new ToolCallTrace(
                    call.name(),
                    summarize(call.arguments()),
                    summarize(responseSummary),
                    durationMs
            ));
        }
    }

    private String extractAssistantText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new InvalidDecisionException("Resposta vazia recebida do modelo local.", List.of("Resposta nula"));
        }
        String text = response.getResult().getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new InvalidDecisionException("Conteudo textual vazio recebido do modelo local.", List.of("Conteudo vazio"));
        }
        return text.trim();
    }

    private CommunicationDecision parseDecision(String rawText) {
        String candidate = extractJsonObject(rawText);
        try {
            return promptBuilder.getOutputConverter().convert(candidate);
        } catch (Exception ex) {
            throw new InvalidDecisionException(
                    "Nao foi possivel converter a resposta do modelo para CommunicationDecision: " + ex.getMessage(),
                    List.of("JSON invalido retornado pelo modelo: " + summarize(rawText))
            );
        }
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String summarize(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 160 ? singleLine.substring(0, 160) + "..." : singleLine;
    }
}
