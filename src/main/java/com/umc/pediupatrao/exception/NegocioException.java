package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

/**
 * Violação de regra de negócio ou validação (resulta em mensagem amigável para o usuário).
 */
public class NegocioException extends RuntimeException {

    private final HttpStatus status;

    public NegocioException(String mensagem) {
        this(mensagem, HttpStatus.BAD_REQUEST);
    }

    public NegocioException(String mensagem, HttpStatus status) {
        super(mensagem);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
