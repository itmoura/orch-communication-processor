package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.ProductType;

import java.util.Set;

/**
 * Definicao de um template homologado no catalogo local de comunicacao.
 */
public record TemplateDefinition(
        String templateId,
        String description,
        boolean approved,
        Set<CommunicationChannel> supportedChannels,
        Set<ProductType> supportedProducts,
        Set<String> requiredVariables
) {
}
