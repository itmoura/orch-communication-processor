package com.company.communication.infrastructure.ai;

import com.company.communication.infrastructure.tools.PaymentBehaviorTool;
import com.company.communication.infrastructure.tools.ProductInformationTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registro central e politica de seguranca das Java Tools disponibilizadas ao modelo.
 * Garante whitelist explicita, limite maximo de chamadas e timeout por execucao.
 */
@Component
public class ToolCallingConfiguration {

    private final List<ToolCallback> registeredCallbacks;
    private final Set<String> allowedToolNames;
    private final int maxToolCalls;
    private final long toolTimeoutMs;

    public ToolCallingConfiguration(
            PaymentBehaviorTool paymentBehaviorTool,
            ProductInformationTool productInformationTool,
            @Value("${app.ai.max-tool-calls:2}") int maxToolCalls,
            @Value("${app.ai.tool-timeout-ms:3000}") long toolTimeoutMs
    ) {
        this.registeredCallbacks = List.of(ToolCallbacks.from(paymentBehaviorTool, productInformationTool));
        this.allowedToolNames = this.registeredCallbacks.stream()
                .map(cb -> cb.getToolDefinition().name())
                .collect(Collectors.toUnmodifiableSet());
        this.maxToolCalls = maxToolCalls;
        this.toolTimeoutMs = toolTimeoutMs;
    }

    public List<ToolCallback> getRegisteredCallbacks() {
        return registeredCallbacks;
    }

    public boolean isToolAllowed(String toolName) {
        return toolName != null && allowedToolNames.contains(toolName);
    }

    public Set<String> getAllowedToolNames() {
        return allowedToolNames;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public long getToolTimeoutMs() {
        return toolTimeoutMs;
    }
}
