package com.company.communication.controller;

import com.company.communication.application.CommunicationProcessor;
import com.company.communication.domain.model.BoletoGerado;
import com.company.communication.domain.model.CommunicationDecision;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint REST de entrada para eventos {@link BoletoGerado}.
 * Responsavel apenas por desserializacao, validacao contratual e encaminhamento ao {@link CommunicationProcessor}.
 */
@RestController
@RequestMapping("/api/v1/communications")
public class CommunicationController {

    private final CommunicationProcessor communicationProcessor;

    public CommunicationController(CommunicationProcessor communicationProcessor) {
        this.communicationProcessor = communicationProcessor;
    }

    @PostMapping("/decision")
    public ResponseEntity<CommunicationDecision> decideCommunication(@Valid @RequestBody BoletoGerado event) {
        CommunicationDecision decision = communicationProcessor.process(event);
        return ResponseEntity.ok(decision);
    }
}
