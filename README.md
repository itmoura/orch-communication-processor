# Communication Processor — AI Orchestrator (MVP Local)

MVP local do módulo **AI Orchestrator** do projeto **Communication Platform**, implementado em **Java 21 + Spring Boot + Spring AI**, executando integralmente em ambiente local com **Ollama** (LLM e Embeddings), **OpenSearch** em Docker (Vector Store / RAG), **Java Tools** via Tool Calling e **Guardrails** determinísticos em Java — sem necessidade de conta, credenciais ou consumo de serviços AWS.

---

## 1. Objetivo do MVP

Receber eventos `BoletoGerado` via API REST (`POST /api/v1/communications/decision`) e executar a orquestração completa até retornar uma decisão estruturada e validada (`CommunicationDecision`):

1. Validar o contrato de entrada (`Bean Validation` + `Jackson`).
2. Consultar perfil comportamental, histórico de pagamentos e catálogo de produtos locais.
3. Construir o `CustomerContext` tipado e sanitizado com elegibilidade determinística.
4. Aplicar guardrails de entrada (bloqueio de dados sensíveis e prompt injection).
5. Consultar a base de conhecimento em Markdown armazenada no OpenSearch via RAG com filtro de metadados (`status=APPROVED`, `productType`, `channel`).
6. Permitir que o modelo local (`llama3.1:8b`) invoque Java Tools registradas (`PaymentBehaviorTool` e `ProductInformationTool`) com whitelist, limite de chamadas e timeout.
7. Produzir uma decisão estruturada `CommunicationDecision` com temperatura baixa e limite de tokens.
8. Validar a decisão com guardrails de saída e regras de negócio determinísticas (com até 1 tentativa controlada de correção).
9. Retornar o JSON validado (HTTP 200) ou erro controlado (HTTP 400 / 422 / 500) com logs estruturados por execução.

---

## 2. Tecnologias e Versões Utilizadas

| Componente | Tecnologia | Versão Efetiva |
|---|---|---|
| Linguagem | Java (Eclipse Temurin JDK) | `21.0.13` |
| Build | Apache Maven / Maven Wrapper | `3.9.9` |
| Framework | Spring Boot (`spring-boot-starter-parent`) | `3.5.15` |
| Framework de IA | Spring AI (`spring-ai-bom`) | `1.1.8` |
| Starters Spring AI | `spring-ai-starter-model-ollama`, `spring-ai-starter-vector-store-opensearch` | `1.1.8` |
| Validação | Jakarta Validation / Hibernate Validator | `3.0.2` / `8.0.3.Final` |
| Serialização JSON | Jackson Databind | `2.21.4` |
| Vector Store | OpenSearch (Docker `opensearchproject/opensearch`) | `2.18.0` |
| Cliente OpenSearch | `opensearch-java` / `opensearch-rest-client` | `2.23.0` / `2.12.0` |
| Servidor LLM / Embeddings | Ollama | `0.40.16` |
| Modelo de Chat (Tool Calling) | `llama3.1:8b` | tag `latest` |
| Modelo de Embeddings | `nomic-embed-text` (768 dimensões) | tag `latest` |

---

## 3. Arquitetura Implementada

A arquitetura separa claramente **domínio**, **aplicação (portas e orquestração)**, **serviços de negócio** e **infraestrutura** (`local` hoje, `aws` no futuro):

```text
POST /api/v1/communications/decision
  │
  ▼
CommunicationController (REST + Bean Validation)
  │
  ▼
CommunicationProcessor (Orquestracao + Logs estruturados + executionId)
  ├──► CustomerBehaviorService ──► CustomerDataPort (MockCustomerDataService)
  ├──► PaymentHistoryPort (MockPaymentHistoryService)
  ├──► ProductCatalogPort (MockProductCatalogService)
  ├──► CustomerContextBuilder (CustomerContext tipado + EligibilityResult)
  ├──► GuardrailPort.validateInput() ──► InputGuardrail
  └──► AIOrchestrator
         ├──► KnowledgeRetrieverPort ──► KnowledgeBaseService (OpenSearch RAG)
         ├──► AgentPromptBuilder (communication-agent.st + contexto + RAG)
         ├──► FoundationModelPort ──► LocalOllamaFoundationModelAdapter (ChatClient + ToolCallingManager)
         │       ├──► PaymentBehaviorTool (@Tool)
         │       └──► ProductInformationTool (@Tool)
         └──► GuardrailPort.validateOutput() ──► OutputGuardrail + CommunicationDecisionValidator
```

