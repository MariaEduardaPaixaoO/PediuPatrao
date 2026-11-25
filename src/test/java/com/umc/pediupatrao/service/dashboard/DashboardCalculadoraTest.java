package com.umc.pediupatrao.service.dashboard;

import static com.umc.pediupatrao.support.PedidosDashboard.item;
import static com.umc.pediupatrao.support.PedidosDashboard.pedido;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.umc.pediupatrao.dto.dashboard.PontoVendaDTO;
import com.umc.pediupatrao.dto.dashboard.ProdutoRankingDTO;
import com.umc.pediupatrao.dto.dashboard.ProdutosMaisVendidosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.StatusPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.StatusQuantidadeDTO;
import com.umc.pediupatrao.dto.dashboard.VendasPeriodoResponse;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Período 01/11 a 10/11/2026; anterior 22/10 a 31/10/2026. */
class DashboardCalculadoraTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 24);
    private static final FiltroPeriodo PERIODO = FiltroPeriodo.resolver(null, "2026-11-01", "2026-11-10", HOJE);
    private static final FiltroPeriodo ANTERIOR = PERIODO.anterior();
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 24, 20, 0);

    private static LocalDateTime dia(int mes, int dia, int hora) {
        return LocalDateTime.of(2026, mes, dia, hora, 0);
    }

    private static BigDecimal v(String valor) {
        return new BigDecimal(valor);
    }

    // 3 finalizados, 1 cancelado, 2 em andamento, 1 do modelo antigo e 1 fora do período
    private static List<Pedido> atuais() {
        return Arrays.asList(
                pedido("p1", StatusPedido.FINALIZADO, dia(11, 1, 19), item("pz1", "Pizza Calabresa", 2, "50.00")),
                pedido("p2", StatusPedido.FINALIZADO, dia(11, 1, 20), "10",
                        item("pz2", "Pizza Margherita", 1, "45.00"), item("bb1", "Refrigerante 2L", 1, "15.00")),
                pedido("p3", StatusPedido.FINALIZADO, dia(11, 5, 21), item("pz1", "Pizza Calabresa", 1, "50.00"),
                        item("bb1", "Refrigerante 2L", 2, "15.00")),
                pedido("p4", StatusPedido.CANCELADO, dia(11, 5, 22), item("pz1", "Pizza Calabresa", 5, "50.00")),
                pedido("p5", StatusPedido.EM_PREPARACAO, dia(11, 10, 18), item("pz2", "Pizza Margherita", 1, "45.00")),
                pedido("p6", StatusPedido.SAIU_PARA_ENTREGA, dia(11, 10, 19), item("es1", "Esfiha", 4, "6.50")),
                legado("p7", dia(11, 3, 12)),
                pedido("p8", StatusPedido.FINALIZADO, dia(11, 11, 0), item("pz1", "Pizza Calabresa", 9, "50.00")));
    }

    private static Pedido legado(String id, LocalDateTime entrada) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setStatus("Entregue");
        p.setDataHoraEntrada(entrada);
        p.setValorTotal(v("30.00"));
        return p;
    }

    private static List<Pedido> anteriores() {
        return List.of(
                pedido("a1", StatusPedido.FINALIZADO, dia(10, 25, 19), item("pz1", "Pizza Calabresa", 1, "50.00")),
                pedido("a2", StatusPedido.FINALIZADO, dia(10, 31, 23), item("pz1", "Pizza Calabresa", 2, "50.00")),
                pedido("a3", StatusPedido.CANCELADO, dia(10, 30, 20), item("pz1", "Pizza Calabresa", 1, "50.00")));
    }

    // ------------------------------------------------------------------ RF01

    @Test
    void resumoNaoContaCanceladosComoVendas() {
        ResumoDashboardResponse r = DashboardCalculadora.resumo(atuais(), PERIODO, anteriores(), ANTERIOR, AGORA);

        // p1 100,00 + p2 54,00 (60,00 - 10%) + p3 80,00 = 234,00
        assertEquals(v("234.00"), r.totalVendas());
        assertEquals(3, r.quantidadeVendas());
        assertEquals(7, r.quantidadePedidos()); // p8 está fora do período
        assertEquals(v("78.00"), r.ticketMedio());
        assertEquals(1, r.quantidadeCancelados());
        assertEquals(v("250.00"), r.valorCancelado());
        assertEquals(v("14.29"), r.taxaCancelamentoPercentual()); // 1 de 7
        assertEquals(2, r.pedidosEmAndamento());
        assertEquals(v("71.00"), r.valorEmAndamento()); // 45,00 + 26,00
        assertEquals(1, r.pedidosNaoClassificados());
        assertEquals(AGORA, r.geradoEm());
        assertEquals(LocalDate.of(2026, 11, 1), r.periodo().inicio());
        assertEquals(10, r.periodo().dias());
    }

    @Test
    void resumoComparaComPeriodoAnterior() {
        ResumoDashboardResponse r = DashboardCalculadora.resumo(atuais(), PERIODO, anteriores(), ANTERIOR, AGORA);

        assertEquals(v("150.00"), r.comparacao().totalVendasAnterior());
        assertEquals(3, r.comparacao().quantidadePedidosAnterior());
        assertEquals(2, r.comparacao().quantidadeVendasAnterior());
        assertEquals(v("75.00"), r.comparacao().ticketMedioAnterior());
        assertEquals(1, r.comparacao().quantidadeCanceladosAnterior());
        assertEquals(v("56.00"), r.comparacao().variacaoVendasPercentual());   // 234 vs 150
        assertEquals(v("133.33"), r.comparacao().variacaoPedidosPercentual()); // 7 vs 3
        assertEquals(v("4.00"), r.comparacao().variacaoTicketMedioPercentual()); // 78 vs 75
        assertTrue(r.comparacao().dadosSuficientes());
        assertEquals(LocalDate.of(2026, 10, 22), r.comparacao().periodoAnterior().inicio());
    }

    @Test
    void resumoSemDadosRetornaZerosESemVariacao() {
        ResumoDashboardResponse r = DashboardCalculadora.resumo(List.of(), PERIODO, null, ANTERIOR, AGORA);

        assertEquals(v("0.00"), r.totalVendas());
        assertEquals(0, r.quantidadePedidos());
        assertNull(r.ticketMedio());
        assertEquals(v("0.00"), r.taxaCancelamentoPercentual());
        assertNull(r.comparacao().variacaoVendasPercentual());
        assertNull(r.comparacao().variacaoPedidosPercentual());
        assertNull(r.comparacao().ticketMedioAnterior());
        assertFalse(r.comparacao().dadosSuficientes());
    }

    @Test
    void resumoTrataValoresNulos() {
        Pedido semValor = pedido("n1", StatusPedido.FINALIZADO, dia(11, 2, 12));
        semValor.setValorTotal(null);
        Pedido semData = pedido("n2", StatusPedido.FINALIZADO, null, item("pz1", "Pizza", 1, "50.00"));
        List<Pedido> lista = new ArrayList<>(Arrays.asList(semValor, null, semData));

        ResumoDashboardResponse r = DashboardCalculadora.resumo(lista, PERIODO, List.of(), ANTERIOR, AGORA);

        assertEquals(1, r.quantidadePedidos()); // o sem data e o nulo são ignorados
        assertEquals(1, r.quantidadeVendas());
        assertEquals(v("0.00"), r.totalVendas());
        assertEquals(v("0.00"), r.ticketMedio());
    }

    @Test
    void variacaoNegativaEZero() {
        assertEquals(v("-50.00"), DashboardCalculadora.variacao(v("50"), v("100")));
        assertEquals(v("0.00"), DashboardCalculadora.variacao(v("100"), v("100")));
        assertNull(DashboardCalculadora.variacao(v("100"), BigDecimal.ZERO));
        assertNull(DashboardCalculadora.variacao(null, v("100")));
    }

    // ------------------------------------------------------------------ RF02

    @Test
    void serieDiariaCobreTodosOsDiasDoPeriodo() {
        VendasPeriodoResponse r = DashboardCalculadora.vendas(atuais(), PERIODO, anteriores(), ANTERIOR);

        assertEquals("DIA", r.agrupamento());
        assertEquals(10, r.pontos().size());
        assertEquals(10, r.pontosAnterior().size());

        PontoVendaDTO dia1 = r.pontos().get(0);
        assertEquals("01/11", dia1.rotulo());
        assertEquals(LocalDate.of(2026, 11, 1), dia1.inicio());
        assertEquals(v("154.00"), dia1.totalVendas());
        assertEquals(2, dia1.quantidadeVendas());
        assertEquals(2, dia1.quantidadePedidos());

        PontoVendaDTO dia2 = r.pontos().get(1);
        assertEquals(v("0.00"), dia2.totalVendas());
        assertEquals(0, dia2.quantidadePedidos());

        PontoVendaDTO dia5 = r.pontos().get(4);
        assertEquals(v("80.00"), dia5.totalVendas());
        assertEquals(2, dia5.quantidadePedidos());
        assertEquals(1, dia5.quantidadeCancelados());

        PontoVendaDTO dia3 = r.pontos().get(2); // pedido do modelo antigo: conta como pedido, não como venda
        assertEquals(1, dia3.quantidadePedidos());
        assertEquals(0, dia3.quantidadeVendas());

        assertEquals(v("234.00"), r.totalVendas());
        assertEquals(v("150.00"), r.totalVendasAnterior());
    }

    @Test
    void periodosLongosSaoAgrupadosPorMes() {
        FiltroPeriodo longo = FiltroPeriodo.resolver(null, "2026-08-15", "2026-11-24", HOJE);
        List<Pedido> lista = List.of(
                pedido("m1", StatusPedido.FINALIZADO, dia(8, 20, 12), item("pz1", "Pizza", 1, "50.00")),
                pedido("m2", StatusPedido.FINALIZADO, dia(8, 31, 23), item("pz1", "Pizza", 1, "50.00")),
                pedido("m3", StatusPedido.FINALIZADO, dia(11, 24, 10), item("pz1", "Pizza", 2, "50.00")));

        VendasPeriodoResponse r = DashboardCalculadora.vendas(lista, longo, List.of(), longo.anterior());

        assertEquals("MES", r.agrupamento());
        assertEquals(4, r.pontos().size()); // ago, set, out, nov
        assertEquals("08/2026", r.pontos().get(0).rotulo());
        assertEquals(v("100.00"), r.pontos().get(0).totalVendas());
        assertEquals(v("0.00"), r.pontos().get(1).totalVendas());
        assertEquals(v("100.00"), r.pontos().get(3).totalVendas());
    }

    // ------------------------------------------------------------------ RF03

    @Test
    void distribuicaoTrazOsSeteStatusDoSistema() {
        StatusPedidosResponse r = DashboardCalculadora.status(atuais(), PERIODO);

        assertEquals(7, r.total());
        assertEquals(1, r.naoClassificados());
        assertEquals(StatusPedido.values().length, r.itens().size());
        for (int i = 0; i < StatusPedido.values().length; i++) {
            assertEquals(StatusPedido.values()[i].name(), r.itens().get(i).status());
            assertEquals(StatusPedido.values()[i].getDescricao(), r.itens().get(i).descricao());
        }
        assertEquals(3, quantidade(r, StatusPedido.FINALIZADO));
        assertEquals(1, quantidade(r, StatusPedido.CANCELADO));
        assertEquals(1, quantidade(r, StatusPedido.EM_PREPARACAO));
        assertEquals(1, quantidade(r, StatusPedido.SAIU_PARA_ENTREGA));
        assertEquals(0, quantidade(r, StatusPedido.RECEBIDO));
        assertEquals(0, quantidade(r, StatusPedido.PRONTO));
        assertEquals(0, quantidade(r, StatusPedido.RETIRADO));
        assertEquals(v("42.86"), doStatus(r, StatusPedido.FINALIZADO).percentual()); // 3 de 7
    }

    @Test
    void distribuicaoSemPedidos() {
        StatusPedidosResponse r = DashboardCalculadora.status(List.of(), PERIODO);
        assertEquals(0, r.total());
        assertEquals(7, r.itens().size());
        for (StatusQuantidadeDTO s : r.itens()) {
            assertEquals(0, s.quantidade());
            assertEquals(v("0.00"), s.percentual());
        }
    }

    private static long quantidade(StatusPedidosResponse r, StatusPedido s) {
        return doStatus(r, s).quantidade();
    }

    private static StatusQuantidadeDTO doStatus(StatusPedidosResponse r, StatusPedido s) {
        return r.itens().stream().filter(i -> i.status().equals(s.name())).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ RF04

    @Test
    void rankingUsaSomentePedidosFinalizadosEPrecoRegistrado() {
        ProdutosMaisVendidosResponse r = DashboardCalculadora.produtosMaisVendidos(atuais(), PERIODO, 10);

        assertEquals(3, r.totalProdutos());
        assertEquals(3, r.itens().size());

        // Calabresa e Refrigerante empatam em 3 un.; desempata pelo valor líquido (150,00 > 43,50)
        ProdutoRankingDTO primeiro = r.itens().get(0);
        assertEquals(1, primeiro.posicao());
        assertEquals("pz1", primeiro.produtoId());
        assertEquals("Pizza Calabresa", primeiro.nome());
        assertEquals(3, primeiro.quantidadeVendida()); // o cancelado (5 un.) e o de fora do período não contam
        assertEquals(v("150.00"), primeiro.valorBruto());
        assertEquals(v("150.00"), primeiro.valorLiquido());

        ProdutoRankingDTO segundo = r.itens().get(1);
        assertEquals("Refrigerante 2L", segundo.nome());
        assertEquals(3, segundo.quantidadeVendida());
        assertEquals(v("45.00"), segundo.valorBruto());
        assertEquals(v("43.50"), segundo.valorLiquido()); // 13,50 + 30,00

        ProdutoRankingDTO terceiro = r.itens().get(2);
        assertEquals("Pizza Margherita", terceiro.nome());
        assertEquals(1, terceiro.quantidadeVendida());
        assertEquals(v("45.00"), terceiro.valorBruto());
        assertEquals(v("40.50"), terceiro.valorLiquido());

        BigDecimal soma = r.itens().stream().map(ProdutoRankingDTO::valorLiquido).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        assertEquals(v("234.00"), soma);
        assertEquals(v("64.10"), primeiro.participacaoPercentual());
    }

    @Test
    void rankingRespeitaOLimiteEInformaTotalDeProdutos() {
        ProdutosMaisVendidosResponse r = DashboardCalculadora.produtosMaisVendidos(atuais(), PERIODO, 1);
        assertEquals(1, r.itens().size());
        assertEquals(3, r.totalProdutos());
        assertEquals(1, r.limite());
    }

    @Test
    void rankingPreservaPrecoHistoricoDoItem() {
        List<Pedido> lista = List.of(
                pedido("h1", StatusPedido.FINALIZADO, dia(11, 1, 12), item("pz1", "Pizza Calabresa", 1, "40.00")),
                pedido("h2", StatusPedido.FINALIZADO, dia(11, 9, 12), item("pz1", "Pizza Calabresa G", 1, "55.00")));
        ProdutosMaisVendidosResponse r = DashboardCalculadora.produtosMaisVendidos(lista, PERIODO, 10);

        assertEquals(1, r.itens().size());
        assertEquals(v("95.00"), r.itens().get(0).valorBruto());
        assertEquals("Pizza Calabresa G", r.itens().get(0).nome()); // nome do registro mais recente
    }

    @Test
    void rankingSemVendasRetornaListaVazia() {
        List<Pedido> lista = List.of(
                pedido("c1", StatusPedido.CANCELADO, dia(11, 2, 12), item("pz1", "Pizza", 3, "50.00")));
        ProdutosMaisVendidosResponse r = DashboardCalculadora.produtosMaisVendidos(lista, PERIODO, 10);
        assertEquals(0, r.totalProdutos());
        assertTrue(r.itens().isEmpty());
    }

    @Test
    void rankingAgrupaItensSemIdPeloNomeEIgnoraItensInvalidos() {
        ItemPedido semId = item(null, "Borda recheada", 2, "8.00");
        ItemPedido semSubtotal = item(null, "Borda recheada", 1, "8.00");
        semSubtotal.setSubtotal(null);
        ItemPedido zerado = item("x", "Item zerado", 0, "1.00");
        Pedido p = pedido("s1", StatusPedido.FINALIZADO, dia(11, 2, 12), semId, zerado);
        p.getItens().add(semSubtotal);
        p.getItens().add(null);

        ProdutosMaisVendidosResponse r = DashboardCalculadora.produtosMaisVendidos(List.of(p), PERIODO, 10);

        assertEquals(1, r.itens().size());
        assertEquals("Borda recheada", r.itens().get(0).nome());
        assertEquals(3, r.itens().get(0).quantidadeVendida());
        assertEquals(v("24.00"), r.itens().get(0).valorBruto());
    }
}
