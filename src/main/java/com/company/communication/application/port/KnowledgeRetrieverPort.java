package com.company.communication.application.port;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.KnowledgeChunk;

import java.util.List;

/**
 * Abstracao para recuperacao semantica (RAG) de politicas, templates e diretrizes.
 * Permite alternar entre OpenSearch local (profile local) e Bedrock Knowledge Bases (profile aws).
 */
public interface KnowledgeRetrieverPort {

    List<KnowledgeChunk> retrieveRelevantKnowledge(
            String query,
            ProductType productType,
            List<CommunicationChannel> allowedChannels
    );
}