### Responsabilidade de Cada Classe

- `CommunicationApplication`: Ponto de entrada Spring Boot.
- `controller/CommunicationController`: Expõe `POST /api/v1/communications/decision`, valida `BoletoGerado` com `@Valid` e delega ao `CommunicationProcessor`.
- `application/CommunicationProcessor`: Coordena as 11 etapas do fluxo, gera `executionId`, mede latência e registra logs estruturados.
- `application/CustomerBehaviorService`: Recupera o perfil comportamental via `CustomerDataPort`.
- `application/AIOrchestrator`: Executa a busca RAG, monta prompts, invoca o `FoundationModelPort`, aciona `GuardrailPort.validateOutput()` e gerencia no máximo 1 tentativa controlada de correção.
- `application/port/*`: Interfaces (`FoundationModelPort`, `KnowledgeRetrieverPort`, `GuardrailPort`, `CustomerDataPort`, `PaymentHistoryPort`, `ProductCatalogPort`) que desacoplam o núcleo da aplicação dos provedores de IA locais ou AWS.
- `domain/model/*` & `domain/enums/*`: Contratos e tipos imutáveis (`BoletoGerado`, `CustomerBehaviorProfile`, `PaymentHistorySummary`, `ProductInfo`, `EligibilityResult`, `CustomerContext`, `CommunicationDecision`, `KnowledgeChunk`, `TemplateDefinition`) e enums (`CommunicationAction`, `CommunicationChannel`, `CommunicationReasonCode`, `ProductType`, `PreferredPeriod`, `CommunicationTone`).
- `service/CustomerContextBuilder`: Combina evento, perfil, métricas de pagamento e produto em `CustomerContext` sanitizado e calcula `EligibilityResult` (canais permitidos, bloqueios, supressão e janela de envio entre 08:00 e 20:00).
- `service/TemplateCatalogService`: Catálogo homologado local de templates (`BOLETO_REMINDER`, `PAYMENT_GUIDANCE_EMAIL`, `NEAR_DUE_DATE_ALERT`, `OVERDUE_SUPPORT_NOTICE`).
- `service/CommunicationDecisionValidator`: Validação determinística da decisão da IA (campos obrigatórios, compatibilidade entre `action` e campos, canal permitido, `fallbackChannel != channel`, template aprovado/compatível, horário válido e variáveis obrigatórias sem PII).
- `infrastructure/knowledge/KnowledgeDocumentLoader`: Lê `classpath:knowledge-base/*.md`, extrai metadados YAML do frontmatter e gera chunks com IDs determinísticos (UUID baseado em arquivo + versão + hash SHA-256 + índice).
- `infrastructure/knowledge/KnowledgeBaseInitializer`: `ApplicationRunner` idempotente que verifica no OpenSearch quais chunks já existem antes de gerar embeddings e indexar.
- `infrastructure/ai/ChatModelConfiguration`: Configura `ChatClient`, `ToolCallingManager` e `OpenSearchVectorStore` (incluindo correção para o `OpenSearchAiSearchFilterExpressionConverter` do Spring AI 1.1.8 adicionar espaço após operadores `AND`/`OR`).
- `infrastructure/ai/KnowledgeBaseService`: Implementa `KnowledgeRetrieverPort` sobre `VectorStore` filtrando apenas documentos com `status=APPROVED`, `productType` aplicável e `channel` permitido.
- `infrastructure/ai/AgentPromptBuilder`: Constrói os prompts a partir de `communication-agent.st`, `CustomerContext`, chunks RAG e `BeanOutputConverter<CommunicationDecision>`.
- `infrastructure/ai/ToolCallingConfiguration`: Registra as ferramentas Java permitidas (`PaymentBehaviorTool`, `ProductInformationTool`), mantém a whitelist e expõe os limites (`max-tool-calls`, `tool-timeout-ms`).
- `infrastructure/ai/LocalOllamaFoundationModelAdapter`: Implementa `FoundationModelPort` via `ChatClient` + `ToolCallingManager` em modo controlado (`internalToolExecutionEnabled(false)`), validando whitelist, limite de chamadas e timeout, seguido pela consolidação em `CommunicationDecision` JSON.
- `infrastructure/tools/PaymentBehaviorTool`: `@Tool getPaymentBehaviorDetails` para consulta de atrasos recentes e maior atraso registrado.
- `infrastructure/tools/ProductInformationTool`: `@Tool getProductInformation` para consulta de características, diretrizes e restrições do produto.
- `infrastructure/guardrails/InputGuardrail`: Valida integridade do `CustomerContext`, bloqueia CPF, e-mail, código de barras/linha digitável e padrões de prompt injection.
- `infrastructure/guardrails/OutputGuardrail` & `GuardrailService`: Avaliam linguagem ameaçadora/coercitiva e delegam a validação de negócio para `CommunicationDecisionValidator`.
- `infrastructure/mock/*`: Adaptadores locais (`MockCustomerDataService`, `MockPaymentHistoryService`, `MockProductCatalogService`) carregando `mock-data/*.json`.
- `exception/*`: `GlobalExceptionHandler`, `InvalidDecisionException`, `GuardrailViolationException`, `CustomerNotFoundException` mapeando HTTP `400`, `422` e `500`.

