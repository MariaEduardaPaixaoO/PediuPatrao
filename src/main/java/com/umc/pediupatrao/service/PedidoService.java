package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.TransicaoStatus;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negócio dos pedidos (RF02, RF03 e RF04).
 *
 * - Valores (preços, subtotais, desconto, total) são calculados exclusivamente aqui.
 * - Usuário responsável e horários vêm do contexto de segurança e do relógio do servidor.
 * - Transições são validadas na camada de serviço.
 * - Toda operação que altera o pedido gera um evento de auditoria.
 */
@Service
public class PedidoService {

    public static final String ENTIDADE = "PEDIDO";
    public static final BigDecimal DESCONTO_MAXIMO = new BigDecimal("20");
    public static final int QUANTIDADE_MAXIMA = 99;
    public static final int JUSTIFICATIVA_MINIMA = 5;
    public static final int JUSTIFICATIVA_MAXIMA = 500;

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final UsuarioContexto contexto;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService,
            UsuarioContexto contexto, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.contexto = contexto;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ consultas

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAllByOrderByDataHoraEntradaDesc();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return obter(id);
    }

    // ------------------------------------------------------------------ RF02: registro e alteração

    /** Registra um novo pedido (status RECEBIDO), com entrada e responsável definidos pelo servidor. */
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    @Transactional
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null) {
            throw new NegocioException("Dados do pedido não informados.");
        }
        Cliente cliente = obterCliente(request.getClienteId());
        TipoEntrega tipo = obterTipoEntrega(request.getTipoEntrega());
        List<ItemPedido> itens = montarItens(request.getItens());

        String usuario = contexto.usuario();
        String perfil = contexto.perfil();
        LocalDateTime agora = LocalDateTime.now(clock);

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(tipo);
        pedido.setItens(itens);
        pedido.setDescontoPercentual(BigDecimal.ZERO.setScale(2));
        recalcularTotais(pedido);
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        pedido.setRegistradoPor(usuario);
        pedido.setDataHoraEntrada(agora);
        pedido.getHistorico().add(new TransicaoStatus(null, StatusPedido.RECEBIDO.name(),
                usuario, perfil, agora, "Pedido recebido"));

        Pedido salvo = pedidoRepository.save(pedido);
        final String id = salvo.getId();
        auditoriaService.registrarComCompensacao(
                () -> pedidoRepository.deleteById(id),
                TipoOperacao.CRIACAO_PEDIDO, ENTIDADE, id,
                Diferencas.comparar(null, resumo(salvo)), null);
        return salvo;
    }

    /** Altera cliente, tipo de entrega e itens. Permitido somente enquanto o pedido está RECEBIDO. */
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    @Transactional
    public Pedido atualizarPedido(String id, PedidoRequest request) {
        if (request == null) {
            throw new NegocioException("Dados do pedido não informados.");
        }
        Pedido pedido = obter(id);
        StatusPedido atual = statusAtual(pedido);
        bloquearSeEncerrado(atual);
        if (atual != StatusPedido.RECEBIDO) {
            throw new ConflitoException("O pedido só pode ser alterado enquanto estiver com status RECEBIDO.");
        }
        Cliente cliente = obterCliente(request.getClienteId());
        TipoEntrega tipo = obterTipoEntrega(request.getTipoEntrega());
        List<ItemPedido> itens = montarItens(request.getItens());

        Pedido anterior = pedido.copiar();
        Map<String, String> antes = resumo(anterior);

        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(tipo);
        pedido.setItens(itens);
        recalcularTotais(pedido); // mantém o percentual de desconto já autorizado, recalculando valores

        return persistirComAuditoria(pedido, anterior, antes, TipoOperacao.ALTERACAO_PEDIDO, null);
    }

    // ------------------------------------------------------------------ RF03: transições

    /**
     * Avança o pedido para o próximo estado válido. O horário e o responsável vêm do servidor.
     * O cancelamento NÃO é feito por aqui (ver {@link #cancelar}).
     */
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    @Transactional
    public Pedido atualizarStatus(String id, String novoStatus) {
        StatusPedido destino = StatusPedido.interpretar(novoStatus)
                .orElseThrow(() -> new NegocioException("Status inválido: " + novoStatus));
        Pedido pedido = obter(id);
        StatusPedido atual = statusAtual(pedido);

        if (destino == StatusPedido.CANCELADO) {
            if (!contexto.possuiPerfil(Perfil.GERENTE)) {
                throw new AccessDeniedException("Somente o GERENTE pode cancelar pedidos.");
            }
            throw new NegocioException("O cancelamento exige justificativa: utilize a operação de cancelamento.");
        }
        bloquearSeEncerrado(atual);

        if (destino.saida() && atual != StatusPedido.PRONTO) {
            throw new ConflitoException("O pedido só pode sair para entrega/retirada depois de estar PRONTO.");
        }
        if (!atual.proximos(pedido.getTipoEntrega()).contains(destino)) {
            throw new ConflitoException("Transição inválida: " + atual.getDescricao()
                    + " → " + destino.getDescricao() + ".");
        }

        Pedido anterior = pedido.copiar();
        Map<String, String> antes = resumo(anterior);
        String usuario = contexto.usuario();
        LocalDateTime agora = LocalDateTime.now(clock);

        pedido.setStatus(destino.name());
        pedido.getHistorico().add(new TransicaoStatus(atual.name(), destino.name(),
                usuario, contexto.perfil(), agora, null));
        TipoOperacao operacao = TipoOperacao.MUDANCA_STATUS;
        if (destino.saida()) {
            pedido.setSaidaPor(usuario);
            pedido.setDataHoraSaida(agora);
            operacao = TipoOperacao.SAIDA_PEDIDO;
        }
        return persistirComAuditoria(pedido, anterior, antes, operacao, null);
    }

    /** Registra a saída (entrega ou retirada, conforme o tipo do pedido). Exige o pedido PRONTO. */
    @PreAuthorize("hasAnyRole('ATENDENTE','GERENTE')")
    @Transactional
    public Pedido registrarSaida(String id) {
        Pedido pedido = obter(id);
        StatusPedido atual = statusAtual(pedido);
        bloquearSeEncerrado(atual);
        if (atual != StatusPedido.PRONTO) {
            throw new ConflitoException("O pedido só pode sair para entrega/retirada depois de estar PRONTO.");
        }
        StatusPedido destino = pedido.getTipoEntrega() == TipoEntrega.RETIRADA
                ? StatusPedido.RETIRADO : StatusPedido.SAIU_PARA_ENTREGA;
        return atualizarStatus(id, destino.name());
    }

    // ------------------------------------------------------------------ RF04: desconto e cancelamento

    /** Aplica (substitui) o desconto percentual do pedido. Exclusivo do GERENTE. */
    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null) {
            throw new NegocioException("Informe o percentual de desconto.");
        }
        if (percentual.compareTo(BigDecimal.ZERO) < 0 || percentual.compareTo(DESCONTO_MAXIMO) > 0) {
            throw new NegocioException("O desconto deve estar entre 0% e 20%.");
        }
        if (percentual.stripTrailingZeros().scale() > 2) {
            throw new NegocioException("O desconto aceita no máximo 2 casas decimais.");
        }
        Pedido pedido = obter(id);
        StatusPedido atual = statusAtual(pedido);
        bloquearSeEncerrado(atual);
        if (!atual.antesDaSaida()) {
            throw new ConflitoException("O desconto só pode ser aplicado antes da saída para entrega/retirada.");
        }
        BigDecimal novo = percentual.setScale(2, RoundingMode.HALF_UP);
        BigDecimal atualPercentual = pedido.getDescontoPercentual() == null
                ? BigDecimal.ZERO.setScale(2) : pedido.getDescontoPercentual();
        if (atualPercentual.compareTo(novo) == 0) {
            throw new NegocioException("O desconto informado já está aplicado ao pedido.");
        }

        Pedido anterior = pedido.copiar();
        Map<String, String> antes = resumo(anterior);
        pedido.setDescontoPercentual(novo);
        recalcularTotais(pedido);
        return persistirComAuditoria(pedido, anterior, antes, TipoOperacao.DESCONTO_PEDIDO, null);
    }

    /** Cancela o pedido, com justificativa obrigatória. Exclusivo do GERENTE. */
    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public Pedido cancelar(String id, String justificativa) {
        String texto = justificativa == null ? "" : justificativa.trim();
        if (texto.isEmpty()) {
            throw new NegocioException("A justificativa do cancelamento é obrigatória.");
        }
        if (texto.length() < JUSTIFICATIVA_MINIMA) {
            throw new NegocioException("A justificativa deve ter pelo menos " + JUSTIFICATIVA_MINIMA + " caracteres.");
        }
        if (texto.length() > JUSTIFICATIVA_MAXIMA) {
            throw new NegocioException("A justificativa deve ter no máximo " + JUSTIFICATIVA_MAXIMA + " caracteres.");
        }
        Pedido pedido = obter(id);
        StatusPedido atual = statusAtual(pedido);
        if (atual == StatusPedido.CANCELADO) {
            throw new ConflitoException("O pedido já está cancelado.");
        }
        if (atual == StatusPedido.FINALIZADO) {
            throw new ConflitoException("Pedido finalizado não pode ser cancelado.");
        }
        if (!atual.antesDaSaida()) {
            throw new ConflitoException("O pedido já saiu para entrega/retirada e não pode mais ser cancelado.");
        }

        Pedido anterior = pedido.copiar();
        Map<String, String> antes = resumo(anterior);
        String usuario = contexto.usuario();
        LocalDateTime agora = LocalDateTime.now(clock);

        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setCanceladoPor(usuario);
        pedido.setDataHoraCancelamento(agora);
        pedido.setJustificativaCancelamento(texto);
        pedido.getHistorico().add(new TransicaoStatus(atual.name(), StatusPedido.CANCELADO.name(),
                usuario, contexto.perfil(), agora, texto));
        return persistirComAuditoria(pedido, anterior, antes, TipoOperacao.CANCELAMENTO_PEDIDO, texto);
    }

    // ------------------------------------------------------------------ auxiliares

    private Pedido persistirComAuditoria(Pedido pedido, Pedido anterior, Map<String, String> antes,
            TipoOperacao operacao, String justificativa) {
        Map<String, String> depois = resumo(pedido);
        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrarComCompensacao(
                () -> {
                    Pedido restaurar = anterior.copiar();
                    restaurar.setVersion(salvo.getVersion()); // evita conflito de versão ao restaurar
                    pedidoRepository.save(restaurar);
                },
                operacao, ENTIDADE, salvo.getId(), Diferencas.comparar(antes, depois), justificativa);
        return salvo;
    }

    private Pedido obter(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Pedido não encontrado."));
    }

    private StatusPedido statusAtual(Pedido pedido) {
        return StatusPedido.interpretar(pedido.getStatus())
                .orElseThrow(() -> new ConflitoException(
                        "Pedido em formato antigo (status \"" + pedido.getStatus() + "\") não pode ser alterado."));
    }

    private void bloquearSeEncerrado(StatusPedido atual) {
        if (atual == StatusPedido.CANCELADO) {
            throw new ConflitoException("Pedido cancelado não pode mais ser alterado.");
        }
        if (atual == StatusPedido.FINALIZADO) {
            throw new ConflitoException("Pedido finalizado não pode mais ser alterado.");
        }
    }

    private Cliente obterCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new NegocioException("Selecione o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new NegocioException("Cliente não encontrado."));
    }

    private TipoEntrega obterTipoEntrega(String valor) {
        return TipoEntrega.interpretar(valor)
                .orElseThrow(() -> new NegocioException("Informe o tipo do pedido: ENTREGA ou RETIRADA."));
    }

    /** Monta os itens com preços lidos do cadastro de produtos (nunca da requisição). */
    private List<ItemPedido> montarItens(List<ItemPedidoRequest> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new NegocioException("O pedido deve ter ao menos um item.");
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemPedidoRequest s : solicitados) {
            if (s == null || s.getProdutoId() == null || s.getProdutoId().isBlank()) {
                throw new NegocioException("Selecione o produto de todos os itens.");
            }
            Integer quantidade = s.getQuantidade();
            if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA) {
                throw new NegocioException("A quantidade de cada item deve estar entre 1 e " + QUANTIDADE_MAXIMA + ".");
            }
            Produto produto = produtoRepository.findById(s.getProdutoId())
                    .orElseThrow(() -> new NegocioException("Produto não encontrado."));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new NegocioException("O produto \"" + produto.getNome() + "\" está inativo.");
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new NegocioException("O produto \"" + produto.getNome() + "\" não possui preço válido.");
            }
            BigDecimal preco = BigDecimal.valueOf(produto.getPreco()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = preco.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_UP);
            itens.add(new ItemPedido(produto.getId(), produto.getNome(), quantidade, preco, subtotal));
        }
        return itens;
    }

    /** Recalcula subtotal, valor do desconto e total a partir dos itens e do percentual autorizado. */
    static void recalcularTotais(Pedido pedido) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        for (ItemPedido item : pedido.getItens()) {
            subtotal = subtotal.add(item.getSubtotal());
        }
        BigDecimal percentual = pedido.getDescontoPercentual() == null
                ? BigDecimal.ZERO.setScale(2) : pedido.getDescontoPercentual();
        BigDecimal desconto = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);
        pedido.setSubtotal(subtotal);
        pedido.setDescontoPercentual(percentual);
        pedido.setValorDesconto(desconto);
        pedido.setValorTotal(subtotal.subtract(desconto).setScale(2, RoundingMode.HALF_UP));
    }

    /** Resumo textual do pedido (campo -> valor) usado para comparar estados na auditoria. */
    static Map<String, String> resumo(Pedido p) {
        Map<String, String> m = new LinkedHashMap<>();
        Diferencas.put(m, "clienteId", p.getClienteId());
        Diferencas.put(m, "clienteNome", p.getClienteNome());
        Diferencas.put(m, "tipoEntrega", p.getTipoEntrega());
        Diferencas.put(m, "itens", descreverItens(p.getItens()));
        Diferencas.put(m, "subtotal", p.getSubtotal());
        Diferencas.put(m, "descontoPercentual", p.getDescontoPercentual());
        Diferencas.put(m, "valorDesconto", p.getValorDesconto());
        Diferencas.put(m, "valorTotal", p.getValorTotal());
        Diferencas.put(m, "status", p.getStatus());
        Diferencas.put(m, "registradoPor", p.getRegistradoPor());
        Diferencas.put(m, "dataHoraEntrada", formatar(p.getDataHoraEntrada()));
        Diferencas.put(m, "saidaPor", p.getSaidaPor());
        Diferencas.put(m, "dataHoraSaida", formatar(p.getDataHoraSaida()));
        Diferencas.put(m, "canceladoPor", p.getCanceladoPor());
        Diferencas.put(m, "dataHoraCancelamento", formatar(p.getDataHoraCancelamento()));
        Diferencas.put(m, "justificativaCancelamento", p.getJustificativaCancelamento());
        return m;
    }

    private static String descreverItens(List<ItemPedido> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ItemPedido i : itens) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(i.getQuantidade()).append("x ").append(i.getNomeProduto())
                    .append(" (R$ ").append(i.getPrecoUnitario()).append(")");
        }
        return sb.toString();
    }

    private static String formatar(LocalDateTime dataHora) {
        return dataHora == null ? null : dataHora.format(FORMATO_DATA_HORA);
    }
}
