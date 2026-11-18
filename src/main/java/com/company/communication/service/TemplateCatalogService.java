package com.company.communication.service;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.TemplateDefinition;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Catalogo local de templates homologados para validacao deterministica das decisoes da IA.
 */
@Service
public class TemplateCatalogService {

    private static final Set<ProductType> ALL_PRODUCTS = Set.of(ProductType.PERSONAL_LOAN, ProductType.CREDIT_CARD);
    private static final Set<CommunicationChannel> ALL_CHANNELS = Set.of(
            CommunicationChannel.WHATSAPP,
            CommunicationChannel.EMAIL,
            CommunicationChannel.SMS
    );
    private static final Set<String> STANDARD_VARIABLES = Set.of("firstName", "dueDate");

    private final Map<String, TemplateDefinition> catalog = Map.of(
            "BOLETO_REMINDER", new TemplateDefinition(
                    "BOLETO_REMINDER",
                    "Lembrete preventivo de boleto gerado ou proximo ao vencimento",
                    true,
                    ALL_CHANNELS,
                    ALL_PRODUCTS,
                    STANDARD_VARIABLES
            ),
            "PAYMENT_GUIDANCE_EMAIL", new TemplateDefinition(
                    "PAYMENT_GUIDANCE_EMAIL",
                    "Orientacao detalhada de pagamento por e-mail para clientes com historico de atrasos",
                    true,
                    Set.of(CommunicationChannel.EMAIL),
                    ALL_PRODUCTS,
                    STANDARD_VARIABLES
            ),
            "NEAR_DUE_DATE_ALERT", new TemplateDefinition(
                    "NEAR_DUE_DATE_ALERT",
                    "Aviso objetivo de vencimento proximo (ate 3 dias)",
                    true,
                    ALL_CHANNELS,
                    ALL_PRODUCTS,
                    STANDARD_VARIABLES
            ),
            "OVERDUE_SUPPORT_NOTICE", new TemplateDefinition(
                    "OVERDUE_SUPPORT_NOTICE",
                    "Comunicacao cordial para regularizacao de boleto vencido",
                    true,
                    Set.of(CommunicationChannel.EMAIL, CommunicationChannel.WHATSAPP),
                    ALL_PRODUCTS,
                    STANDARD_VARIABLES
            )
    );

    public Optional<TemplateDefinition> findById(String templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalog.get(templateId));
    }

    public Set<String> getApprovedTemplateIds() {
        return catalog.keySet();
    }
}
