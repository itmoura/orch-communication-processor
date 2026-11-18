package com.company.communication.infrastructure.ai;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.opensearch.OpenSearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Configuracao do ChatClient, ToolCallingManager e OpenSearchVectorStore para o profile local.
 */
@Configuration
@Profile({"local", "default"})
public class ChatModelConfiguration {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public ToolCallingManager toolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    /**
     * Corrige o bug do {@link OpenSearchAiSearchFilterExpressionConverter} do Spring AI 1.1.8,
     * que omite o espaco apos operadores booleanos (" AND" / " OR") na sintaxe Lucene query_string.
     */
    @Bean
    public FilterExpressionConverter openSearchFilterExpressionConverter() {
        return new OpenSearchAiSearchFilterExpressionConverter() {
            @Override
            protected void doExpression(Filter.Expression expression, StringBuilder context) {
                if (expression.type() == Filter.ExpressionType.IN || expression.type() == Filter.ExpressionType.NIN) {
                    if (expression.type() == Filter.ExpressionType.NIN) {
                        context.append("NOT ");
                    }
                    this.convertOperand(expression.left(), context);
                    context.append("(");
                    this.convertOperand(expression.right(), context);
                    context.append(")");
                    return;
                }
                this.convertOperand(expression.left(), context);
                context.append(switch (expression.type()) {
                    case AND -> " AND ";
                    case OR -> " OR ";
                    case EQ, NE -> "";
                    case NOT -> " NOT ";
                    case LT -> "<";
                    case LTE -> "<=";
                    case GT -> ">";
                    case GTE -> ">=";
                    default -> throw new IllegalArgumentException("Unsupported expression type: " + expression.type());
                });
                if (expression.type() == Filter.ExpressionType.NE) {
                    // Lucene query_string negacao de campo
                    int insertPos = context.lastIndexOf("metadata.");
                    if (insertPos >= 0) {
                        context.insert(insertPos, "NOT ");
                    }
                }
                this.convertOperand(expression.right(), context);
            }

            @Override
            protected void doAddValueRangeSpitter(Filter.Value value, StringBuilder context) {
                context.append(" OR ");
            }
        };
    }

    @Bean
    public OpenSearchVectorStore vectorStore(
            OpenSearchClient openSearchClient,
            EmbeddingModel embeddingModel,
            FilterExpressionConverter openSearchFilterExpressionConverter,
            @Value("${spring.ai.vectorstore.opensearch.index-name}") String indexName,
            @Value("${spring.ai.vectorstore.opensearch.initialize-schema:true}") boolean initializeSchema
    ) {
        return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
                .index(indexName)
                .initializeSchema(initializeSchema)
                .filterExpressionConverter(openSearchFilterExpressionConverter)
                .build();
    }
}
