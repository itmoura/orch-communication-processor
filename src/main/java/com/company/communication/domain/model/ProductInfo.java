package com.company.communication.domain.model;

import com.company.communication.domain.enums.CommunicationChannel;
import com.company.communication.domain.enums.ProductType;

import java.util.List;

/**
 * Informacoes complementares e restricoes de um produto bancario.
 */
public record ProductInfo(
        ProductType productType,
        String displayName,
        List<String> characteristics,
        List<String> specificGuidelines,
        List<String> communicationRestrictions,
        List<CommunicationChannel> allowedChannels
) {
}
