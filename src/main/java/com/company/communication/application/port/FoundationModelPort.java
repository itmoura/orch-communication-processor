package com.company.communication.application.port;

import com.company.communication.domain.model.CommunicationDecision;

import java.util.List;

/**
 * Abstracao do Foundation Model responsavel por orquestrar raciocinio, Tool Calling e saida estruturada.
 * Desacopla o nucleo de aplicacao das classes especificas de Ollama ou Amazon Bedrock Converse.
 */
public interface FoundationModelPort {

    ModelExecutionResult generateDecision(String systemPrompt, String userPrompt);

    /**
     * Resultado da execucao do modelo, incluindo decisao estruturada e rastreio das tools acionadas.
     */
    record ModelExecutionResult(
            CommunicationDecision decision,
            List<ToolCallTrace> toolCalls
    ) {
    }

    /**
     * Registro resumido de uma chamada de ferramenta realizada pelo modelo.
     */
    record ToolCallTrace(
            String toolName,
            String argumentsSummary,
            String resultSummary,
            long durationMs
    ) {
    }
}
