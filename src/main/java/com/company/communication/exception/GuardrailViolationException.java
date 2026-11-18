package com.company.communication.exception;

/**
 * Excecao lancada quando os guardrails de entrada detectam contexto invalido ou dados sensiveis proibidos.
 */
public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
