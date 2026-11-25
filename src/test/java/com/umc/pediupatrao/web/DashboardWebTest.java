package com.umc.pediupatrao.web;

import static com.umc.pediupatrao.support.PedidosDashboard.item;
import static com.umc.pediupatrao.support.PedidosDashboard.pedido;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.DashboardController;
import com.umc.pediupatrao.controller.DashboardExceptionHandler;
import com.umc.pediupatrao.controller.DashboardViewController;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioContexto;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Controller, segurança e DashboardService reais; só o repositório é simulado. Hoje = 24/11/2026. */
@WebMvcTest(controllers = {DashboardController.class, DashboardViewController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioContexto.class, DashboardService.class,
    DashboardExceptionHandler.class, DashboardWebTest.Relogio.class})
@SuppressWarnings("deprecation")
class DashboardWebTest {

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-24T23:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    private static final String[] ENDPOINTS = {
        "/api/dashboard/resumo", "/api/dashboard/vendas", "/api/dashboard/pedidos/status",
        "/api/dashboard/produtos-mais-vendidos"};

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PedidoRepository pedidoRepository;
    @MockBean
    private UsuarioRepository usuarioRepository;

    private static RequestPostProcessor admin() {
        return user("admin").roles("ADMIN");
    }

    private static RequestPostProcessor gerente() {
        return user("gerente01").roles("GERENTE");
    }

    private static RequestPostProcessor atendente() {
        return user("atendente01").roles("ATENDENTE");
    }

