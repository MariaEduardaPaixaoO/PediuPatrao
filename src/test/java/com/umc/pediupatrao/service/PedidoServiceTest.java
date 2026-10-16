package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.support.ContextoTeste;
import com.umc.pediupatrao.support.Repositorios;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;

/**
 * Testes das regras de pedidos (RF02, RF03, RF04) e da auditoria correspondente, sem MongoDB real.
 */
class PedidoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-16T15:00:00Z"), ZONA);

    private Map<String, Pedido> pedidos;
    private List<AuditoriaEvento> eventos;
    private PedidoRepository pedidoRepository;
    private AuditoriaRepository auditoriaRepository;
    private PedidoService service;

    @BeforeEach
    void preparar() {
        pedidos = new HashMap<>();
        eventos = new ArrayList<>();
        pedidoRepository = Repositorios.pedidos(pedidos);
        auditoriaRepository = Repositorios.auditoria(eventos);

        Cliente cliente = new Cliente();
        cliente.setId("c1");
        cliente.setNome("Maria Souza");
        cliente.setTelefone("(11) 99999-0000");
        ClienteRepository clienteRepository = mock(ClienteRepository.class);
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente));
        when(clienteRepository.findById("inexistente")).thenReturn(Optional.empty());

        ProdutoRepository produtoRepository = mock(ProdutoRepository.class);
        when(produtoRepository.findById("pizza")).thenReturn(Optional.of(produto("pizza", "Pizza Calabresa", 40.00, true)));
        when(produtoRepository.findById("refri")).thenReturn(Optional.of(produto("refri", "Refrigerante", 8.50, true)));
        when(produtoRepository.findById("velho")).thenReturn(Optional.of(produto("velho", "Item inativo", 10.00, false)));
        when(produtoRepository.findById("arredonda")).thenReturn(Optional.of(produto("arredonda", "Preço quebrado", 10.005, true)));
        when(produtoRepository.findById("nulo")).thenReturn(Optional.empty());

        UsuarioContexto contexto = new UsuarioContexto();
        AuditoriaService auditoriaService = new AuditoriaService(
                auditoriaRepository, mock(MongoTemplate.class), contexto, RELOGIO);
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository,
                auditoriaService, contexto, RELOGIO);

        ContextoTeste.autenticar("atendente01", Perfil.ATENDENTE);
    }

    @AfterEach
    void limpar() {
        ContextoTeste.limpar();
    }

    // ---------------------------------------------------------------- RF02: cálculo e registro

    @Test
    void calculaSubtotaisEtotalNoServidor() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 2), item("refri", 3)));

        assertEquals(new BigDecimal("80.00"), p.getItens().get(0).getSubtotal());
        assertEquals(new BigDecimal("25.50"), p.getItens().get(1).getSubtotal());
        assertEquals(new BigDecimal("40.00"), p.getItens().get(0).getPrecoUnitario());
        assertEquals(new BigDecimal("105.50"), p.getSubtotal());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getValorDesconto()));
        assertEquals(new BigDecimal("105.50"), p.getValorTotal());
    }

    @Test
    void arredondaValoresMonetariosComDuasCasas() {
        Pedido p = service.criarPedido(requisicao("RETIRADA", item("arredonda", 3)));

        assertEquals(new BigDecimal("10.01"), p.getItens().get(0).getPrecoUnitario());
        assertEquals(new BigDecimal("30.03"), p.getValorTotal());
    }

    @Test
    void registraEntradaResponsavelEhistoricoAutomaticamente() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));

        assertNotNull(p.getId());
        assertEquals(StatusPedido.RECEBIDO.name(), p.getStatus());
        assertEquals("atendente01", p.getRegistradoPor());
        assertEquals(LocalDateTime.now(RELOGIO), p.getDataHoraEntrada());
        assertEquals("Maria Souza", p.getClienteNome());
        assertEquals(1, p.getHistorico().size());
        assertEquals("atendente01", p.getHistorico().get(0).getUsuario());
        assertEquals("ATENDENTE", p.getHistorico().get(0).getPerfil());
        assertNull(p.getDataHoraSaida());
    }

    @Test
    void validaDadosObrigatorios() {
        assertThrows(NegocioException.class, () -> service.criarPedido(null));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA")));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("pizza", 0))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("pizza", -2))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("pizza", 100))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("pizza", null))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("nulo", 1))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("ENTREGA", item("velho", 1))));
        assertThrows(NegocioException.class, () -> service.criarPedido(requisicao("VOANDO", item("pizza", 1))));
        PedidoRequest semCliente = requisicao("ENTREGA", item("pizza", 1));
        semCliente.setClienteId("inexistente");
        assertThrows(NegocioException.class, () -> service.criarPedido(semCliente));
        assertTrue(pedidos.isEmpty(), "Nenhum pedido deve ser gravado quando a validação falha");
        assertTrue(eventos.isEmpty(), "Nenhuma auditoria deve ser gravada quando a validação falha");
    }

    @Test
    void auditaCriacaoDoPedidoComUsuarioPerfilEValores() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 2)));

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.CRIACAO_PEDIDO, e.getOperacao());
        assertEquals("PEDIDO", e.getEntidadeTipo());
        assertEquals(p.getId(), e.getEntidadeId());
        assertEquals("atendente01", e.getUsuario());
        assertEquals("ATENDENTE", e.getPerfil());
        assertEquals(LocalDateTime.now(RELOGIO), e.getDataHora());
        assertEquals("80.00", campo(e, "valorTotal").getValorNovo());
        assertNull(campo(e, "valorTotal").getValorAnterior());
        assertEquals("RECEBIDO", campo(e, "status").getValorNovo());
        assertEquals("atendente01", campo(e, "registradoPor").getValorNovo());
        assertNotNull(campo(e, "dataHoraEntrada").getValorNovo());
    }

    @Test
    void alteraItensSomenteEnquantoRecebidoERecalculaTotais() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        eventos.clear();

        Pedido alterado = service.atualizarPedido(p.getId(), requisicao("RETIRADA", item("pizza", 2), item("refri", 1)));

        assertEquals(new BigDecimal("88.50"), alterado.getValorTotal());
        assertEquals(1, eventos.size());
        assertEquals(TipoOperacao.ALTERACAO_PEDIDO, eventos.get(0).getOperacao());
        assertEquals("40.00", campo(eventos.get(0), "valorTotal").getValorAnterior());
        assertEquals("88.50", campo(eventos.get(0), "valorTotal").getValorNovo());
        assertEquals("ENTREGA", campo(eventos.get(0), "tipoEntrega").getValorAnterior());
        assertEquals("RETIRADA", campo(eventos.get(0), "tipoEntrega").getValorNovo());

        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        assertThrows(ConflitoException.class,
                () -> service.atualizarPedido(p.getId(), requisicao("ENTREGA", item("pizza", 1))));
    }

    // ---------------------------------------------------------------- RF03: transições, entrada e saída

    @Test
    void percorreFluxoCompletoDeEntregaRegistrandoSaidaEResponsaveis() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        String id = p.getId();

        ContextoTeste.autenticar("cozinha01", Perfil.ATENDENTE);
        assertEquals("EM_PREPARACAO", service.atualizarStatus(id, "EM_PREPARACAO").getStatus());
        assertEquals("PRONTO", service.atualizarStatus(id, "PRONTO").getStatus());
        assertNull(pedidos.get(id).getDataHoraSaida());

        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        Pedido saiu = service.registrarSaida(id);
        assertEquals("SAIU_PARA_ENTREGA", saiu.getStatus());
        assertEquals("gerente01", saiu.getSaidaPor());
        assertEquals(LocalDateTime.now(RELOGIO), saiu.getDataHoraSaida());

        Pedido fim = service.atualizarStatus(id, "FINALIZADO");
        assertEquals("FINALIZADO", fim.getStatus());
        assertEquals(5, fim.getHistorico().size());
        assertEquals("cozinha01", fim.getHistorico().get(1).getUsuario());
        assertEquals("gerente01", fim.getHistorico().get(3).getUsuario());

        // O evento de saída é auditado com o responsável e o horário
        AuditoriaEvento saida = eventos.stream()
                .filter(e -> e.getOperacao() == TipoOperacao.SAIDA_PEDIDO).findFirst().orElseThrow();
        assertEquals("gerente01", saida.getUsuario());
        assertEquals("PRONTO", campo(saida, "status").getValorAnterior());
        assertEquals("SAIU_PARA_ENTREGA", campo(saida, "status").getValorNovo());
        assertEquals("gerente01", campo(saida, "saidaPor").getValorNovo());
        assertNotNull(campo(saida, "dataHoraSaida").getValorNovo());
        long mudancas = eventos.stream().filter(e -> e.getOperacao() == TipoOperacao.MUDANCA_STATUS).count();
        assertEquals(3, mudancas); // EM_PREPARACAO, PRONTO e FINALIZADO
    }

    @Test
    void retiradaNoBalcaoUsaEstadoRetirado() {
        Pedido p = service.criarPedido(requisicao("RETIRADA", item("pizza", 1)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        service.atualizarStatus(p.getId(), "PRONTO");

        Pedido retirado = service.registrarSaida(p.getId());

        assertEquals("RETIRADO", retirado.getStatus());
        assertEquals("atendente01", retirado.getSaidaPor());
        assertNotNull(retirado.getDataHoraSaida());
    }

    @Test
    void bloqueiaTransicoesInvalidas() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        String id = p.getId();

        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "PRONTO"));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "FINALIZADO"));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "RECEBIDO"));
        assertThrows(NegocioException.class, () -> service.atualizarStatus(id, "INEXISTENTE"));

        service.atualizarStatus(id, "EM_PREPARACAO");
        // finalizar um pedido em preparação é inválido
        ConflitoException e = assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "FINALIZADO"));
        assertTrue(e.getMessage().contains("Transição inválida"));
        assertEquals("EM_PREPARACAO", pedidos.get(id).getStatus());
    }

    @Test
    void impedeSaidaAntesDeEstarPronto() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        String id = p.getId();

        assertThrows(ConflitoException.class, () -> service.registrarSaida(id));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "SAIU_PARA_ENTREGA"));
        service.atualizarStatus(id, "EM_PREPARACAO");
        assertThrows(ConflitoException.class, () -> service.registrarSaida(id));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "RETIRADO"));
        assertNull(pedidos.get(id).getDataHoraSaida());
        assertNull(pedidos.get(id).getSaidaPor());
    }

    @Test
    void naoPermiteSairComoRetiradoUmPedidoDeEntrega() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        service.atualizarStatus(p.getId(), "PRONTO");

        assertThrows(ConflitoException.class, () -> service.atualizarStatus(p.getId(), "RETIRADO"));
        assertEquals("PRONTO", pedidos.get(p.getId()).getStatus());
    }

    @Test
    void pedidoFinalizadoNaoAceitaAlteracoes() {
        String id = pedidoFinalizado();
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "PRONTO"));
        assertThrows(ConflitoException.class, () -> service.aplicarDesconto(id, new BigDecimal("5")));
        assertThrows(ConflitoException.class, () -> service.cancelar(id, "Tentativa após finalizar"));
        assertThrows(ConflitoException.class, () -> service.atualizarPedido(id, requisicao("ENTREGA", item("pizza", 1))));
        assertEquals("FINALIZADO", pedidos.get(id).getStatus());
    }

    @Test
    void naoPermiteInformarCancelamentoPeloEndpointDeStatus() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));

        // ATENDENTE: acesso negado (somente o GERENTE cancela)
        assertThrows(AccessDeniedException.class, () -> service.atualizarStatus(p.getId(), "CANCELADO"));

        // GERENTE: precisa usar a operação de cancelamento (que exige justificativa)
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        assertThrows(NegocioException.class, () -> service.atualizarStatus(p.getId(), "CANCELADO"));
        assertEquals("RECEBIDO", pedidos.get(p.getId()).getStatus());
    }

    // ---------------------------------------------------------------- RF04: descontos

    @Test
    void aplicaDescontoPercentualERecalculaTotalComAuditoria() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 2), item("refri", 3)));
        eventos.clear();
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        Pedido com = service.aplicarDesconto(p.getId(), new BigDecimal("10"));

        assertEquals(new BigDecimal("10.00"), com.getDescontoPercentual());
        assertEquals(new BigDecimal("10.55"), com.getValorDesconto());
        assertEquals(new BigDecimal("94.95"), com.getValorTotal());
        assertEquals(new BigDecimal("105.50"), com.getSubtotal());

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.DESCONTO_PEDIDO, e.getOperacao());
        assertEquals("gerente01", e.getUsuario());
        assertEquals("GERENTE", e.getPerfil());
        assertEquals("0.00", campo(e, "descontoPercentual").getValorAnterior());
        assertEquals("10.00", campo(e, "descontoPercentual").getValorNovo());
        assertEquals("105.50", campo(e, "valorTotal").getValorAnterior());
        assertEquals("94.95", campo(e, "valorTotal").getValorNovo());
    }

    @Test
    void permiteDescontoDeExatamenteVintePorCentoMasNaoMais() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 2), item("refri", 3)));
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        Pedido vinte = service.aplicarDesconto(p.getId(), new BigDecimal("20"));
        assertEquals(new BigDecimal("21.10"), vinte.getValorDesconto());
        assertEquals(new BigDecimal("84.40"), vinte.getValorTotal());

        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("20.01")));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("21")));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("100")));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("-1")));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("1.234")));
        assertThrows(NegocioException.class, () -> service.aplicarDesconto(p.getId(), null));
        assertEquals(new BigDecimal("84.40"), pedidos.get(p.getId()).getValorTotal());
    }

    @Test
    void descontoSubstituiOAnteriorEPodeSerRemovido() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        service.aplicarDesconto(p.getId(), new BigDecimal("10"));
        Pedido cinco = service.aplicarDesconto(p.getId(), new BigDecimal("5"));
        assertEquals(new BigDecimal("38.00"), cinco.getValorTotal());

        Pedido zero = service.aplicarDesconto(p.getId(), BigDecimal.ZERO);
        assertEquals(new BigDecimal("40.00"), zero.getValorTotal());
    }

    @Test
    void descontoSoEhPermitidoAntesDaSaida() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        service.atualizarStatus(p.getId(), "PRONTO");
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        // PRONTO ainda permite
        service.aplicarDesconto(p.getId(), new BigDecimal("5"));

        service.registrarSaida(p.getId());
        assertThrows(ConflitoException.class, () -> service.aplicarDesconto(p.getId(), new BigDecimal("10")));
        assertEquals(new BigDecimal("38.00"), pedidos.get(p.getId()).getValorTotal());
    }

    @Test
    void edicaoDeItensPreservaPercentualDeDescontoJaAutorizado() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        service.aplicarDesconto(p.getId(), new BigDecimal("10"));

        Pedido alterado = service.atualizarPedido(p.getId(), requisicao("ENTREGA", item("pizza", 2)));

        assertEquals(new BigDecimal("10.00"), alterado.getDescontoPercentual());
        assertEquals(new BigDecimal("72.00"), alterado.getValorTotal());
    }

    // ---------------------------------------------------------------- RF04: cancelamento

    @Test
    void cancelaComJustificativaPreservandoDadosEauditando() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 2)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        eventos.clear();
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        Pedido c = service.cancelar(p.getId(), "  Cliente desistiu do pedido  ");

        assertEquals("CANCELADO", c.getStatus());
        assertEquals("gerente01", c.getCanceladoPor());
        assertEquals(LocalDateTime.now(RELOGIO), c.getDataHoraCancelamento());
        assertEquals("Cliente desistiu do pedido", c.getJustificativaCancelamento());
        // dados históricos preservados
        assertEquals(1, c.getItens().size());
        assertEquals(new BigDecimal("80.00"), c.getValorTotal());
        assertEquals("atendente01", c.getRegistradoPor());
        assertEquals(3, c.getHistorico().size());

        assertEquals(1, eventos.size());
        AuditoriaEvento e = eventos.get(0);
        assertEquals(TipoOperacao.CANCELAMENTO_PEDIDO, e.getOperacao());
        assertEquals("Cliente desistiu do pedido", e.getJustificativa());
        assertEquals("gerente01", e.getUsuario());
        assertEquals("GERENTE", e.getPerfil());
        assertEquals("EM_PREPARACAO", campo(e, "status").getValorAnterior());
        assertEquals("CANCELADO", campo(e, "status").getValorNovo());
        assertEquals("gerente01", campo(e, "canceladoPor").getValorNovo());
    }

    @Test
    void cancelamentoExigeJustificativa() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        assertThrows(NegocioException.class, () -> service.cancelar(p.getId(), null));
        assertThrows(NegocioException.class, () -> service.cancelar(p.getId(), ""));
        assertThrows(NegocioException.class, () -> service.cancelar(p.getId(), "     "));
        assertThrows(NegocioException.class, () -> service.cancelar(p.getId(), "ab"));
        assertThrows(NegocioException.class, () -> service.cancelar(p.getId(), "x".repeat(501)));
        assertEquals("RECEBIDO", pedidos.get(p.getId()).getStatus());
    }

    @Test
    void naoCancelaDepoisDaSaidaOuFinalizacao() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        service.atualizarStatus(p.getId(), "PRONTO");
        service.registrarSaida(p.getId());
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);

        assertThrows(ConflitoException.class, () -> service.cancelar(p.getId(), "Tarde demais para cancelar"));
        assertEquals("SAIU_PARA_ENTREGA", pedidos.get(p.getId()).getStatus());

        service.atualizarStatus(p.getId(), "FINALIZADO");
        assertThrows(ConflitoException.class, () -> service.cancelar(p.getId(), "Tarde demais para cancelar"));
    }

    @Test
    void pedidoCanceladoNaoPodeMaisSerAlterado() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        String id = p.getId();
        ContextoTeste.autenticar("gerente01", Perfil.GERENTE);
        service.cancelar(id, "Pedido duplicado no sistema");
        int eventosAntes = eventos.size();

        assertThrows(ConflitoException.class, () -> service.cancelar(id, "Cancelar novamente o pedido"));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "EM_PREPARACAO"));
        assertThrows(ConflitoException.class, () -> service.atualizarStatus(id, "FINALIZADO"));
        assertThrows(ConflitoException.class, () -> service.aplicarDesconto(id, new BigDecimal("10")));
        assertThrows(ConflitoException.class, () -> service.registrarSaida(id));
        assertThrows(ConflitoException.class, () -> service.atualizarPedido(id, requisicao("ENTREGA", item("pizza", 3))));

        assertEquals("CANCELADO", pedidos.get(id).getStatus());
        assertEquals(new BigDecimal("40.00"), pedidos.get(id).getValorTotal());
        assertEquals(eventosAntes, eventos.size(), "Tentativas bloqueadas não geram auditoria");
    }

    // ---------------------------------------------------------------- consistência com a auditoria

    @Test
    void desfazCriacaoQuandoAuditoriaFalha() {
        doThrow(new RuntimeException("mongo fora do ar")).when(auditoriaRepository).save(any(AuditoriaEvento.class));

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.criarPedido(requisicao("ENTREGA", item("pizza", 1))));

        assertTrue(e.isOperacaoDesfeita());
        assertTrue(pedidos.isEmpty(), "O pedido não deve permanecer sem o registro de auditoria");
    }

    @Test
    void desfazMudancaDeStatusQuandoAuditoriaFalha() {
        Pedido p = service.criarPedido(requisicao("ENTREGA", item("pizza", 1)));
        doThrow(new RuntimeException("mongo fora do ar")).when(auditoriaRepository).save(any(AuditoriaEvento.class));

        AuditoriaException e = assertThrows(AuditoriaException.class,
                () -> service.atualizarStatus(p.getId(), "EM_PREPARACAO"));

        assertTrue(e.isOperacaoDesfeita());
        assertEquals("RECEBIDO", pedidos.get(p.getId()).getStatus());
        assertEquals(1, pedidos.get(p.getId()).getHistorico().size());
    }

    @Test
    void naoGravaPedidoSemUsuarioAutenticado() {
        ContextoTeste.limpar();
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> service.criarPedido(requisicao("ENTREGA", item("pizza", 1))));
        assertTrue(pedidos.isEmpty());
    }

    // ---------------------------------------------------------------- auxiliares

    private String pedidoFinalizado() {
        Pedido p = service.criarPedido(requisicao("RETIRADA", item("pizza", 1)));
        service.atualizarStatus(p.getId(), "EM_PREPARACAO");
        service.atualizarStatus(p.getId(), "PRONTO");
        service.registrarSaida(p.getId());
        service.atualizarStatus(p.getId(), "FINALIZADO");
        return p.getId();
    }

    private static PedidoRequest requisicao(String tipo, ItemPedidoRequest... itens) {
        PedidoRequest r = new PedidoRequest();
        r.setClienteId("c1");
        r.setTipoEntrega(tipo);
        r.setItens(new ArrayList<>(List.of(itens)));
        return r;
    }

    private static ItemPedidoRequest item(String produtoId, Integer quantidade) {
        return new ItemPedidoRequest(produtoId, quantidade);
    }

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setTipo("Pizza");
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    private static CampoAlterado campo(AuditoriaEvento evento, String nome) {
        return evento.getAlteracoes().stream()
                .filter(c -> nome.equals(c.getCampo()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Campo não auditado: " + nome
                        + " (campos: " + evento.getAlteracoes().stream().map(CampoAlterado::getCampo).toList() + ")"));
    }
}
