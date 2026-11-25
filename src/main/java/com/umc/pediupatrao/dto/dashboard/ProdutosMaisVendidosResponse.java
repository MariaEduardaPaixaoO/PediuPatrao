package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/** RF04: ranking de produtos dos pedidos finalizados. totalProdutos é contado antes do limite. */
public record ProdutosMaisVendidosResponse(
        PeriodoDTO periodo,
        String criterio,
        int limite,
        int totalProdutos,
        List<ProdutoRankingDTO> itens) {
}
