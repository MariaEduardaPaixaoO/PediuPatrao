package com.umc.pediupatrao.dto.dashboard;

import java.math.BigDecimal;

public record StatusQuantidadeDTO(String status, String descricao, long quantidade, BigDecimal percentual) {
}
