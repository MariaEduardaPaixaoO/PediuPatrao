package com.umc.pediupatrao.service;

import static com.umc.pediupatrao.support.PedidosDashboard.item;
import static com.umc.pediupatrao.support.PedidosDashboard.pedido;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.dashboard.ProdutosMaisVendidosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.dashboard.FiltroPeriodo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    // 24/11/2026 20:00 em São Paulo
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-24T23:00:00Z"),
            ZoneId.of("America/Sao_Paulo"));

    private final PedidoRepository repositorio = mock(PedidoRepository.class);
    private final DashboardService service = new DashboardService(repositorio, RELOGIO);

    @Test
    void hojeVemDoRelogioDoServidor() {
        assertEquals(LocalDate.of(2026, 11, 24), service.hoje());
        FiltroPeriodo f = service.resolverPeriodo(null, null, null);
        assertEquals(LocalDate.of(2026, 10, 26), f.inicio());
    }

    @Test
    void resumoConsultaOPeriodoEOPeriodoAnteriorNoBanco() {
        LocalDateTime ini = LocalDateTime.of(2026, 11, 18, 0, 0);
        LocalDateTime fim = LocalDateTime.of(2026, 11, 25, 0, 0);
        LocalDateTime iniAnt = LocalDateTime.of(2026, 11, 11, 0, 0);
        when(repositorio.buscarParaDashboard(eq(ini), eq(fim))).thenReturn(List.of(
                pedido("p1", StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 20, 19, 0),
                        item("pz1", "Pizza", 2, "50.00")),
                pedido("p2", StatusPedido.CANCELADO, LocalDateTime.of(2026, 11, 21, 19, 0),
                        item("pz1", "Pizza", 1, "50.00"))));
        when(repositorio.buscarParaDashboard(eq(iniAnt), eq(ini))).thenReturn(List.of(
                pedido("a1", StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 12, 19, 0),
                        item("pz1", "Pizza", 1, "50.00"))));

        ResumoDashboardResponse r = service.resumo(service.resolverPeriodo("ULTIMOS_7_DIAS", null, null));

        verify(repositorio).buscarParaDashboard(ini, fim);
        verify(repositorio).buscarParaDashboard(iniAnt, ini);
        verifyNoMoreInteractions(repositorio);
        assertEquals(new BigDecimal("100.00"), r.totalVendas());
        assertEquals(1, r.quantidadeCancelados());
        assertEquals(new BigDecimal("50.00"), r.comparacao().totalVendasAnterior());
        assertEquals(new BigDecimal("100.00"), r.comparacao().variacaoVendasPercentual());
        assertEquals(LocalDateTime.of(2026, 11, 24, 20, 0), r.geradoEm());
    }

    @Test
    void statusERankingConsultamSomenteOPeriodo() {
        FiltroPeriodo hoje = service.resolverPeriodo("HOJE", null, null);
        service.statusPedidos(hoje);
        service.produtosMaisVendidos(hoje, null);
        verify(repositorio, times(2)).buscarParaDashboard(LocalDateTime.of(2026, 11, 24, 0, 0),
                LocalDateTime.of(2026, 11, 25, 0, 0));
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    void rankingUsaLimitePadraoEValidaOsLimites() {
        FiltroPeriodo hoje = service.resolverPeriodo("HOJE", null, null);
        ProdutosMaisVendidosResponse padrao = service.produtosMaisVendidos(hoje, null);
        assertEquals(DashboardService.LIMITE_RANKING_PADRAO, padrao.limite());
        assertEquals(50, service.produtosMaisVendidos(hoje, 50).limite());

        NegocioException zero = assertThrows(NegocioException.class, () -> service.produtosMaisVendidos(hoje, 0));
        assertEquals("O limite do ranking deve estar entre 1 e 50.", zero.getMessage());
        assertThrows(NegocioException.class, () -> service.produtosMaisVendidos(hoje, 51));
    }

    @Test
    void semPedidosNoBancoRetornaIndicadoresZerados() {
        when(repositorio.buscarParaDashboard(any(), any())).thenReturn(List.of());
        ResumoDashboardResponse r = service.resumo(service.resolverPeriodo("MES_ATUAL", null, null));
        assertEquals(new BigDecimal("0.00"), r.totalVendas());
        assertEquals(0, r.quantidadePedidos());
        assertEquals(null, r.ticketMedio());
    }
}
