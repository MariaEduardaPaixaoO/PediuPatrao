package com.umc.pediupatrao.service.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.exception.NegocioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FiltroPeriodoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);

    @Test
    void semParametrosUsaUltimos30Dias() {
        FiltroPeriodo f = FiltroPeriodo.resolver(null, null, null, HOJE);
        assertEquals(FiltroPeriodo.ULTIMOS_30_DIAS, f.tipo());
        assertEquals(LocalDate.of(2026, 10, 26), f.inicio());
        assertEquals(HOJE, f.fim());
        assertEquals(30, f.dias());
    }

    @Test
    void periodosPredefinidos() {
        FiltroPeriodo hoje = FiltroPeriodo.resolver("HOJE", null, null, HOJE);
        assertEquals(HOJE, hoje.inicio());
        assertEquals(HOJE, hoje.fim());
        assertEquals(1, hoje.dias());

        FiltroPeriodo sete = FiltroPeriodo.resolver("ultimos_7_dias", "", " ", HOJE);
        assertEquals(LocalDate.of(2026, 11, 18), sete.inicio());
        assertEquals(7, sete.dias());

        FiltroPeriodo mes = FiltroPeriodo.resolver("MES_ATUAL", null, null, HOJE);
        assertEquals(LocalDate.of(2026, 11, 1), mes.inicio());
        assertEquals(HOJE, mes.fim());
        assertEquals(24, mes.dias());
    }

    @Test
    void periodoPersonalizadoValido() {
        FiltroPeriodo f = FiltroPeriodo.resolver(null, "2026-11-01", "2026-11-10", HOJE);
        assertEquals(FiltroPeriodo.PERSONALIZADO, f.tipo());
        assertEquals(10, f.dias());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), f.inicioInclusivo());
        assertEquals(LocalDateTime.of(2026, 11, 11, 0, 0), f.fimExclusivo());

        FiltroPeriodo explicito = FiltroPeriodo.resolver("PERSONALIZADO", "2026-11-24", "2026-11-24", HOJE);
        assertEquals(1, explicito.dias());
    }

    @Test
    void periodoAnteriorTemMesmaDuracaoEImediatamenteAntes() {
        FiltroPeriodo f = FiltroPeriodo.resolver(null, "2026-11-01", "2026-11-10", HOJE);
        FiltroPeriodo anterior = f.anterior();
        assertEquals(FiltroPeriodo.ANTERIOR, anterior.tipo());
        assertEquals(LocalDate.of(2026, 10, 22), anterior.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), anterior.fim());
        assertEquals(f.dias(), anterior.dias());
    }

    @Test
    void contemConsideraInicioInclusivoEFimExclusivo() {
        FiltroPeriodo f = FiltroPeriodo.resolver("HOJE", null, null, HOJE);
        assertTrue(f.contem(LocalDateTime.of(2026, 11, 24, 0, 0)));
        assertTrue(f.contem(LocalDateTime.of(2026, 11, 24, 23, 59, 59)));
        assertFalse(f.contem(LocalDateTime.of(2026, 11, 25, 0, 0)));
        assertFalse(f.contem(LocalDateTime.of(2026, 11, 23, 23, 59, 59)));
        assertFalse(f.contem(null));
    }

    @Test
    void datasInvalidasSaoRecusadas() {
        assertMensagem("Data inicial inválida. Use o formato AAAA-MM-DD.", null, "24/11/2026", "2026-11-24");
        assertMensagem("Data final inválida. Use o formato AAAA-MM-DD.", null, "2026-11-01", "2026-02-30");
        assertMensagem("Data inicial inválida. Use o formato AAAA-MM-DD.", null, "abc", "2026-11-24");
    }

    @Test
    void intervalosInconsistentesSaoRecusados() {
        assertMensagem("A data final não pode ser anterior à data inicial.", null, "2026-11-10", "2026-11-01");
        assertMensagem("A data inicial não pode estar no futuro.", null, "2026-11-25", "2026-11-26");
        assertMensagem("A data final não pode estar no futuro.", null, "2026-11-20", "2026-11-25");
        assertMensagem("Informe a data inicial e a data final.", null, "2026-11-01", null);
        assertMensagem("Informe a data inicial e a data final.", null, null, "2026-11-01");
        assertMensagem("O intervalo máximo de consulta é de 366 dias.", null, "2025-01-01", "2026-11-24");
        assertMensagem("Informe um período predefinido ou as datas inicial e final, não os dois.",
                "HOJE", "2026-11-01", "2026-11-10");
    }

    @Test
    void intervaloDeExatamente366DiasEhAceito() {
        FiltroPeriodo f = FiltroPeriodo.resolver(null, "2025-11-24", "2026-11-24", HOJE);
        assertEquals(366, f.dias());
    }

    @Test
    void periodoDesconhecidoEhRecusado() {
        NegocioException e = assertThrows(NegocioException.class,
                () -> FiltroPeriodo.resolver("ANO_PASSADO", null, null, HOJE));
        assertTrue(e.getMessage().startsWith("Período inválido."));
    }

    private static void assertMensagem(String esperada, String periodo, String inicio, String fim) {
        NegocioException e = assertThrows(NegocioException.class,
                () -> FiltroPeriodo.resolver(periodo, inicio, fim, HOJE));
        assertEquals(esperada, e.getMessage());
    }
}
