package com.umc.pediupatrao.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.controller.AuditoriaController;
import com.umc.pediupatrao.controller.AuditoriaViewController;
import com.umc.pediupatrao.controller.ClienteController;
import com.umc.pediupatrao.controller.HomeController;
import com.umc.pediupatrao.controller.PedidoController;
import com.umc.pediupatrao.controller.PedidoViewController;
import com.umc.pediupatrao.controller.ProdutoController;
import com.umc.pediupatrao.controller.UsuarioController;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioContexto;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Autorização no backend: as rotas e APIs são testadas diretamente (sem depender de botões da tela).
 */
@WebMvcTest(controllers = {PedidoController.class, AuditoriaController.class, UsuarioController.class,
    ClienteController.class, ProdutoController.class, HomeController.class, PedidoViewController.class,
    AuditoriaViewController.class})
@Import({SecurityConfig.class, UsuarioDetailsService.class, UsuarioContexto.class})
@SuppressWarnings("deprecation")
class SegurancaWebTest {

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

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    // ---------------------------------------------------------------- autenticação

    @Test
    void autenticaOsTresPerfisPeloFormularioDeLogin() throws Exception {
        for (String[] u : new String[][] {{"admin", "ADMIN"}, {"gerente01", "GERENTE"}, {"atendente01", "ATENDENTE"}}) {
            Usuario usuario = new Usuario();
            usuario.setUsername(u[0]);
            usuario.setPassword(new BCryptPasswordEncoder(4).encode("senhaForte1"));
            usuario.setRole(u[1]);
            when(usuarioRepository.findByUsername(u[0])).thenReturn(Optional.of(usuario));

            mvc.perform(formLogin("/login").user(u[0]).password("senhaForte1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andExpect(authenticated().withRoles(u[1]));
        }
    }

    @Test
    void rejeitaSenhaIncorretaEUsuarioInexistente() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setUsername("gerente01");
        usuario.setPassword(new BCryptPasswordEncoder(4).encode("senhaForte1"));
        usuario.setRole("GERENTE");
        when(usuarioRepository.findByUsername("gerente01")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        mvc.perform(formLogin("/login").user("gerente01").password("errada"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        mvc.perform(formLogin("/login").user("fantasma").password("qualquer"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void usuarioSemPerfilValidoNaoAutentica() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setUsername("semperfil");
        usuario.setPassword(new BCryptPasswordEncoder(4).encode("senhaForte1"));
        usuario.setRole("SUPERUSER");
        when(usuarioRepository.findByUsername("semperfil")).thenReturn(Optional.of(usuario));

        mvc.perform(formLogin("/login").user("semperfil").password("senhaForte1"))
                .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @Test
    void anonimoRecebe401NasApisERedirecionaParaLoginNasPaginas() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
        // antes da evolução esta rota era pública (criação de usuários sem login)
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(JSON)
                .content("{\"username\":\"intruso\",\"password\":\"senhaForte1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/pedidos")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login")));
        verifyNoInteractions(usuarioService, pedidoService);
    }

    // ---------------------------------------------------------------- RF01: descontos e cancelamentos

    @Test
    void somenteGerenteAplicaDescontoPelaApi() throws Exception {
        when(pedidoService.aplicarDesconto(eq("p1"), any(BigDecimal.class))).thenReturn(new Pedido());
        String corpo = "{\"percentual\": 10}";

        mvc.perform(post("/api/pedidos/p1/desconto").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/desconto").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        verify(pedidoService, never()).aplicarDesconto(anyString(), any());

        mvc.perform(post("/api/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isOk());
        verify(pedidoService).aplicarDesconto(eq("p1"), any(BigDecimal.class));
    }

    @Test
    void somenteGerenteCancelaPelaApi() throws Exception {
        when(pedidoService.cancelar(eq("p1"), anyString())).thenReturn(new Pedido());
        String corpo = "{\"justificativa\": \"Cliente desistiu do pedido\"}";

        mvc.perform(post("/api/pedidos/p1/cancelar").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/cancelar").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        verify(pedidoService, never()).cancelar(anyString(), anyString());

        mvc.perform(post("/api/pedidos/p1/cancelar").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isOk());
        verify(pedidoService).cancelar("p1", "Cliente desistiu do pedido");
    }

    @Test
    void somenteGerenteDescontaECancelaPelosFormulariosDaTela() throws Exception {
        mvc.perform(post("/pedidos/p1/desconto").with(user("admin").roles("ADMIN")).with(csrf())
                .param("percentual", "10")).andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/cancelar").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .param("justificativa", "Motivo qualquer")).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);

        mvc.perform(post("/pedidos/p1/desconto").with(user("gerente01").roles("GERENTE")).with(csrf())
                .param("percentual", "10")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/pedidos/p1/cancelar").with(user("gerente01").roles("GERENTE")).with(csrf())
                .param("justificativa", "Motivo qualquer")).andExpect(status().is3xxRedirection());
        verify(pedidoService).aplicarDesconto(eq("p1"), any(BigDecimal.class));
        verify(pedidoService).cancelar("p1", "Motivo qualquer");
    }

    @Test
    void adminNaoCriaNemAvancaPedidosMasConsultaTodos() throws Exception {
        mvc.perform(post("/api/pedidos").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").param("status", "EM_PREPARACAO")
                .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/saida").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        verify(pedidoService, never()).criarPedido(any());
        verify(pedidoService, never()).atualizarStatus(anyString(), anyString());

        for (String perfil : new String[] {"ADMIN", "GERENTE", "ATENDENTE"}) {
            mvc.perform(get("/api/pedidos").with(user("u").roles(perfil))).andExpect(status().isOk());
        }
    }

    @Test
    void atendenteEGerenteRegistramPedidosEAvancamStatus() throws Exception {
        Pedido criado = new Pedido();
        criado.setId("p9");
        when(pedidoService.criarPedido(any(PedidoRequest.class))).thenReturn(criado);
        when(pedidoService.atualizarStatus("p1", "EM_PREPARACAO")).thenReturn(new Pedido());

        for (String perfil : new String[] {"ATENDENTE", "GERENTE"}) {
            mvc.perform(post("/api/pedidos").with(user("u").roles(perfil)).with(csrf()).contentType(JSON)
                    .content("{\"clienteId\":\"c1\",\"tipoEntrega\":\"ENTREGA\",\"itens\":[{\"produtoId\":\"pz\",\"quantidade\":2}]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("p9"));
            mvc.perform(put("/api/pedidos/p1/status").param("status", "EM_PREPARACAO")
                    .with(user("u").roles(perfil)).with(csrf())).andExpect(status().isOk());
        }
    }

    @Test
    void camposFinanceirosEResponsaveisEnviadosNaRequisicaoSaoIgnorados() throws Exception {
        Pedido criado = new Pedido();
        criado.setId("p9");
        when(pedidoService.criarPedido(any(PedidoRequest.class))).thenReturn(criado);

        // tenta forçar total, desconto, status, responsável e horário de entrada
        String malicioso = "{\"clienteId\":\"c1\",\"tipoEntrega\":\"ENTREGA\","
                + "\"valorTotal\":0.01,\"valorDesconto\":99,\"descontoPercentual\":100,\"status\":\"FINALIZADO\","
                + "\"registradoPor\":\"gerente01\",\"dataHoraEntrada\":\"2000-01-01T00:00:00\","
                + "\"itens\":[{\"produtoId\":\"pz\",\"quantidade\":1,\"precoUnitario\":0.01,\"subtotal\":0.01}]}";

        mvc.perform(post("/api/pedidos").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(malicioso)).andExpect(status().isCreated());

        ArgumentCaptor<PedidoRequest> captor = ArgumentCaptor.forClass(PedidoRequest.class);
        verify(pedidoService).criarPedido(captor.capture());
        // o DTO só carrega cliente, tipo e (produto, quantidade): não há onde receber valores arbitrários
        org.junit.jupiter.api.Assertions.assertEquals("c1", captor.getValue().getClienteId());
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().getItens().size());
        org.junit.jupiter.api.Assertions.assertEquals("pz", captor.getValue().getItens().get(0).getProdutoId());
        for (java.lang.reflect.Field campo : PedidoRequest.class.getDeclaredFields()) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    campo.getName().matches("(?i).*(total|desconto|status|registrad|dataHora|preco).*"),
                    "PedidoRequest não pode aceitar o campo " + campo.getName());
        }
    }

    // ---------------------------------------------------------------- RF05: auditoria

    @Test
    void atendenteNaoConsultaAuditoriaPorApiNemPorPagina() throws Exception {
        mvc.perform(get("/api/auditoria").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/auditoria").with(user("atendente01").roles("ATENDENTE")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void adminEGerenteConsultamAuditoria() throws Exception {
        when(auditoriaService.consultar(any(), any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/auditoria").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/auditoria").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());
    }

    @Test
    void auditoriaNaoPodeSerAlteradaNemExcluidaPelasInterfaces() throws Exception {
        for (String perfil : new String[] {"ADMIN", "GERENTE", "ATENDENTE"}) {
            mvc.perform(delete("/api/auditoria/a1").with(user("u").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/auditoria/a1").with(user("u").roles(perfil)).with(csrf())
                    .contentType(JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(post("/api/auditoria").with(user("u").roles(perfil)).with(csrf())
                    .contentType(JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(post("/auditoria/limpar").with(user("u").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------------------------------------------------------------- RF01: usuários e perfis

    @Test
    void somenteAdminAcessaAdministracaoDeUsuarios() throws Exception {
        Usuario u = new Usuario();
        u.setId("u1");
        u.setUsername("atendente01");
        u.setPassword("$2a$10$hashdeverdade");
        u.setRole("ATENDENTE");
        when(usuarioService.listarTodos()).thenReturn(List.of(u));

        for (String perfil : new String[] {"GERENTE", "ATENDENTE"}) {
            mvc.perform(get("/api/usuarios").with(user("u").roles(perfil))).andExpect(status().isForbidden());
            mvc.perform(get("/usuarios").with(user("u").roles(perfil))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(usuarioService);

        mvc.perform(get("/api/usuarios").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("atendente01"))
                .andExpect(jsonPath("$[0].role").value("ATENDENTE"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    void autoelevacaoDePrivilegiosEBloqueadaPeloBackend() throws Exception {
        String elevar = "{\"username\":\"atendente01\",\"role\":\"ADMIN\"}";

        // ATENDENTE e GERENTE tentando se promover por API ou formulário
        for (String perfil : new String[] {"ATENDENTE", "GERENTE"}) {
            mvc.perform(put("/api/usuarios/u1").with(user("atendente01").roles(perfil)).with(csrf())
                    .contentType(JSON).content(elevar)).andExpect(status().isForbidden());
            mvc.perform(post("/api/usuarios").with(user("atendente01").roles(perfil)).with(csrf())
                    .contentType(JSON).content("{\"username\":\"novo\",\"password\":\"senhaForte1\",\"role\":\"ADMIN\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/editar/u1").with(user("atendente01").roles(perfil)).with(csrf())
                    .param("username", "atendente01").param("role", "ADMIN")).andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/salvar").with(user("atendente01").roles(perfil)).with(csrf())
                    .param("username", "novo").param("password", "senhaForte1").param("role", "ADMIN"))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/usuarios/deletar/u2").with(user("atendente01").roles(perfil)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(usuarioService);
    }

    @Test
    void perfilInvalidoEmAtribuicaoRetorna400() throws Exception {
        when(usuarioService.criar(any())).thenThrow(new NegocioException("Perfil inválido. Perfis permitidos: ADMIN, GERENTE ou ATENDENTE."));

        mvc.perform(post("/api/usuarios").with(user("admin").roles("ADMIN")).with(csrf()).contentType(JSON)
                .content("{\"username\":\"novo\",\"password\":\"senhaForte1\",\"role\":\"SUPERUSER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Perfil inválido. Perfis permitidos: ADMIN, GERENTE ou ATENDENTE."));
    }

    @Test
    void errosDeRegraDeNegocioViramRespostasJsonComStatusAdequado() throws Exception {
        when(pedidoService.atualizarStatus("p1", "FINALIZADO"))
                .thenThrow(new ConflitoException("Transição inválida: Em preparação → Finalizado."));

        mvc.perform(put("/api/pedidos/p1/status").param("status", "FINALIZADO")
                .with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Transição inválida: Em preparação → Finalizado."));
    }

    // ---------------------------------------------------------------- clientes e produtos

    @Test
    void permissoesDeClientesPorPerfil() throws Exception {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        when(clienteService.listarClientes()).thenReturn(List.of(c));
        when(clienteService.criar(any())).thenReturn(c);
        when(clienteService.atualizar(eq("c1"), any())).thenReturn(c);
        String corpo = "{\"nome\":\"Maria\",\"telefone\":\"(11) 99999-0000\"}";

        // ADMIN não acessa dados de clientes
        mvc.perform(get("/api/clientes").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        // GERENTE consulta e exclui, mas não cadastra nem altera
        mvc.perform(get("/api/clientes").with(user("gerente01").roles("GERENTE"))).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/c1").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/c1").with(user("gerente01").roles("GERENTE")).with(csrf()))
                .andExpect(status().isNoContent());
        // ATENDENTE consulta, cadastra e altera, mas não exclui
        mvc.perform(get("/api/clientes").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isCreated());
        mvc.perform(put("/api/clientes/c1").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/c1").with(user("atendente01").roles("ATENDENTE")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void manutencaoDeProdutosSoParaAdminEGerente() throws Exception {
        Produto p = new Produto();
        p.setId("pz");
        when(produtoService.salvar(any())).thenReturn(p);
        String corpo = "{\"nome\":\"Pizza\",\"tipo\":\"Pizza\",\"preco\":40.0,\"ativo\":true}";

        mvc.perform(post("/api/produtos").with(user("atendente01").roles("ATENDENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isForbidden());
        mvc.perform(post("/api/produtos").with(user("gerente01").roles("GERENTE")).with(csrf())
                .contentType(JSON).content(corpo)).andExpect(status().isCreated());
        mvc.perform(get("/api/produtos").with(user("atendente01").roles("ATENDENTE"))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- CSRF

    @Test
    void csrfPermaneceHabilitadoEmApisEFormularios() throws Exception {
        // sem token CSRF, mesmo autenticado e autorizado, a requisição é recusada
        mvc.perform(post("/api/pedidos").with(user("atendente01").roles("ATENDENTE"))
                .contentType(JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/clientes/c1").with(user("gerente01").roles("GERENTE")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/cancelar").with(user("gerente01").roles("GERENTE"))
                .param("justificativa", "Motivo qualquer")).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/salvar").with(user("admin").roles("ADMIN"))
                .param("username", "novo").param("password", "senhaForte1").param("role", "GERENTE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
        verify(pedidoService, never()).cancelar(anyString(), anyString());
        verify(clienteService, never()).excluir(anyString());
    }
}