---

## 4. Estrutura de Pastas

```text
.
├── .env.example
├── .gitignore
├── IMPLEMENTATION_GUIDE.md
├── README.md
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
├── pom.xml
└── src/
    └── main/
        ├── java/com/company/communication/
        │   ├── CommunicationApplication.java
        │   ├── application/
        │   │   ├── AIOrchestrator.java
        │   │   ├── CommunicationProcessor.java
        │   │   ├── CustomerBehaviorService.java
        │   │   └── port/
        │   │       ├── CustomerDataPort.java
        │   │       ├── FoundationModelPort.java
        │   │       ├── GuardrailPort.java
        │   │       ├── KnowledgeRetrieverPort.java
        │   │       ├── PaymentHistoryPort.java
        │   │       └── ProductCatalogPort.java
        │   ├── controller/
        │   │   └── CommunicationController.java
        │   ├── domain/
        │   │   ├── enums/
        │   │   │   ├── CommunicationAction.java
        │   │   │   ├── CommunicationChannel.java
        │   │   │   ├── CommunicationReasonCode.java
        │   │   │   ├── CommunicationTone.java
        │   │   │   ├── PreferredPeriod.java
        │   │   │   └── ProductType.java
        │   │   └── model/
        │   │       ├── BoletoGerado.java
        │   │       ├── CommunicationDecision.java
        │   │       ├── CustomerBehaviorProfile.java
        │   │       ├── CustomerContext.java
        │   │       ├── EligibilityResult.java
        │   │       ├── KnowledgeChunk.java
        │   │       ├── PaymentHistorySummary.java
        │   │       ├── ProductInfo.java
        │   │       └── TemplateDefinition.java
        │   ├── exception/
        │   │   ├── CustomerNotFoundException.java
        │   │   ├── GlobalExceptionHandler.java
        │   │   ├── GuardrailViolationException.java
        │   │   └── InvalidDecisionException.java
        │   ├── infrastructure/
        │   │   ├── ai/
        │   │   │   ├── AgentPromptBuilder.java
        │   │   │   ├── ChatModelConfiguration.java
        │   │   │   ├── KnowledgeBaseService.java
        │   │   │   ├── LocalOllamaFoundationModelAdapter.java
        │   │   │   └── ToolCallingConfiguration.java
        │   │   ├── guardrails/
        │   │   │   ├── GuardrailService.java
        │   │   │   ├── InputGuardrail.java
        │   │   │   └── OutputGuardrail.java
        │   │   ├── knowledge/
        │   │   │   ├── KnowledgeBaseInitializer.java
        │   │   │   └── KnowledgeDocumentLoader.java
        │   │   ├── mock/
        │   │   │   ├── MockCustomerDataService.java
        │   │   │   ├── MockPaymentHistoryService.java
        │   │   │   └── MockProductCatalogService.java
        │   │   └── tools/
        │   │       ├── PaymentBehaviorTool.java
        │   │       └── ProductInformationTool.java
        │   └── service/
        │       ├── CommunicationDecisionValidator.java
        │       ├── CustomerContextBuilder.java
        │       └── TemplateCatalogService.java
        └── resources/
            ├── application-aws.yml
            ├── application-local.yml
            ├── application.yml
            ├── knowledge-base/
            │   ├── communication-guidelines.md
            │   ├── communication-policies.md
            │   ├── communication-templates.md
            │   └── product-rules.md
            ├── mock-data/
            │   ├── customers.json
            │   ├── payment-history.json
            │   └── products.json
            └── prompts/
                └── communication-agent.st
```

