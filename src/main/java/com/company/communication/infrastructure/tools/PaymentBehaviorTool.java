package com.company.communication.infrastructure.tools;

import com.company.communication.application.port.PaymentHistoryPort;
import com.company.communication.domain.model.PaymentHistorySummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Ferramenta Java disponibilizada ao modelo via Spring AI Tool Calling para consultar
 * detalhes complementares do historico de pagamentos do cliente.
 */
@Component
public class PaymentBehaviorTool {

    private static final Logger log = LoggerFactory.getLogger(PaymentBehaviorTool.class);
    private static final Pattern VALID_CUSTOMER_ID = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    private final PaymentHistoryPort paymentHistoryPort;

    public PaymentBehaviorTool(PaymentHistoryPort paymentHistoryPort) {
        this.paymentHistoryPort = paymentHistoryPort;
    }

    @Tool(
            name = "getPaymentBehaviorDetails",
            description = "Consulta detalhes complementares do historico de pagamentos de um cliente: "
                    + "quantidade de pagamentos recentes, atrasos, maior atraso registrado e resumo dos ultimos boletos."
    )
    public PaymentHistorySummary getPaymentBehaviorDetails(
            @ToolParam(description = "Identificador interno do cliente, exemplo: cus-123456")
            String customerId
    ) {
        if (customerId == null || !VALID_CUSTOMER_ID.matcher(customerId.trim()).matches()) {
            throw new IllegalArgumentException("Argumento customerId invalido para PaymentBehaviorTool.");
        }

        String sanitizedId = customerId.trim();
        log.info("Tool executada: PaymentBehaviorTool.getPaymentBehaviorDetails(customerId={})", sanitizedId);
        return paymentHistoryPort.findByCustomerId(sanitizedId)
                .orElseThrow(() -> new IllegalArgumentException("Historico de pagamentos nao encontrado para " + sanitizedId));
    }
}