    // 2 finalizados, 1 cancelado e 1 em preparação
    private void pedidosDeHoje() {
        when(pedidoRepository.buscarParaDashboard(eq(LocalDateTime.of(2026, 11, 24, 0, 0)),
                eq(LocalDateTime.of(2026, 11, 25, 0, 0)))).thenReturn(List.of(
                pedido("p1", StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 24, 19, 0),
                        item("pz1", "Pizza Calabresa", 2, "50.00")),
                pedido("p2", StatusPedido.FINALIZADO, LocalDateTime.of(2026, 11, 24, 20, 0), "10",
                        item("pz2", "Pizza Margherita", 1, "45.00"), item("bb1", "Refrigerante 2L", 1, "15.00")),
                pedido("p3", StatusPedido.CANCELADO, LocalDateTime.of(2026, 11, 24, 20, 30),
                        item("pz1", "Pizza Calabresa", 3, "50.00")),
                pedido("p4", StatusPedido.EM_PREPARACAO, LocalDateTime.of(2026, 11, 24, 21, 0),
                        item("es1", "Esfiha", 4, "6.50"))));
    }

    // ------------------------------------------------------------------ controle de acesso (RNF01)

    @Test
    void semLoginApiResponde401EPaginaVaiParaLogin() throws Exception {
        for (String url : ENDPOINTS) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void atendenteRecebe403NaApiENaPagina() throws Exception {
        for (String url : ENDPOINTS) {
            mvc.perform(get(url).with(atendente())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/dashboard").with(atendente())).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void adminEGerenteConsultamTodosOsEndpoints() throws Exception {
        for (String url : ENDPOINTS) {
            mvc.perform(get(url).with(admin())).andExpect(status().isOk());
            mvc.perform(get(url).with(gerente())).andExpect(status().isOk());
        }
    }

    @Test
    void apiDoDashboardEhSomenteLeitura() throws Exception {
        mvc.perform(post("/api/dashboard/resumo").with(gerente()).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/dashboard/resumo").with(gerente())).andExpect(status().isForbidden());
    }

    @Test
    void paginaDoDashboardParaGerente() throws Exception {
        mvc.perform(get("/dashboard").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard Administrativo")))
                .andExpect(content().string(containsString("data-api=\"/api/dashboard\"")))
                .andExpect(content().string(containsString("data-hoje=\"2026-11-24\"")))
                .andExpect(content().string(containsString("/plugins/chart.js/Chart.min.js")))
                .andExpect(content().string(containsString("/js/dashboard.js")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")));
        verifyNoInteractions(pedidoRepository);
    }

    // ------------------------------------------------------------------ RF01 a RF04 com dados

    @Test
    void resumoComDadosDoBanco() throws Exception {
        pedidosDeHoje();
        mvc.perform(get("/api/dashboard/resumo").param("periodo", "HOJE").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.tipo").value("HOJE"))
                .andExpect(jsonPath("$.periodo.inicio").value("2026-11-24"))
                .andExpect(jsonPath("$.periodo.fim").value("2026-11-24"))
                .andExpect(jsonPath("$.totalVendas").value(154.0))
                .andExpect(jsonPath("$.quantidadeVendas").value(2))
                .andExpect(jsonPath("$.quantidadePedidos").value(4))
                .andExpect(jsonPath("$.ticketMedio").value(77.0))
                .andExpect(jsonPath("$.quantidadeCancelados").value(1))
                .andExpect(jsonPath("$.valorCancelado").value(150.0))
                .andExpect(jsonPath("$.taxaCancelamentoPercentual").value(25.0))
                .andExpect(jsonPath("$.pedidosEmAndamento").value(1))
                .andExpect(jsonPath("$.comparacao.periodoAnterior.inicio").value("2026-11-23"))
                .andExpect(jsonPath("$.comparacao.dadosSuficientes").value(false))
                .andExpect(jsonPath("$.comparacao.variacaoVendasPercentual").value(nullValue()));
    }

    @Test
    void vendasRetornaUmPontoPorDia() throws Exception {
        mvc.perform(get("/api/dashboard/vendas").param("periodo", "ULTIMOS_7_DIAS").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agrupamento").value("DIA"))
                .andExpect(jsonPath("$.pontos.length()").value(7))
                .andExpect(jsonPath("$.pontosAnterior.length()").value(7))
                .andExpect(jsonPath("$.pontos[0].rotulo").value("18/11"))
                .andExpect(jsonPath("$.pontos[6].rotulo").value("24/11"));
    }

    @Test
    void statusTrazOsSeteStatusDoSistema() throws Exception {
        pedidosDeHoje();
        mvc.perform(get("/api/dashboard/pedidos/status").param("periodo", "HOJE").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.itens.length()").value(7))
                .andExpect(jsonPath("$.itens[0].status").value("RECEBIDO"))
                .andExpect(jsonPath("$.itens[1].status").value("EM_PREPARACAO"))
                .andExpect(jsonPath("$.itens[1].quantidade").value(1))
                .andExpect(jsonPath("$.itens[5].status").value("FINALIZADO"))
                .andExpect(jsonPath("$.itens[5].quantidade").value(2))
                .andExpect(jsonPath("$.itens[6].status").value("CANCELADO"))
                .andExpect(jsonPath("$.itens[6].descricao").value("Cancelado"));
    }

    @Test
    void produtosMaisVendidosComPrecoRegistrado() throws Exception {
        pedidosDeHoje();
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("periodo", "HOJE").param("limite", "2")
                        .with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(2))
                .andExpect(jsonPath("$.totalProdutos").value(3))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$.itens[0].quantidadeVendida").value(2)) // o cancelado não conta
                .andExpect(jsonPath("$.itens[0].valorLiquido").value(100.0))
                .andExpect(jsonPath("$.itens[1].nome").value("Pizza Margherita"))
                .andExpect(jsonPath("$.itens[1].valorLiquido").value(40.5));
    }

    @Test
    void ausenciaDeDadosRetornaEstruturaVazia() throws Exception {
        when(pedidoRepository.buscarParaDashboard(any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/dashboard/resumo").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.tipo").value("ULTIMOS_30_DIAS"))
                .andExpect(jsonPath("$.totalVendas").value(0.0))
                .andExpect(jsonPath("$.quantidadePedidos").value(0))
                .andExpect(jsonPath("$.ticketMedio").value(nullValue()));
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
        mvc.perform(get("/api/dashboard/pedidos/status").with(gerente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.itens.length()").value(7));
    }

    @Test
    void periodoPersonalizado() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-01").param("fim", "2026-11-10")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.tipo").value("PERSONALIZADO"))
                .andExpect(jsonPath("$.periodo.dias").value(10))
                .andExpect(jsonPath("$.comparacao.periodoAnterior.inicio").value("2026-10-22"))
                .andExpect(jsonPath("$.comparacao.periodoAnterior.fim").value("2026-10-31"));
    }

    // ------------------------------------------------------------------ datas inválidas e erros (RF05, RNF06)

    @Test
    void datasInvalidasRetornam400ComMensagem() throws Exception {
        erro400("/api/dashboard/resumo?inicio=2026-13-01&fim=2026-11-10",
                "Data inicial inválida. Use o formato AAAA-MM-DD.");
        erro400("/api/dashboard/vendas?inicio=2026-11-10&fim=2026-11-01",
                "A data final não pode ser anterior à data inicial.");
        erro400("/api/dashboard/pedidos/status?inicio=2026-11-20&fim=2026-11-30",
                "A data final não pode estar no futuro.");
        erro400("/api/dashboard/resumo?inicio=2026-11-01",
                "Informe a data inicial e a data final.");
        erro400("/api/dashboard/resumo?inicio=2025-01-01&fim=2026-11-24",
                "O intervalo máximo de consulta é de 366 dias.");
        erro400("/api/dashboard/resumo?periodo=HOJE&inicio=2026-11-01&fim=2026-11-02",
                "Informe um período predefinido ou as datas inicial e final, não os dois.");
        mvc.perform(get("/api/dashboard/resumo?periodo=SEMPRE").with(gerente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(containsString("Período inválido.")));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    void limiteInvalidoRetorna400() throws Exception {
        erro400("/api/dashboard/produtos-mais-vendidos?limite=abc", "O limite do ranking deve ser um número inteiro.");
        erro400("/api/dashboard/produtos-mais-vendidos?limite=0", "O limite do ranking deve estar entre 1 e 50.");
        erro400("/api/dashboard/produtos-mais-vendidos?limite=51", "O limite do ranking deve estar entre 1 e 50.");
    }

    @Test
    void falhaNoBancoRetorna503SemDetalhesTecnicos() throws Exception {
        when(pedidoRepository.buscarParaDashboard(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("Timeout em mongodb://usuario:senha@host"));
        mvc.perform(get("/api/dashboard/resumo").with(gerente()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensagem").value(
                        "Não foi possível consultar os dados do dashboard agora. Tente novamente em instantes."))
                .andExpect(content().string(not(containsString("mongodb://"))));
    }

    private void erro400(String url, String mensagem) throws Exception {
        mvc.perform(get(url).with(gerente()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensagem").value(mensagem));
    }
}