---

## 5. Instalação e Execução Local

### Pré-requisitos
- **Java 21** (JDK)
- **Docker** (Docker Desktop / Docker Engine)
- **Ollama** instalado localmente

### Passo 1 — Instalar e iniciar o Ollama
No Windows (via winget) ou site oficial (`https://ollama.com`):
```powershell
winget install -e --id Ollama.Ollama
ollama serve
```

### Passo 2 — Baixar os modelos locais
```powershell
ollama pull llama3.1:8b
ollama pull nomic-embed-text
ollama list
```
Os modelos podem ser alterados via variáveis de ambiente (`OLLAMA_CHAT_MODEL` e `OLLAMA_EMBEDDING_MODEL`), sem alterar o código Java.

### Passo 3 — Subir o OpenSearch com Docker Compose
```powershell
docker compose up -d
```
Para subir também o OpenSearch Dashboards (opcional, porta `5601`):
```powershell
docker compose --profile dashboards up -d
```
Verificar saúde do cluster:
```powershell
curl http://localhost:9200/_cluster/health
```

### Passo 4 — Variáveis de Ambiente
Copie `.env.example` para `.env` (ou exporte as variáveis no shell):
- `SPRING_PROFILES_ACTIVE=local`
- `SERVER_PORT=8080`
- `OLLAMA_BASE_URL=http://localhost:11434`
- `OLLAMA_CHAT_MODEL=llama3.1:8b`
- `OLLAMA_EMBEDDING_MODEL=nomic-embed-text`
- `OPENSEARCH_URIS=http://localhost:9200`
- `OPENSEARCH_INDEX_NAME=communication-knowledge-base`
- `AI_TEMPERATURE=0.05`
- `AI_MAX_TOKENS=900`
- `AI_MAX_TOOL_CALLS=2`
- `AI_TOOL_TIMEOUT_MS=3000`
- `RAG_TOP_K=4`
- `RAG_SIMILARITY_THRESHOLD=0.35`

### Passo 5 — Iniciar a aplicação Spring Boot
```powershell
.\mvnw.cmd spring-boot:run
```
Ou empacotando e executando o JAR:
```powershell
.\mvnw.cmd package -DskipTests
java -jar target/communication-processor-0.1.0-SNAPSHOT.jar
```

---

## 6. Como Funcionam Indexação, RAG, Tool Calling e Guardrails

### Indexação Idempotente da Base de Conhecimento
Na inicialização, `KnowledgeBaseInitializer` carrega os 4 arquivos Markdown de `src/main/resources/knowledge-base/`, faz parsing do cabeçalho YAML (`documentType`, `productType`, `channel`, `status`, `documentVersion`), divide em 10 chunks via `TokenTextSplitter` e gera um UUID determinístico (`UUID.nameUUIDFromBytes`) por `arquivo + versão + hash SHA-256 + chunkIndex`. Antes de chamar o `VectorStore`, consulta `openSearchClient.exists(...)` para inserir apenas chunks novos ou alterados.

### Consulta RAG (OpenSearch Vector Store)
`KnowledgeBaseService` gera uma consulta semântica contextual (produto, canal preferencial, elegibilidade, dias até vencimento e tom) e executa `vectorStore.similaritySearch(...)` com `FilterExpressionBuilder` exigindo:
- `status == 'APPROVED'`
- `productType == 'ALL' OR productType == <produto>`
- `channel IN ['ALL', <canais permitidos>]`

