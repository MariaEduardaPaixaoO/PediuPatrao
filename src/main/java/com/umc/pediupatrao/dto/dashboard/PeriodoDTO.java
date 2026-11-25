package com.umc.pediupatrao.dto.dashboard;

import java.time.LocalDate;

/** Período de uma consulta do dashboard; início e fim são inclusivos. */
public record PeriodoDTO(String tipo, LocalDate inicio, LocalDate fim, long dias) {
}
