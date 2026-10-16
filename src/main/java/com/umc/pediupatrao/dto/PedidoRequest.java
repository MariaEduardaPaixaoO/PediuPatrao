package com.umc.pediupatrao.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Dados aceitos para registrar/alterar um pedido. Não existem campos para total, desconto,
 * status, responsável ou horários: esses valores nunca vêm do cliente HTTP.
 */
public class PedidoRequest {

    private String clienteId;
    private String tipoEntrega;
    private List<ItemPedidoRequest> itens = new ArrayList<>();

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getTipoEntrega() { return tipoEntrega; }
    public void setTipoEntrega(String tipoEntrega) { this.tipoEntrega = tipoEntrega; }

    public List<ItemPedidoRequest> getItens() { return itens; }
    public void setItens(List<ItemPedidoRequest> itens) { this.itens = itens; }
}
