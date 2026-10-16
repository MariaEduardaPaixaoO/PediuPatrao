package com.umc.pediupatrao.dto;

import java.time.LocalDateTime;

public class ErroResponse {

    private final int status;
    private final String mensagem;
    private final LocalDateTime timestamp = LocalDateTime.now();

    public ErroResponse(int status, String mensagem) {
        this.status = status;
        this.mensagem = mensagem;
    }

    public int getStatus() { return status; }
    public String getMensagem() { return mensagem; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
