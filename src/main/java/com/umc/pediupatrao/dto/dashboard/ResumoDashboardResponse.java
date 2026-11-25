package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF01: indicadores de vendas. Só pedidos finalizados entram em totalVendas e ticketMedio. */
public record ResumoDashboardResponse(
        PeriodoDTO periodo,
        BigDecimal totalVendas,
        long quantidadeVendas,
        long quantidadePedidos,
        BigDecimal ticketMedio,
        long quantidadeCancelados,
        BigDecimal valorCancelado,
        BigDecimal taxaCancelamentoPercentual,
        long pedidosEmAndamento,
        BigDecimal valorEmAndamento,
        long pedidosNaoClassificados,
        ComparacaoPeriodoDTO comparacao,
        LocalDateTime geradoEm) {
}
