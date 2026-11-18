package com.company.communication.application.port;

import com.company.communication.domain.enums.ProductType;
import com.company.communication.domain.model.ProductInfo;

import java.util.Optional;

/**
 * Porta de acesso ao catalogo de regras e caracteristicas dos produtos bancarios.
 */
public interface ProductCatalogPort {

    Optional<ProductInfo> findByProductType(ProductType productType);
}
