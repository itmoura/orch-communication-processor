package com.company.communication.domain.model;

/**
 * Trecho de documento recuperado da base de conhecimento via busca semantica (RAG).
 */
public record KnowledgeChunk(
        String documentId,
        String sourceFile,
        String documentType,
        String productType,
        String channel,
        String status,
        String documentVersion,
        String content,
        Double score
) {
}