### Tool Calling Controlado
As ferramentas `PaymentBehaviorTool` (`getPaymentBehaviorDetails`) e `ProductInformationTool` (`getProductInformation`) são expostas ao `llama3.1:8b` via `@Tool` e `ToolCallbacks.from(...)`. O `LocalOllamaFoundationModelAdapter` executa o loop manualmente com `internalToolExecutionEnabled(false)` e `ToolCallingManager`, garantindo:
- Execução apenas de nomes presentes na whitelist (`ToolCallingConfiguration`).
- Validação dos argumentos na própria ferramenta.
- Limite máximo de 2 chamadas por interação (`app.ai.max-tool-calls`).
- Timeout por execução (`app.ai.tool-timeout-ms`, 3000 ms).
- Nenhuma ferramenta com efeitos colaterais (envio, cancelamento ou alteração de consentimento).

### Guardrails de Entrada e Saída
- **Entrada (`InputGuardrail`)**: bloqueia contexto nulo/incompleto, CPF, e-mail, código de barras/linha digitável e padrões de prompt injection antes da chamada ao modelo.
- **Saída (`OutputGuardrail` + `CommunicationDecisionValidator`)**: valida estrutura JSON, enums, compatibilidade de campos para `SEND` e `SUPPRESS`, canal permitido em `eligibility.allowedChannels`, `fallbackChannel != channel`, template aprovado no `TemplateCatalogService`, janela permitida (`08:00`–`20:00`, nunca antes de `occurredAt`), variáveis obrigatórias (`firstName`, `dueDate` em `dd/MM/yyyy`), ausência de PII e ausência de linguagem ameaçadora. Caso haja violação, o `AIOrchestrator` realiza **1 tentativa controlada de correção** informando os erros encontrados. Persistindo inválida, retorna **HTTP 422**.

---

## 7. Exemplos de Requisição e Respostas Reais Obtidas

### Cenário A — Cliente Preventivo (`cus-123456`, Ana)

```bash
curl -i -X POST http://localhost:8080/api/v1/communications/decision \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-123456",
    "eventType": "BOLETO_GERADO",
    "occurredAt": "2026-11-17T09:00:00-03:00",
    "boletoId": "bol-987654",
    "customerId": "cus-123456",
    "amountInCents": 150000,
    "dueDate": "2026-11-25",
    "productType": "PERSONAL_LOAN"
  }'
```

Resposta real observada (`HTTP 200`):
```json
{
  "action": "SEND",
  "channel": "WHATSAPP",
  "sendAt": "2026-11-17T12:00:00Z",
  "templateId": "BOLETO_REMINDER",
  "variables": {
    "firstName": "Ana",
    "dueDate": "25/11/2026"
  },
  "fallbackChannel": null,
  "reasonCode": "DUE_DATE_REMINDER"
}
```
*(Nota: `2026-11-17T12:00:00Z` corresponde a `09:00:00-03:00` no fuso de Brasília. Logs confirmaram `docsRetrieved=4` e execução de `getPaymentBehaviorDetails` e `getProductInformation`.)*

### Cenário B — Cliente com Histórico de Atrasos (`cus-789012`, Carlos)

```bash
curl -i -X POST http://localhost:8080/api/v1/communications/decision \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-789012",
    "eventType": "BOLETO_GERADO",
    "occurredAt": "2026-11-17T14:00:00-03:00",
    "boletoId": "bol-789012",
    "customerId": "cus-789012",
    "amountInCents": 245000,
    "dueDate": "2026-11-20",
    "productType": "PERSONAL_LOAN"
  }'
```

Resposta real observada (`HTTP 200`):
```json
{
  "action": "SEND",
  "channel": "EMAIL",
  "sendAt": "2026-11-17T17:00:00Z",
  "templateId": "BOLETO_REMINDER",
  "variables": {
    "firstName": "Carlos",
    "dueDate": "20/11/2026"
  },
  "fallbackChannel": null,
  "reasonCode": "DUE_DATE_REMINDER"
}
```

### Cenário C — Cliente sem Canal WhatsApp Permitido (`cus-345678`, Mariana)

```bash
curl -i -X POST http://localhost:8080/api/v1/communications/decision \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-345678",
    "eventType": "BOLETO_GERADO",
    "occurredAt": "2026-11-17T09:30:00-03:00",
    "boletoId": "bol-345678",
    "customerId": "cus-345678",
    "amountInCents": 98000,
    "dueDate": "2026-11-20",
    "productType": "CREDIT_CARD"
  }'
```

