package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.support.ContextoTeste;
import com.umc.pediupatrao.support.Repositorios;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Administração de usuários e perfis: atribuição segura e proteção contra autoelevação (RF01).
 */
class UsuarioServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));

    private Map<String, Usuario> usuarios;
    private List<AuditoriaEvento> eventos;
    private PasswordEncoder encoder;
    private UsuarioService service;

    @BeforeEach
    void preparar() {
        usuarios = new HashMap<>();
        eventos = new ArrayList<>();
        UsuarioRepository repo = Repositorios.usuarios(usuarios);
        encoder = new BCryptPasswordEncoder(4);
        UsuarioContexto contexto = new UsuarioContexto();
        AuditoriaService auditoria = new AuditoriaService(Repositorios.auditoria(eventos),
                mock(MongoTemplate.class), contexto, RELOGIO);
        service = new UsuarioService(repo, encoder, auditoria, contexto, new SessionRegistryImpl());

        guardar("admin", Perfil.ADMIN);
        guardar("admin2", Perfil.ADMIN);
        guardar("atendente01", Perfil.ATENDENTE);
        ContextoTeste.autenticar("admin", Perfil.ADMIN);
    }

    @AfterEach
    void limpar() {
        ContextoTeste.limpar();
    }

    @Test
    void criaUsuarioComSenhaCriptografadaEPerfilValido() {
        Usuario criado = service.criar(req("gerente01", "senhaForte1", "GERENTE"));

        assertEquals("GERENTE", criado.getRole());
        assertNotEquals("senhaForte1", criado.getPassword());
        assertTrue(encoder.matches("senhaForte1", criado.getPassword()));
    }

    @Test
    void rejeitaPerfisForaDosTresOficiais() {
        assertThrows(NegocioException.class, () -> service.criar(req("user01", "senhaForte1", "SUPERUSER")));
        assertThrows(NegocioException.class, () -> service.criar(req("user02", "senhaForte1", "USER")));
        assertThrows(NegocioException.class, () -> service.criar(req("user03", "senhaForte1", "ROLE_ADMIN")));
        assertThrows(NegocioException.class, () -> service.criar(req("user04", "senhaForte1", "")));
        assertThrows(NegocioException.class, () -> service.criar(req("user05", "senhaForte1", null)));
        assertThrows(NegocioException.class, () -> service.atualizar(id("atendente01"), req("atendente01", null, "DONO")));
        assertEquals("ATENDENTE", usuarios.get(id("atendente01")).getRole());
    }

    @Test
    void validaLoginESenha() {
        assertThrows(NegocioException.class, () -> service.criar(req("ab", "senhaForte1", "ATENDENTE")));
        assertThrows(NegocioException.class, () -> service.criar(req("com espaço", "senhaForte1", "ATENDENTE")));
        assertThrows(NegocioException.class, () -> service.criar(req("novo01", "curta", "ATENDENTE")));
        assertThrows(NegocioException.class, () -> service.criar(req("novo01", "", "ATENDENTE")));
        assertThrows(ConflitoException.class, () -> service.criar(req("admin", "senhaForte1", "ATENDENTE")));
    }

    @Test
    void administradorNaoAlteraOProprioPerfilNemLogin() {
        String idAdmin = id("admin");

        assertThrows(NegocioException.class, () -> service.atualizar(idAdmin, req("admin", null, "ATENDENTE")));
        assertThrows(NegocioException.class, () -> service.atualizar(idAdmin, req("renomeado", null, "ADMIN")));
        assertEquals("ADMIN", usuarios.get(idAdmin).getRole());
        assertEquals("admin", usuarios.get(idAdmin).getUsername());
    }

    @Test
    void usuarioNaoPodeExcluirASiMesmo() {
        assertThrows(NegocioException.class, () -> service.excluir(id("admin")));
        assertTrue(usuarios.containsKey(id("admin")));
    }

    @Test
    void protegeOUltimoAdministrador() {
        usuarios.remove(id("admin2"));
        ContextoTeste.autenticar("atendente01", Perfil.ADMIN); // outro ADMIN agindo sobre "admin" (único ADMIN restante)

        assertThrows(ConflitoException.class, () -> service.excluir(id("admin")));
        assertThrows(ConflitoException.class, () -> service.atualizar(id("admin"), req("admin", null, "GERENTE")));
        assertEquals("ADMIN", usuarios.get(id("admin")).getRole());
    }

    @Test
    void adminAtribuiPerfilEAuditaSemRegistrarSenha() {
        Usuario atualizado = service.atualizar(id("atendente01"), req("atendente01", "novaSenha123", "GERENTE"));

        assertEquals("GERENTE", atualizado.getRole());
        assertTrue(encoder.matches("novaSenha123", atualizado.getPassword()));

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.ALTERACAO_USUARIO, e.getOperacao());
        assertEquals("admin", e.getUsuario());
        assertEquals("ADMIN", e.getPerfil());
        CampoAlterado perfil = e.getAlteracoes().stream().filter(c -> c.getCampo().equals("perfil")).findFirst().orElseThrow();
        assertEquals("ATENDENTE", perfil.getValorAnterior());
        assertEquals("GERENTE", perfil.getValorNovo());
        String tudo = e.getAlteracoes().stream()
                .map(c -> c.getCampo() + c.getValorAnterior() + c.getValorNovo()).reduce("", String::concat);
        assertFalse(tudo.contains("novaSenha123"), "A senha nunca pode aparecer na auditoria");
        assertFalse(tudo.contains(atualizado.getPassword()), "O hash da senha nunca pode aparecer na auditoria");
    }

    @Test
    void senhaEmBrancoNaEdicaoMantemASenhaAtual() {
        String hashAntes = usuarios.get(id("atendente01")).getPassword();

        service.atualizar(id("atendente01"), req("atendente01", "", "GERENTE"));

        assertEquals(hashAntes, usuarios.get(id("atendente01")).getPassword());
    }

    @Test
    void auditaCriacaoEExclusaoDeUsuarios() {
        Usuario criado = service.criar(req("gerente01", "senhaForte1", "GERENTE"));
        service.excluir(criado.getId());

        assertEquals(TipoOperacao.CRIACAO_USUARIO, eventos.get(0).getOperacao());
        assertEquals(TipoOperacao.EXCLUSAO_USUARIO, eventos.get(1).getOperacao());
        assertFalse(usuarios.containsKey(criado.getId()));
    }

    // ----------------------------------------------------------------

    private void guardar(String username, Perfil perfil) {
        Usuario u = new Usuario();
        u.setId("id-" + username);
        u.setUsername(username);
        u.setPassword(encoder.encode("senhaInicial1"));
        u.setRole(perfil.name());
        usuarios.put(u.getId(), u);
    }

    private static String id(String username) {
        return "id-" + username;
    }

    private static UsuarioRequest req(String username, String senha, String role) {
        UsuarioRequest r = new UsuarioRequest();
        r.setUsername(username);
        r.setPassword(senha);
        r.setRole(role);
        return r;
    }
}
