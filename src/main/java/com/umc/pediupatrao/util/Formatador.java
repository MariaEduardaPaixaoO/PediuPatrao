package com.umc.pediupatrao.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Formatação de datas para as páginas (acessível nos templates como {@code @fmt}).
 */
@Component("fmt")
public class Formatador {

    private static final DateTimeFormatter COMPLETO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter CURTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public String dataHora(LocalDateTime valor) {
        return valor == null ? "" : valor.format(COMPLETO);
    }

    public String dataHoraCurta(LocalDateTime valor) {
        return valor == null ? "" : valor.format(CURTO);
    }
}
