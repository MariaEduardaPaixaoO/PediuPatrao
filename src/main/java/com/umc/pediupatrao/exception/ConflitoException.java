package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

/**
 * Operação incompatível com o estado atual do recurso (ex.: transição de status inválida).
 */
public class ConflitoException extends NegocioException {

    public ConflitoException(String mensagem) {
        super(mensagem, HttpStatus.CONFLICT);
    }
}
