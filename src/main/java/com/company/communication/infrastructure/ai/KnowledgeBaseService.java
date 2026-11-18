package com.company.communication.infrastructure.ai;

import com.company.communication.application.port.KnowledgeRetrieverPort;
import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.KnowledgeChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servico de consulta RAG local utilizando Spring AI VectorStore sobre OpenSearch.
 * Recupera apenas documentos aprovados e aplicaveis ao produto e canais permitidos.
 */
@Service
@Profile({"local", "default"})
public class KnowledgeBaseService implements KnowledgeRetrieverPort {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public KnowledgeBaseService(
            VectorStore vectorStore,
            @Value("${app.ai.rag.top-k:6}") int topK,
            @Value("${app.ai.rag.similarity-threshold:0.35}") double similarityThreshold
    ) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<KnowledgeChunk> retrieveRelevantKnowledge(
            String query,
            ProductType productType,
            List<CommunicationChannel> allowedChannels
    ) {
        Filter.Expression filterExpression = buildMetadataFilter(productType, allowedChannels);

        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(filterExpression)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents == null) {
            return List.of();
        }

        return documents.stream()
                .map(this::toKnowledgeChunk)
                .toList();
    }

    private Filter.Expression buildMetadataFilter(
            ProductType productType,
            List<CommunicationChannel> allowedChannels
    ) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        FilterExpressionBuilder.Op statusFilter = b.eq("status", "APPROVED");
        FilterExpressionBuilder.Op productFilter = b.group(b.or(
                b.eq("productType", "ALL"),
                b.eq("productType", productType.name())
        ));

        List<Object> channelValues = new ArrayList<>();
        channelValues.add("ALL");
        if (allowedChannels != null) {
            allowedChannels.forEach(c -> channelValues.add(c.name()));
        }
        FilterExpressionBuilder.Op channelFilter = b.in("channel", channelValues);

        return b.and(statusFilter, b.and(productFilter, channelFilter)).build();
    }

    private KnowledgeChunk toKnowledgeChunk(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        return new KnowledgeChunk(
                doc.getId(),
                stringMeta(meta, "sourceFile"),
                stringMeta(meta, "documentType"),
                stringMeta(meta, "productType"),
                stringMeta(meta, "channel"),
                stringMeta(meta, "status"),
                stringMeta(meta, "documentVersion"),
                doc.getText(),
                doc.getScore()
        );
    }

    private String stringMeta(Map<String, Object> meta, String key) {
        Object val = meta.get(key);
        return val == null ? "" : String.valueOf(val);
    }
}
