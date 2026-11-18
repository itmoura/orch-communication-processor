package com.company.communication.application;

import com.company.communication.application.port.GuardrailPort;
import com.company.communication.application.port.PaymentHistoryPort;
import com.company.communication.application.port.ProductCatalogPort;
import com.company.communication.domain.model.BoletoGerado;
import com.company.communication.domain.model.CommunicationDecision;
import com.company.communication.domain.model.CustomerBehaviorProfile;
import com.company.communication.domain.model.CustomerContext;
import com.company.communication.domain.model.PaymentHistorySummary;
import com.company.communication.domain.model.ProductInfo;
import com.company.communication.service.CustomerContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestrador principal do fluxo de processamento de eventos {@link BoletoGerado}.
 * Executa exatamente a sequencia definida: perfil comportamental, contexto, guardrail de entrada,
 * orquestracao de IA (RAG + Tools + LLM + Guardrail de saida) e observabilidade estruturada.
 */
@Service
public class CommunicationProcessor {

    private static final Logger log = LoggerFactory.getLogger(CommunicationProcessor.class);

    private final CustomerBehaviorService customerBehaviorService;
    private final PaymentHistoryPort paymentHistoryPort;
    private final ProductCatalogPort productCatalogPort;
    private final CustomerContextBuilder contextBuilder;
    private final GuardrailPort guardrailPort;
    private final AIOrchestrator aiOrchestrator;

    public CommunicationProcessor(
            CustomerBehaviorService customerBehaviorService,
            PaymentHistoryPort paymentHistoryPort,
            ProductCatalogPort productCatalogPort,
            CustomerContextBuilder contextBuilder,
            GuardrailPort guardrailPort,
            AIOrchestrator aiOrchestrator
    ) {
        this.customerBehaviorService = customerBehaviorService;
        this.paymentHistoryPort = paymentHistoryPort;
        this.productCatalogPort = productCatalogPort;
        this.contextBuilder = contextBuilder;
        this.guardrailPort = guardrailPort;
        this.aiOrchestrator = aiOrchestrator;
    }

    public CommunicationDecision process(BoletoGerado event) {
        long startMs = System.currentTimeMillis();
        String executionId = UUID.randomUUID().toString();

        log.info("executionId={} eventId={} customerId={} stage=STEP_1_RECEIVED boletoId={} productType={}",
                executionId, event.eventId(), event.customerId(), event.boletoId(), event.productType());

        try {
            // Etapa 2: Recuperacao do perfil comportamental e dados auxiliares locais
            CustomerBehaviorProfile profile = customerBehaviorService.getProfile(event.customerId());
            PaymentHistorySummary paymentHistory = paymentHistoryPort.findByCustomerId(event.customerId())
                    .orElse(new PaymentHistorySummary(event.customerId(), 0, 0, 0, 0, 0, List.of()));
            ProductInfo productInfo = productCatalogPort.findByProductType(event.productType())
                    .orElseThrow(() -> new IllegalArgumentException("Produto nao catalogado: " + event.productType()));

            log.info("executionId={} eventId={} customerId={} stage=STEP_2_BEHAVIOR_PROFILE preferredChannel={} preferredPeriod={} onTime={} late={}",
                    executionId, event.eventId(), event.customerId(),
                    profile.preferredChannel(), profile.preferredPeriod(),
                    profile.onTimePayments(), profile.latePayments());

            // Etapa 3: Construcao do CustomerContext tipado e sanitizado
            CustomerContext context = contextBuilder.build(event, profile, paymentHistory, productInfo);
            log.info("executionId={} eventId={} customerId={} stage=STEP_3_CONTEXT_BUILT allowedChannels={} suppressionRequired={}",
                    executionId, event.eventId(), event.customerId(),
                    context.eligibility().allowedChannels(), context.eligibility().suppressionRequired());

            // Etapa 4: Guardrails de entrada
            guardrailPort.validateInput(context);
            log.info("executionId={} eventId={} customerId={} stage=STEP_4_INPUT_GUARDRAIL status=PASSED",
                    executionId, event.eventId(), event.customerId());

            // Etapas 5 a 10: RAG, Java Tools, LLM e Guardrails de saida
            CommunicationDecision decision = aiOrchestrator.orchestrate(executionId, context);

            long totalMs = System.currentTimeMillis() - startMs;
            log.info("executionId={} eventId={} customerId={} stage=STEP_11_COMPLETED action={} channel={} templateId={} reasonCode={} durationMs={}",
                    executionId, event.eventId(), event.customerId(),
                    decision.action(), decision.channel(), decision.templateId(), decision.reasonCode(), totalMs);

            return decision;
        } catch (RuntimeException ex) {
            long totalMs = System.currentTimeMillis() - startMs;
            log.error("executionId={} eventId={} customerId={} stage=FAILED durationMs={} error={}",
                    executionId, event.eventId(), event.customerId(), totalMs, ex.getMessage());
            throw ex;
        }
    }
}
