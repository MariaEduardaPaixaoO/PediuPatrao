package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Autorização na camada de serviço (@PreAuthorize), exercitada com os serviços reais atrás do proxy de
 * segurança. Mesmo que um controlador ou uma tela falhe em proteger uma operação, o serviço recusa.
 * Quando a autorização é concedida, o fluxo segue até a regra de negócio (aqui, "não encontrado").
 */
@SpringBootTest(classes = {PedidoService.class, AuditoriaService.class, ClienteService.class, UsuarioService.class,
    UsuarioContexto.class, AutorizacaoServicosTest.Config.class})
@SuppressWarnings("deprecation")
class AutorizacaoServicosTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        SessionRegistry sessionRegistry() {
            return new SessionRegistryImpl();
        }
    }

    @MockBean
    private PedidoRepository pedidoRepository;
    @MockBean
    private ClienteRepository clienteRepository;
    @MockBean
    private ProdutoRepository produtoRepository;
    @MockBean
    private UsuarioRepository usuarioRepository;
    @MockBean
    private AuditoriaRepository auditoriaRepository;
    @MockBean
    private MongoTemplate mongoTemplate;

    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private AuditoriaService auditoriaService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private UsuarioService usuarioService;

    // ---- desconto e cancelamento: somente GERENTE

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAplicaDescontoNemCancela() {
        assertThrows(AccessDeniedException.class, () -> pedidoService.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(AccessDeniedException.class, () -> pedidoService.cancelar("p1", "Motivo qualquer"));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAplicaDescontoNemCancela() {
        assertThrows(AccessDeniedException.class, () -> pedidoService.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(AccessDeniedException.class, () -> pedidoService.cancelar("p1", "Motivo qualquer"));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteChegaAteARegraDeNegocio() {
        assertThrows(NaoEncontradoException.class, () -> pedidoService.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(NaoEncontradoException.class, () -> pedidoService.cancelar("p1", "Motivo qualquer"));
    }

    @Test
    void semAutenticacaoNenhumaOperacaoEhExecutada() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> pedidoService.aplicarDesconto("p1", new BigDecimal("10")));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> pedidoService.listarPedidos());
    }

    // ---- pedidos: ADMIN apenas consulta

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaPedidosMasNaoRegistraNemAvanca() {
        pedidoService.listarPedidos();
        assertThrows(AccessDeniedException.class, () -> pedidoService.criarPedido(new PedidoRequest()));
        assertThrows(AccessDeniedException.class, () -> pedidoService.atualizarStatus("p1", "EM_PREPARACAO"));
        assertThrows(AccessDeniedException.class, () -> pedidoService.registrarSaida("p1"));
    }

    // ---- auditoria: somente ADMIN e GERENTE

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() {
        assertThrows(AccessDeniedException.class,
                () -> auditoriaService.consultar(null, null, null, null, null));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaAuditoria() {
        auditoriaService.consultar(null, null, null, null, null);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaAuditoria() {
        auditoriaService.consultar(null, null, null, null, null);
    }

    // ---- clientes

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAcessaClientes() {
        assertThrows(AccessDeniedException.class, () -> clienteService.listarClientes());
        assertThrows(AccessDeniedException.class, () -> clienteService.criar(new Cliente()));
        assertThrows(AccessDeniedException.class, () -> clienteService.excluir("c1"));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaMasNaoAlteraClientes() {
        clienteService.listarClientes();
        assertThrows(AccessDeniedException.class, () -> clienteService.criar(new Cliente()));
        assertThrows(AccessDeniedException.class, () -> clienteService.atualizar("c1", new Cliente()));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoExcluiClientes() {
        assertThrows(AccessDeniedException.class, () -> clienteService.excluir("c1"));
    }

    // ---- usuários e perfis: somente ADMIN

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() {
        assertThrows(AccessDeniedException.class, () -> usuarioService.listarTodos());
        assertThrows(AccessDeniedException.class, () -> usuarioService.criar(new UsuarioRequest()));
        assertThrows(AccessDeniedException.class, () -> usuarioService.atualizar("u1", new UsuarioRequest()));
        assertThrows(AccessDeniedException.class, () -> usuarioService.excluir("u1"));
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoSeAutoPromove() {
        UsuarioRequest elevar = new UsuarioRequest();
        elevar.setUsername("atendente01");
        elevar.setRole("ADMIN");
        assertThrows(AccessDeniedException.class, () -> usuarioService.atualizar("u1", elevar));
    }
}
