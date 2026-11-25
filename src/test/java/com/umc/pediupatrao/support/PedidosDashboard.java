package com.umc.pediupatrao.support;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Pedidos para os testes do dashboard, com os valores calculados como no PedidoService. */
public final class PedidosDashboard {

    private PedidosDashboard() {
    }

    public static ItemPedido item(String produtoId, String nome, int quantidade, String precoUnitario) {
        BigDecimal preco = new BigDecimal(precoUnitario).setScale(2);
        return new ItemPedido(produtoId, nome, quantidade, preco,
                preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2));
    }

    public static Pedido pedido(String id, StatusPedido status, LocalDateTime entrada, String descontoPercentual,
            ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setClienteId("cli1");
        p.setClienteNome("Cliente Teste");
        p.setTipoEntrega(TipoEntrega.ENTREGA);
        p.setStatus(status == null ? null : status.name());
        p.setDataHoraEntrada(entrada);
        List<ItemPedido> lista = new ArrayList<>(List.of(itens));
        p.setItens(lista);
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (ItemPedido i : lista) {
            subtotal = subtotal.add(i.getSubtotal());
        }
        BigDecimal pct = new BigDecimal(descontoPercentual).setScale(2);
        BigDecimal desconto = subtotal.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        p.setSubtotal(subtotal);
        p.setDescontoPercentual(pct);
        p.setValorDesconto(desconto);
        p.setValorTotal(subtotal.subtract(desconto));
        return p;
    }

    public static Pedido pedido(String id, StatusPedido status, LocalDateTime entrada, ItemPedido... itens) {
        return pedido(id, status, entrada, "0", itens);
    }
}
