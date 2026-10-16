package com.umc.pediupatrao.exception;

import org.springframework.http.HttpStatus;

public class NaoEncontradoException extends NegocioException {

    public NaoEncontradoException(String mensagem) {
        super(mensagem, HttpStatus.NOT_FOUND);
    }
}
