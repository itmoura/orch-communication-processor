package com.company.communication.infrastructure.mock;

import com.company.communication.application.port.CustomerDataPort;
import com.company.communication.domain.model.CustomerBehaviorProfile;
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
 * Adaptador local que carrega perfis comportamentais ficticios a partir de JSON.
 */
@Service
@Profile({"local", "default"})
public class MockCustomerDataService implements CustomerDataPort {

    private static final Logger log = LoggerFactory.getLogger(MockCustomerDataService.class);

    private final ObjectMapper objectMapper;
    private final Resource customersResource;
    private Map<String, CustomerBehaviorProfile> profilesById = Map.of();

    public MockCustomerDataService(
            ObjectMapper objectMapper,
            @Value("${app.mock.customers-path}") Resource customersResource
    ) {
        this.objectMapper = objectMapper;
        this.customersResource = customersResource;
    }

    @PostConstruct
    void loadCustomers() throws IOException {
        try (InputStream is = customersResource.getInputStream()) {
            List<CustomerBehaviorProfile> profiles = objectMapper.readValue(
                    is,
                    new TypeReference<>() {}
            );
            this.profilesById = profiles.stream()
                    .collect(Collectors.toUnmodifiableMap(CustomerBehaviorProfile::customerId, Function.identity()));
            log.info("Carregados {} perfis comportamentais mockados de {}", profilesById.size(), customersResource.getFilename());
        }
    }

    @Override
    public Optional<CustomerBehaviorProfile> findByCustomerId(String customerId) {
        return Optional.ofNullable(profilesById.get(customerId));
    }
}
