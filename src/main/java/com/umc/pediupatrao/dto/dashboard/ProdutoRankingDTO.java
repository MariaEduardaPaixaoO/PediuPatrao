package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/** valorbruto usa o preço gravado no item; valorLiquido já tem o desconto do pedido rateado. */
public record ProdutoRankingDTO(
        int posicao,
        String produtoId,
        String nome,
        long quantidadeVendida,
        BigDecimal valorBruto,
        BigDecimal valorLiquido,
        BigDecimal participacaoPercentual) {
}
