package com.umc.pediupatrao.entity;

/**
 * Tipos de operação registrados na auditoria.
 */
public enum TipoOperacao {
    CRIACAO_PEDIDO("Inclusão de pedido (entrada)"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS("Mudança de status"),
    SAIDA_PEDIDO("Saída do pedido (entrega/retirada)"),
    DESCONTO_PEDIDO("Desconto em pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),
    CRIACAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente"),
    CRIACAO_USUARIO("Inclusão de usuário"),
    ALTERACAO_USUARIO("Alteração de usuário"),
    EXCLUSAO_USUARIO("Exclusão de usuário");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
