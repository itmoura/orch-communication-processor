package com.company.communication.infrastructure.mock;

import com.company.communication.application.port.ProductCatalogPort;
import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.ProductInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador local que carrega informacoes e restricoes dos produtos bancarios a partir de JSON.
 */
@Service
@Profile({"local", "default"})
public class MockProductCatalogService implements ProductCatalogPort {

    private static final Logger log = LoggerFactory.getLogger(MockProductCatalogService.class);

    private final ObjectMapper objectMapper;
    private final Resource productsResource;
    private Map<ProductType, ProductInfo> productsByType = Map.of();

    public MockProductCatalogService(
            ObjectMapper objectMapper,
            @Value("${app.mock.products-path}") Resource productsResource
    ) {
        this.objectMapper = objectMapper;
        this.productsResource = productsResource;
    }

    @PostConstruct
    void loadProducts() throws IOException {
        try (InputStream is = productsResource.getInputStream()) {
            List<ProductInfo> products = objectMapper.readValue(
                    is,
                    new TypeReference<>() {}
            );
            this.productsByType = products.stream()
                    .collect(Collectors.toUnmodifiableMap(ProductInfo::productType, Function.identity()));
            log.info("Carregados {} produtos mockados de {}", productsByType.size(), productsResource.getFilename());
        }
    }

    @Override
    public Optional<ProductInfo> findByProductType(ProductType productType) {
        return Optional.ofNullable(productsByType.get(productType));
    }
}
