package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.support.ContextoTeste;
import com.umc.pediupatrao.support.Repositorios;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Auditoria das alterações cadastrais de clientes (RF05), com foco em telefone e endereço.
 */
class ClienteServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-23T17:35:00Z"), ZoneId.of("America/Sao_Paulo"));

    private Map<String, Cliente> clientes;
    private Map<String, Pedido> pedidos;
    private List<AuditoriaEvento> eventos;
    private AuditoriaRepository auditoriaRepository;
    private ClienteService service;

    @BeforeEach
    void preparar() {
        clientes = new HashMap<>();
        pedidos = new HashMap<>();
        eventos = new ArrayList<>();
        ClienteRepository clienteRepository = Repositorios.clientes(clientes);
        PedidoRepository pedidoRepository = Repositorios.pedidos(pedidos);
        auditoriaRepository = Repositorios.auditoria(eventos);
        UsuarioContexto contexto = new UsuarioContexto();
        AuditoriaService auditoria = new AuditoriaService(auditoriaRepository, mock(MongoTemplate.class), contexto, RELOGIO);
        service = new ClienteService(clienteRepository, pedidoRepository, auditoria);
        ContextoTeste.autenticar("atendente01", Perfil.ATENDENTE);
    }

    @AfterEach
    void limpar() {
        ContextoTeste.limpar();
    }

    @Test
    void auditaInclusaoDeCliente() {
        Cliente criado = service.criar(cliente("Maria", "(11) 99999-0000", "Rua das Flores"));

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.CRIACAO_CLIENTE, e.getOperacao());
        assertEquals("CLIENTE", e.getEntidadeTipo());
        assertEquals(criado.getId(), e.getEntidadeId());
        assertEquals("atendente01", e.getUsuario());
        assertEquals("ATENDENTE", e.getPerfil());
        assertEquals(LocalDateTime.now(RELOGIO), e.getDataHora());
        assertEquals("Rua das Flores", campo(e, "logradouro").getValorNovo());
        assertNull(campo(e, "logradouro").getValorAnterior());
    }

    @Test
    void auditaAlteracaoDeEnderecoETelefoneComValoresAnterioresENovos() {
        Cliente criado = service.criar(cliente("Maria", "(11) 99999-0000", "Rua das Flores"));
        eventos.clear();

        Cliente dados = cliente("Maria", "(11) 98888-1111", "Av. Brasil");
        service.atualizar(criado.getId(), dados);

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, e.getOperacao());
        assertEquals(criado.getId(), e.getEntidadeId());
        assertEquals(2, e.getAlteracoes().size(), "Somente os campos realmente alterados são registrados");
        assertEquals("Rua das Flores", campo(e, "logradouro").getValorAnterior());
        assertEquals("Av. Brasil", campo(e, "logradouro").getValorNovo());
        assertEquals("(11) 99999-0000", campo(e, "telefone").getValorAnterior());
        assertEquals("(11) 98888-1111", campo(e, "telefone").getValorNovo());
        assertEquals("Av. Brasil", clientes.get(criado.getId()).getLogradouro());
    }

    @Test
    void naoGeraAuditoriaQuandoNadaMudou() {
        Cliente criado = service.criar(cliente("Maria", "(11) 99999-0000", "Rua das Flores"));
        eventos.clear();

        service.atualizar(criado.getId(), cliente("Maria", "(11) 99999-0000", "Rua das Flores"));

        assertTrue(eventos.isEmpty());
    }

    @Test
    void ignoraIdentificadorEnviadoNaCriacao() {
        Cliente existente = service.criar(cliente("Maria", "(11) 99999-0000", "Rua das Flores"));
        Cliente malicioso = cliente("Invasor", "(11) 97777-0000", "Rua X");
        malicioso.setId(existente.getId());

        Cliente novo = service.criar(malicioso);

        assertFalse(existente.getId().equals(novo.getId()), "Criação nunca pode sobrescrever um cliente existente");
        assertEquals("Maria", clientes.get(existente.getId()).getNome());
    }

    @Test
    void validaDadosDoCliente() {
        assertThrows(NegocioException.class, () -> service.criar(cliente("", "(11) 99999-0000", "Rua")));
        assertThrows(NegocioException.class, () -> service.criar(cliente("Maria", "123", "Rua")));
        assertThrows(NegocioException.class, () -> service.criar(cliente("Maria", "", "Rua")));
        Cliente cepRuim = cliente("Maria", "(11) 99999-0000", "Rua");
        cepRuim.setCep("123");
        assertThrows(NegocioException.class, () -> service.criar(cepRuim));
        Cliente ufRuim = cliente("Maria", "(11) 99999-0000", "Rua");
        ufRuim.setEstado("SAO PAULO");
        assertThrows(NegocioException.class, () -> service.criar(ufRuim));
        assertTrue(clientes.isEmpty());
        assertTrue(eventos.isEmpty());
    }

    @Test
    void auditaExclusaoERegistraDadosAnteriores() {
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        Cliente criado = clienteDireto();
        eventos.clear();

        service.excluir(criado.getId());

        assertTrue(clientes.isEmpty());
        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.EXCLUSAO_CLIENTE, e.getOperacao());
        assertEquals("gerente01", e.getUsuario());
        assertEquals("GERENTE", e.getPerfil());
        assertEquals("Maria", campo(e, "nome").getValorAnterior());
        assertNull(campo(e, "nome").getValorNovo());
    }

    @Test
    void naoExcluiClienteComPedidosParaPreservarHistorico() {
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        Cliente criado = clienteDireto();
        Pedido p = new Pedido();
        p.setClienteId(criado.getId());
        p.setStatus("RECEBIDO");
        pedidos.put("ped1", p);

        assertThrows(ConflitoException.class, () -> service.excluir(criado.getId()));
        assertEquals(1, clientes.size());
    }

    @Test
    void reverteAlteracaoQuandoAuditoriaFalha() {
        Cliente criado = service.criar(cliente("Maria", "(11) 99999-0000", "Rua das Flores"));
        doThrow(new RuntimeException("mongo fora do ar")).when(auditoriaRepository).save(any(AuditoriaEvento.class));

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.atualizar(criado.getId(), cliente("Maria", "(11) 99999-0000", "Av. Brasil")));

        assertTrue(e.isOperacaoDesfeita());
        assertEquals("Rua das Flores", clientes.get(criado.getId()).getLogradouro());
    }

    // ----------------------------------------------------------------

    private Cliente clienteDireto() {
        // grava direto no "banco" (sem passar pelo serviço) para testar a exclusão isoladamente
        Cliente c = cliente("Maria", "(11) 99999-0000", "Rua das Flores");
        c.setId("cliX");
        clientes.put("cliX", Repositorios.copia(c));
        return c;
    }

    private static Cliente cliente(String nome, String telefone, String logradouro) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setTelefone(telefone);
        c.setLogradouro(logradouro);
        c.setCidade("São Paulo");
        c.setEstado("SP");
        return c;
    }

    private static CampoAlterado campo(AuditoriaEvento evento, String nome) {
        return evento.getAlteracoes().stream()
                .filter(c -> nome.equals(c.getCampo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Campo não auditado: " + nome));
    }
}
