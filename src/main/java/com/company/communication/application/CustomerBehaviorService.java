package com.company.communication.application;

import com.company.communication.application.port.CustomerDataPort;
import com.company.communication.domain.model.CustomerBehaviorProfile;
import com.company.communication.exception.CustomerNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Servico de aplicacao responsavel por recuperar o perfil comportamental do cliente.
 */
@Service
public class CustomerBehaviorService {

    private final CustomerDataPort customerDataPort;

    public CustomerBehaviorService(CustomerDataPort customerDataPort) {
        this.customerDataPort = customerDataPort;
    }

    public CustomerBehaviorProfile getProfile(String customerId) {
        return customerDataPort.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Perfil comportamental nao encontrado para o cliente: " + customerId
                ));
    }
}
