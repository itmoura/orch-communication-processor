package com.company.communication.exception;

import java.util.List;

/**
 * Excecao lancada quando a decisao gerada pela IA permanece invalida apos as tentativas permitidas.
 */
public class InvalidDecisionException extends RuntimeException {

    private final List<String> violations;

    public InvalidDecisionException(String message, List<String> violations) {
        super(message);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
