package com.company.communication.infrastructure.tools;

import com.company.communication.application.port.ProductCatalogPort;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.ProductInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Ferramenta Java disponibilizada ao modelo via Spring AI Tool Calling para consultar
 * caracteristicas, orientacoes e restricoes especificas do produto associado ao boleto.
 */
@Component
public class ProductInformationTool {

    private static final Logger log = LoggerFactory.getLogger(ProductInformationTool.class);

    private final ProductCatalogPort productCatalogPort;

    public ProductInformationTool(ProductCatalogPort productCatalogPort) {
        this.productCatalogPort = productCatalogPort;
    }

    @Tool(
            name = "getProductInformation",
            description = "Consulta informacoes complementares do produto associado ao boleto: "
                    + "tipo de produto, caracteristicas relevantes, orientacoes especificas e restricoes de comunicacao."
    )
    public ProductInfo getProductInformation(
            @ToolParam(description = "Tipo de produto bancario: PERSONAL_LOAN ou CREDIT_CARD")
            String productType
    ) {
        if (productType == null || productType.isBlank()) {
            throw new IllegalArgumentException("Argumento productType e obrigatorio para ProductInformationTool.");
        }

        final ProductType parsedType;
        try {
            parsedType = ProductType.valueOf(productType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("productType invalido: " + productType + ". Valores aceitos: PERSONAL_LOAN, CREDIT_CARD.");
        }

        log.info("Tool executada: ProductInformationTool.getProductInformation(productType={})", parsedType);
        return productCatalogPort.findByProductType(parsedType)
                .orElseThrow(() -> new IllegalArgumentException("Produto nao encontrado no catalogo: " + parsedType));
    }
}
