package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.support.ContextoTeste;
import com.umc.pediupatrao.support.Repositorios;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class AuditoriaServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));

    private List<AuditoriaEvento> eventos;
    private AuditoriaRepository repositorio;
    private MongoTemplate mongoTemplate;
    private AuditoriaService service;

    @BeforeEach
    void preparar() {
        eventos = new ArrayList<>();
        repositorio = Repositorios.auditoria(eventos);
        mongoTemplate = mock(MongoTemplate.class);
        service = new AuditoriaService(repositorio, mongoTemplate, new UsuarioContexto(), RELOGIO);
        ContextoTeste.autenticar("atendente01", Perfil.ATENDENTE);
    }

    @AfterEach
    void limpar() {
        ContextoTeste.limpar();
    }

    @Test
    void persisteEventoComUsuarioPerfilDataEValores() {
        AuditoriaEvento salvo = service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "abc123",
                List.of(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil")), null);

        assertEquals(1, eventos.size());
        assertEquals("aud1", salvo.getId());
        assertEquals("atendente01", salvo.getUsuario());
        assertEquals("ATENDENTE", salvo.getPerfil());
        assertEquals(LocalDateTime.of(2026, 9, 23, 14, 35, 0), salvo.getDataHora());
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, salvo.getOperacao());
        assertEquals("CLIENTE", salvo.getEntidadeTipo());
        assertEquals("abc123", salvo.getEntidadeId());
        assertEquals("Rua das Flores", salvo.getAlteracoes().get(0).getValorAnterior());
        assertEquals("Av. Brasil", salvo.getAlteracoes().get(0).getValorNovo());
    }

    @Test
    void usuarioEPerfilVemDoContextoDeSegurancaNuncaDaRequisicao() {
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        AuditoriaEvento salvo = service.registrar(TipoOperacao.DESCONTO_PEDIDO, "PEDIDO", "p1",
                List.of(new CampoAlterado("descontoPercentual", "0.00", "10.00")), null);

        assertEquals("gerente01", salvo.getUsuario());
        assertEquals("GERENTE", salvo.getPerfil());
    }

    @Test
    void naoRegistraSemUsuarioAutenticado() {
        ContextoTeste.limpar();

        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> service.registrar(TipoOperacao.CRIACAO_PEDIDO, "PEDIDO", "p1", List.of(), null));
        assertTrue(eventos.isEmpty());
    }

    @Test
    void descartaCamposSensiveisComoSenhaETokens() {
        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("password", "velha", "nova"));
        campos.add(new CampoAlterado("senha", "velha", "nova"));
        campos.add(new CampoAlterado("tokenAcesso", "a", "b"));
        campos.add(new CampoAlterado("credencialApi", "a", "b"));
        campos.add(new CampoAlterado("perfil", "ATENDENTE", "GERENTE"));

        AuditoriaEvento salvo = service.registrar(TipoOperacao.ALTERACAO_USUARIO, "USUARIO", "u1", campos, null);

        assertEquals(1, salvo.getAlteracoes().size());
        assertEquals("perfil", salvo.getAlteracoes().get(0).getCampo());
    }

    @Test
    void ignoraCamposSemMudancaERegistraJustificativa() {
        List<CampoAlterado> campos = List.of(
                new CampoAlterado("status", "PRONTO", "CANCELADO"),
                new CampoAlterado("clienteNome", "Maria", "Maria"));

        AuditoriaEvento salvo = service.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, "PEDIDO", "p1", campos,
                "  Cliente desistiu  ");

        assertEquals(1, salvo.getAlteracoes().size());
        assertEquals("Cliente desistiu", salvo.getJustificativa());
    }

    @Test
    void falhaDePersistenciaGeraExcecaoENaoEhSilenciada() {
        doThrow(new RuntimeException("sem conexão")).when(repositorio).save(any(AuditoriaEvento.class));

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.registrar(TipoOperacao.CRIACAO_PEDIDO, "PEDIDO", "p1", List.of(), null));

        assertFalse(e.isOperacaoDesfeita());
        assertTrue(eventos.isEmpty());
    }

    @Test
    void compensacaoEhExecutadaQuandoAuditoriaFalha() {
        doThrow(new RuntimeException("sem conexão")).when(repositorio).save(any(AuditoriaEvento.class));
        AtomicBoolean desfeito = new AtomicBoolean(false);

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.registrarComCompensacao(() -> desfeito.set(true),
                        TipoOperacao.CRIACAO_PEDIDO, "PEDIDO", "p1", List.of(), null));

        assertTrue(desfeito.get());
        assertTrue(e.isOperacaoDesfeita());
    }

    @Test
    void informaQuandoNaoFoiPossivelDesfazerAOperacao() {
        doThrow(new RuntimeException("sem conexão")).when(repositorio).save(any(AuditoriaEvento.class));

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.registrarComCompensacao(() -> {
                    throw new RuntimeException("falha ao desfazer");
                }, TipoOperacao.CRIACAO_PEDIDO, "PEDIDO", "p1", List.of(), null));

        assertFalse(e.isOperacaoDesfeita());
        assertTrue(e.getMessage().contains("sem o registro de auditoria"));
    }

    @Test
    void consultaOrdenaDoMaisRecenteELimitaOTamanho() {
        AuditoriaEvento a = new AuditoriaEvento();
        a.setId("a1");
        when(mongoTemplate.find(any(Query.class), eq(AuditoriaEvento.class))).thenReturn(List.of(a));

        List<AuditoriaEvento> resultado = service.consultar("pedido", "p1", "gerente01",
                TipoOperacao.CANCELAMENTO_PEDIDO, 100000);

        assertEquals(1, resultado.size());
        org.mockito.ArgumentCaptor<Query> captor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(AuditoriaEvento.class));
        assertEquals(AuditoriaService.LIMITE_MAXIMO, captor.getValue().getLimit());
        assertTrue(captor.getValue().getSortObject().containsKey("dataHora"));
        assertEquals("p1", captor.getValue().getQueryObject().get("entidadeId"));
        assertEquals("PEDIDO", captor.getValue().getQueryObject().get("entidadeTipo"));
    }

    @Test
    void naoExistemMetodosDeAtualizacaoOuExclusaoNoRepositorioDeAuditoria() {
        for (java.lang.reflect.Method m : AuditoriaRepository.class.getMethods()) {
            String nome = m.getName().toLowerCase();
            assertFalse(nome.contains("delete") || nome.contains("remove") || nome.contains("update"),
                    "O repositório de auditoria não pode expor operações destrutivas: " + m.getName());
        }
    }
}
