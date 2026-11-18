package com.company.communication.application.port;

import com.company.communication.domain.model.CustomerBehaviorProfile;

import java.util.Optional;

/**
 * Porta de acesso ao perfil comportamental do cliente.
 */
public interface CustomerDataPort {

    Optional<CustomerBehaviorProfile> findByCustomerId(String customerId);
}
