package com.umc.pediupatrao.entity;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Estados do pedido e regras de transição.
 *
 * Fluxo: RECEBIDO -> EM_PREPARACAO -> PRONTO -> (SAIU_PARA_ENTREGA | RETIRADO) -> FINALIZADO.
 * CANCELADO é excepcional e só é alcançado pela operação de cancelamento (exclusiva do GERENTE).
 */
public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Estados em que o pedido ainda não saiu: descontos, cancelamentos e edições dependem disso. */
    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean saida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    /** Próximos estados válidos a partir deste, conforme o tipo de entrega do pedido. */
    public Set<StatusPedido> proximos(TipoEntrega tipoEntrega) {
        switch (this) {
            case RECEBIDO:
                return Set.of(EM_PREPARACAO);
            case EM_PREPARACAO:
                return Set.of(PRONTO);
            case PRONTO:
                return Set.of(tipoEntrega == TipoEntrega.RETIRADA ? RETIRADO : SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return Set.of(FINALIZADO);
            default:
                return Set.of();
        }
    }

    public static Optional<StatusPedido> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StatusPedido.valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Descrição amigável de um valor de status armazenado (aceita valores legados). */
    public static String descricaoDe(String valor) {
        return interpretar(valor).map(StatusPedido::getDescricao).orElse(valor);
    }
}
