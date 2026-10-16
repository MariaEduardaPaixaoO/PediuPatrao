package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;

/**
 * Registro do histórico de transições de um pedido (quem, quando, de/para).
 */
public class TransicaoStatus {

    private String statusAnterior;
    private String statusNovo;
    private String usuario;
    private String perfil;
    private LocalDateTime dataHora;
    private String observacao;

    public TransicaoStatus() {
    }

    public TransicaoStatus(String statusAnterior, String statusNovo, String usuario,
            String perfil, LocalDateTime dataHora, String observacao) {
        this.statusAnterior = statusAnterior;
        this.statusNovo = statusNovo;
        this.usuario = usuario;
        this.perfil = perfil;
        this.dataHora = dataHora;
        this.observacao = observacao;
    }

    public String getStatusAnterior() { return statusAnterior; }
    public void setStatusAnterior(String statusAnterior) { this.statusAnterior = statusAnterior; }

    public String getStatusNovo() { return statusNovo; }
    public void setStatusNovo(String statusNovo) { this.statusNovo = statusNovo; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
