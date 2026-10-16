package com.umc.pediupatrao.entity;

import java.util.Locale;
import java.util.Optional;

public enum TipoEntrega {
    ENTREGA("Entrega"),
    RETIRADA("Retirada no balcão");

    private final String descricao;

    TipoEntrega(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public static Optional<TipoEntrega> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TipoEntrega.valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