Resposta real observada (`HTTP 200`):
```json
{
  "action": "SEND",
  "channel": "EMAIL",
  "sendAt": "2026-11-17T13:00:00Z",
  "templateId": "BOLETO_REMINDER",
  "variables": {
    "firstName": "Mariana",
    "dueDate": "20/11/2026"
  },
  "fallbackChannel": null,
  "reasonCode": "DUE_DATE_REMINDER"
}
```

### Cenário D (Adicional) — Cliente com Opt-Out (`cus-999000`, Roberto — `SUPPRESS`)

```bash
curl -i -X POST http://localhost:8080/api/v1/communications/decision \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-999000",
    "eventType": "BOLETO_GERADO",
    "occurredAt": "2026-11-17T10:00:00-03:00",
    "boletoId": "bol-999000",
    "customerId": "cus-999000",
    "amountInCents": 50000,
    "dueDate": "2026-11-24",
    "productType": "PERSONAL_LOAN"
  }'
```

Resposta real observada (`HTTP 200`):
```json
{
  "action": "SUPPRESS",
  "channel": null,
  "sendAt": null,
  "templateId": null,
  "variables": {},
  "fallbackChannel": null,
  "reasonCode": "CUSTOMER_OPT_OUT"
}
```

### Cenário E — Validação de Contrato Inválido (`HTTP 400`)

```bash
curl -i -X POST http://localhost:8080/api/v1/communications/decision \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "",
    "eventType": "INVALID",
    "occurredAt": "2026-11-17T10:00:00-03:00",
    "boletoId": "bol-000",
    "customerId": "cus-123456",
    "amountInCents": -100,
    "dueDate": "2026-11-24",
    "productType": "PERSONAL_LOAN"
  }'
```

Resposta real observada (`HTTP 400`):
```json
{
  "timestamp": "2026-11-17T10:43:52.0619018-03:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Dados de entrada invalidos",
  "details": [
    "eventType: eventType deve ser BOLETO_GERADO",
    "eventId: eventId e obrigatorio",
    "amountInCents: amountInCents deve ser maior que zero"
  ],
  "path": "/api/v1/communications/decision"
}
```

---

## 8. Como Substituir os Adaptadores Locais por AWS Futuramente

O núcleo (`CommunicationProcessor`, `AIOrchestrator`, `CustomerContextBuilder`, `CommunicationDecisionValidator`) depende apenas das interfaces em `application/port/` e não referencia classes do Ollama ou OpenSearch. Para ativar o profile `aws` no futuro:

1. **Dependências (`pom.xml`)**: adicionar `spring-ai-starter-model-bedrock-converse`, `software.amazon.awssdk:bedrockruntime` e `software.amazon.awssdk:bedrockagentruntime`.
2. **Foundation Model (`FoundationModelPort`)**: criar um adaptador anotado com `@Profile("aws")` usando `BedrockProxyChatModel` (Converse API, cliente `bedrockruntime`) mantendo o mesmo `ToolCallingManager` para as Java Tools.
3. **Knowledge Base (`KnowledgeRetrieverPort`)**: criar um adaptador `@Profile("aws")` usando `BedrockAgentRuntimeClient` (`RetrieveRequest`) apontando para o Amazon Bedrock Knowledge Bases (conectado ao OpenSearch Serverless), sem indexação local.
4. **Guardrails (`GuardrailPort`)**: criar um adaptador `@Profile("aws")` chamando `ApplyGuardrail` do Bedrock (`bedrockruntime`) e preservando a validação determinística final de `CommunicationDecisionValidator`.
5. **Fontes de Dados (`CustomerDataPort`, `PaymentHistoryPort`, `ProductCatalogPort`)**: substituir os mocks JSON por clientes HTTP/DynamoDB/RDS reais anotados com `@Profile("aws")`.
6. **Credenciais e Região**: usar `DefaultCredentialsProvider` (AWS CLI/SSO em dev, IAM Role em ECS/EKS/Lambda) com `AWS_REGION` via variável de ambiente.
