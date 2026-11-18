package com.company.communication.exception;

/**
 * Excecao lancada quando o cliente ou recurso de dominio nao e encontrado na base de dados.
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
