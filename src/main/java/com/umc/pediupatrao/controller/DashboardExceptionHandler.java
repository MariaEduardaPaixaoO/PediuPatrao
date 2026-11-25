package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ErroResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Falha no MongoDB durante uma consulta do dashboard: 503 sem detalhes da conexão.
 * Restrito ao DashboardController para não mudar o comportamento das outras APIs.
 */
@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardExceptionHandler.class);

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErroResponse> bancoIndisponivel(DataAccessException e) {
        log.error("Falha ao consultar o MongoDB para o dashboard: {}", e.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErroResponse(503,
                "Não foi possível consultar os dados do dashboard agora. Tente novamente em instantes."));
    }
}
