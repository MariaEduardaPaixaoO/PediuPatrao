package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;


public record VendasPeriodoResponse(
        PeriodoDTO periodo,
        PeriodoDTO periodoAnterior,
        String agrupamento,
        List<PontoVendaDTO> pontos,
        List<PontoVendaDTO> pontosAnterior,
        BigDecimal totalVendas,
        BigDecimal totalVendasAnterior) {
}
