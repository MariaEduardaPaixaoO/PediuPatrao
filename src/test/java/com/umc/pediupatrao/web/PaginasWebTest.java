package com.umc.pediupatrao.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaViewController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoViewController;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.TransicaoStatus;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioContexto;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import com.umc.pediupatrao.util.Formatador;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renderização das páginas Thymeleaf e visibilidade dos botões por perfil. (A visibilidade é só
 * conveniência de interface: a proteção real é verificada em SegurancaWebTest e nos testes de serviço.)
 */
@WebMvcTest(controllers = {HomeController.class, PedidoViewController.class, AuditoriaViewController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioContexto.class, Formatador.class})
@SuppressWarnings("deprecation")
class PaginasWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PedidoService pedidoService;
    @MockBean
    private ClienteService clienteService;
    @MockBean
    private ProdutoService produtoService;
    @MockBean
    private UsuarioService usuarioService;
    @MockBean
    private AuditoriaService auditoriaService;
    @MockBean
    private UsuarioRepository usuarioRepository;

    @Test
    void paginaDeLoginEhPublica() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Entrar")));
    }

    @Test
    void menuELateralMudaConformeOPerfil() throws Exception {
        mvc.perform(get("/").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Usuários")))
                .andExpect(content().string(containsString("Auditoria")))
                .andExpect(content().string(not(containsString(">&nbsp;Clientes"))))
                .andExpect(content().string(containsString("Sair")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Auditoria")))
                .andExpect(content().string(containsString("Clientes")))
                .andExpect(content().string(not(containsString("Usuários"))));
        mvc.perform(get("/").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Clientes")))
                .andExpect(content().string(not(containsString("Auditoria"))))
                .andExpect(content().string(not(containsString("Usuários"))));
    }

    @Test
    void layoutExpoeTokenCsrfParaChamadasFetch() throws Exception {
        mvc.perform(get("/").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"_csrf_header\"")));
    }

    @Test
    void listaDePedidosMostraBotaoNovoSomenteParaQuemRegistra() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(List.of(pedido("PRONTO")));

        mvc.perform(get("/pedidos").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Novo Pedido")))
                .andExpect(content().string(containsString("Maria Souza")))
                .andExpect(content().string(containsString("Pronto")));
        mvc.perform(get("/pedidos").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Novo Pedido"))));
    }

    @Test
    void detalheMostraAcoesConformePerfilEStatus() throws Exception {
        when(pedidoService.buscarPorId("p1")).thenReturn(pedido("PRONTO"));

        mvc.perform(get("/pedidos/p1").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cancelar pedido")))
                .andExpect(content().string(containsString("Desconto")))
                .andExpect(content().string(containsString("Registrar")))
                .andExpect(content().string(containsString("Histórico de transições")));
        mvc.perform(get("/pedidos/p1").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Cancelar pedido"))))
                .andExpect(content().string(not(containsString("name=\"percentual\""))))
                .andExpect(content().string(containsString("Registrar")));
        mvc.perform(get("/pedidos/p1").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Cancelar pedido"))))
                .andExpect(content().string(not(containsString("name=\"percentual\""))))
                .andExpect(content().string(not(containsString("Registrar"))));
    }

    @Test
    void pedidoCanceladoNaoOferecaAcoes() throws Exception {
        Pedido p = pedido("CANCELADO");
        p.setCanceladoPor("gerente01");
        p.setDataHoraCancelamento(LocalDateTime.of(2026, 10, 16, 12, 0));
        p.setJustificativaCancelamento("Cliente desistiu");
        when(pedidoService.buscarPorId("p1")).thenReturn(p);

        mvc.perform(get("/pedidos/p1").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cliente desistiu")))
                .andExpect(content().string(not(containsString("Cancelar pedido"))))
                .andExpect(content().string(not(containsString("name=\"percentual\""))));
    }

    @Test
    void formularioDeNovoPedidoRenderiza() throws Exception {
        when(clienteService.listarClientes()).thenReturn(List.of());
        when(produtoService.listarProdutos()).thenReturn(List.of());

        mvc.perform(get("/pedidos/novo").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Registrar Pedido")))
                .andExpect(content().string(containsString("O valor final é calculado pelo servidor")));
    }

    @Test
    void paginaDeAuditoriaRenderizaCamposAnterioresENovos() throws Exception {
        AuditoriaEvento e = new AuditoriaEvento();
        e.setDataHora(LocalDateTime.of(2026, 9, 23, 14, 35));
        e.setUsuario("atendente01");
        e.setPerfil("ATENDENTE");
        e.setOperacao(TipoOperacao.ALTERACAO_CLIENTE);
        e.setEntidadeTipo("CLIENTE");
        e.setEntidadeId("c1");
        e.setAlteracoes(List.of(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil")));
        when(auditoriaService.consultar(any(), any(), any(), any(), any())).thenReturn(List.of(e));

        mvc.perform(get("/auditoria").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("atendente01")))
                .andExpect(content().string(containsString("Alteração de cliente")))
                .andExpect(content().string(containsString("logradouro")))
                .andExpect(content().string(containsString("Rua das Flores")))
                .andExpect(content().string(containsString("Av. Brasil")))
                .andExpect(content().string(containsString("23/09/2026 14:35")));
    }

    private static Pedido pedido(String status) {
        Pedido p = new Pedido();
        p.setId("p1abcdef123456");
        p.setClienteId("c1");
        p.setClienteNome("Maria Souza");
        p.setTipoEntrega(TipoEntrega.ENTREGA);
        p.setStatus(status);
        p.setRegistradoPor("atendente01");
        p.setDataHoraEntrada(LocalDateTime.of(2026, 10, 16, 11, 30));
        p.setItens(new ArrayList<>(List.of(new ItemPedido("pz", "Pizza Calabresa", 2,
                new BigDecimal("40.00"), new BigDecimal("80.00")))));
        p.setSubtotal(new BigDecimal("80.00"));
        p.setDescontoPercentual(new BigDecimal("0.00"));
        p.setValorDesconto(new BigDecimal("0.00"));
        p.setValorTotal(new BigDecimal("80.00"));
        p.getHistorico().add(new TransicaoStatus(null, "RECEBIDO", "atendente01", "ATENDENTE",
                LocalDateTime.of(2026, 10, 16, 11, 30), "Pedido recebido"));
        return p;
    }
}
