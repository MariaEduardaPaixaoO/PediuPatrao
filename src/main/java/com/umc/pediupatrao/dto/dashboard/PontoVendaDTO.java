package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;


public record PontoVendaDTO(
        String rotulo,
        LocalDate inicio,
        BigDecimal totalVendas,
        long quantidadeVendas,
        long quantidadePedidos,
        long quantidadeCancelados) {
}
