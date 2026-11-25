package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

/** Indicadores do período anterior de mesma duração. Variação nula quando a base é zero. */
public record ComparacaoPeriodoDTO(
        PeriodoDTO periodoAnterior,
        BigDecimal totalVendasAnterior,
        long quantidadePedidosAnterior,
        long quantidadeVendasAnterior,
        BigDecimal ticketMedioAnterior,
        long quantidadeCanceladosAnterior,
        BigDecimal variacaoVendasPercentual,
        BigDecimal variacaoPedidosPercentual,
        BigDecimal variacaoTicketMedioPercentual,
        boolean dadosSuficientes) {
}
