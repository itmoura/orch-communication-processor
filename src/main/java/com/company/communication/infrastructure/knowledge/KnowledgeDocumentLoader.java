package com.company.communication.infrastructure.knowledge;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Carrega arquivos Markdown locais da base de conhecimento, extrai metadados de cabecalho
 * e divide o conteudo em trechos (chunks) com IDs deterministicos para indexacao idempotente.
 */
@Component
public class KnowledgeDocumentLoader {

    private final ResourcePatternResolver resourceResolver;
    private final String knowledgeBaseLocation;
    private final TokenTextSplitter textSplitter;

    public KnowledgeDocumentLoader(
            ResourcePatternResolver resourceResolver,
            @Value("${app.ai.rag.knowledge-base-location}") String knowledgeBaseLocation
    ) {
        this.resourceResolver = resourceResolver;
        this.knowledgeBaseLocation = knowledgeBaseLocation;
        this.textSplitter = TokenTextSplitter.builder()
                .withChunkSize(350)
                .withMinChunkSizeChars(80)
                .withMinChunkLengthToEmbed(20)
                .withMaxNumChunks(100)
                .withKeepSeparator(true)
                .build();
    }

    public List<Document> loadAndSplitDocuments() throws IOException {
        Resource[] resources = resourceResolver.getResources(knowledgeBaseLocation);
        List<Document> allChunks = new ArrayList<>();

        for (Resource resource : resources) {
            if (!resource.exists() || resource.getFilename() == null) {
                continue;
            }
            String rawContent = resource.getContentAsString(StandardCharsets.UTF_8);
            ParsedMarkdown parsed = parseFrontmatter(rawContent, resource.getFilename());
            String contentHash = sha256(parsed.body());

            Document sourceDoc = new Document(parsed.body(), parsed.metadata());
            List<Document> splitChunks = textSplitter.split(List.of(sourceDoc));

            for (int i = 0; i < splitChunks.size(); i++) {
                Document chunk = splitChunks.get(i);
                Map<String, Object> metadata = new LinkedHashMap<>(parsed.metadata());
                metadata.put("chunkIndex", i);
                metadata.put("contentHash", contentHash);

                String deterministicKey = resource.getFilename()
                        + ":" + metadata.get("documentVersion")
                        + ":" + contentHash
                        + ":" + i;
                String deterministicId = UUID.nameUUIDFromBytes(deterministicKey.getBytes(StandardCharsets.UTF_8)).toString();
                metadata.put("documentId", deterministicId);

                allChunks.add(new Document(deterministicId, chunk.getText(), metadata));
            }
        }

        return allChunks;
    }

    private ParsedMarkdown parseFrontmatter(String rawContent, String filename) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceFile", filename);
        metadata.put("documentType", "GENERAL");
        metadata.put("productType", "ALL");
        metadata.put("channel", "ALL");
        metadata.put("status", "APPROVED");
        metadata.put("documentVersion", "1.0");

        String normalized = rawContent.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return new ParsedMarkdown(metadata, normalized.trim());
        }

        int closingIndex = normalized.indexOf("\n---\n", 4);
        if (closingIndex < 0) {
            return new ParsedMarkdown(metadata, normalized.trim());
        }

        String headerBlock = normalized.substring(4, closingIndex);
        String body = normalized.substring(closingIndex + 5).trim();

        for (String line : headerBlock.split("\n")) {
            int sep = line.indexOf(':');
            if (sep > 0) {
                String key = line.substring(0, sep).trim();
                String value = line.substring(sep + 1).trim().replaceAll("^\"|\"$", "");
                metadata.put(key, value);
            }
        }

        return new ParsedMarkdown(metadata, body);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel", ex);
        }
    }

    private record ParsedMarkdown(Map<String, Object> metadata, String body) {
    }
}
