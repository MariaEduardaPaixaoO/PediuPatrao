package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ErroResponse;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.exception.NegocioException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tradução de erros de negócio para respostas JSON das APIs. Erros de autorização
 * (AccessDeniedException) NÃO são tratados aqui: seguem para o Spring Security (HTTP 403).
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErroResponse> negocio(NegocioException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErroResponse(e.getStatus().value(), e.getMessage()));
    }

    @ExceptionHandler(AuditoriaException.class)
    public ResponseEntity<ErroResponse> auditoria(AuditoriaException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroResponse(500, e.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErroResponse> concorrencia(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErroResponse(409,
                "O registro foi alterado por outro usuário. Recarregue e tente novamente."));
    }
}
