package com.company.communication.infrastructure.knowledge;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializador idempotente da base de conhecimento no OpenSearch local.
 * Verifica quais chunks ainda nao existem no indice antes de gerar embeddings e indexar.
 */
@Component
@Profile({"local", "default"})
public class KnowledgeBaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private final KnowledgeDocumentLoader documentLoader;
    private final VectorStore vectorStore;
    private final OpenSearchClient openSearchClient;
    private final String indexName;

    public KnowledgeBaseInitializer(
            KnowledgeDocumentLoader documentLoader,
            VectorStore vectorStore,
            OpenSearchClient openSearchClient,
            @Value("${spring.ai.vectorstore.opensearch.index-name}") String indexName
    ) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.openSearchClient = openSearchClient;
        this.indexName = indexName;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<Document> chunks = documentLoader.loadAndSplitDocuments();
        if (chunks.isEmpty()) {
            log.warn("Nenhum documento encontrado para indexacao na base de conhecimento.");
            return;
        }

        boolean indexExists = openSearchClient.indices().exists(req -> req.index(indexName)).value();
        List<Document> pendingChunks = new ArrayList<>();

        for (Document chunk : chunks) {
            if (!indexExists || !documentExists(chunk.getId())) {
                pendingChunks.add(chunk);
            }
        }

        if (pendingChunks.isEmpty()) {
            log.info("Base de conhecimento ja indexada de forma idempotente no OpenSearch (indice='{}', totalChunks={}).",
                    indexName, chunks.size());
            return;
        }

        vectorStore.add(pendingChunks);
        log.info("Indexados {} novos chunks da base de conhecimento no OpenSearch (indice='{}', totalChunks={}).",
                pendingChunks.size(), indexName, chunks.size());
    }

    private boolean documentExists(String id) throws IOException {
        return openSearchClient.exists(req -> req.index(indexName).id(id)).value();
    }
}
