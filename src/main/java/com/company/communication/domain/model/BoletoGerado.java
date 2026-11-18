package com.company.communication.domain.model;

import com.company.communication.domain.enums.ProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Evento de entrada representando a geracao de um boleto bancario.
 */
public record BoletoGerado(
        @NotBlank(message = "eventId e obrigatorio")
        String eventId,

        @NotBlank(message = "eventType e obrigatorio")
        @Pattern(regexp = "BOLETO_GERADO", message = "eventType deve ser BOLETO_GERADO")
        String eventType,

        @NotNull(message = "occurredAt e obrigatorio")
        OffsetDateTime occurredAt,

        @NotBlank(message = "boletoId e obrigatorio")
        String boletoId,

        @NotBlank(message = "customerId e obrigatorio")
        String customerId,

        @NotNull(message = "amountInCents e obrigatorio")
        @Positive(message = "amountInCents deve ser maior que zero")
        Long amountInCents,

        @NotNull(message = "dueDate e obrigatorio")
        LocalDate dueDate,

        @NotNull(message = "productType e obrigatorio")
        ProductType productType
) {
}
