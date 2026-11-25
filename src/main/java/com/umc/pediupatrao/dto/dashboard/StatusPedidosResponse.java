package com.umc.pediupatrao.dto.dashboard;

import java.util.List;

/** RF03: os sete status na ordem do fluxo, inclusive os zerado. Legados vão em naoClassificados. */
public record StatusPedidosResponse(
        PeriodoDTO periodo,
        long total,
        List<StatusQuantidadeDTO> itens,
        long naoClassificados) {
}
