package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.dto.dashboard.ComparacaoPeriodoDTO;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Indicadores do dashboard calculados sobre os pedidos já consultados (regras em docs/requisitos.md).
 *
 * - Venda é o pedido FINALIZADO; cancelados não entram em vendas, ticket médio nem ranking.
 * - O valor é o valorTotal gravado pelo PedidoService; nenhum preço é recalculado aqui.
 * - Pedidos com status fora do enum (modelo antigo) contam como pedido, mas não são classificados.
 */
public final class DashboardCalculadora {

    /** Acima disso a série passa a ser mensal. */
    public static final int LIMITE_DIAS_AGRUPAMENTO_DIARIO = 62;
    public static final String AGRUPAMENTO_DIA = "DIA";
    public static final String AGRUPAMENTO_MES = "MES";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = new BigDecimal("100");
    private static final DateTimeFormatter ROTULO_DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter ROTULO_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    private DashboardCalculadora() {
    }

    // ------------------------------------------------------------------ RF01

    public static ResumoDashboardResponse resumo(List<Pedido> atuais, FiltroPeriodo periodo,
            List<Pedido> anteriores, FiltroPeriodo periodoAnterior, LocalDateTime geradoEm) {
        Totais atual = totais(atuais, periodo);
        Totais anterior = totais(anteriores, periodoAnterior);

        BigDecimal ticketAtual = ticket(atual);
        BigDecimal ticketAnterior = ticket(anterior);
        ComparacaoPeriodoDTO comparacao = new ComparacaoPeriodoDTO(
                periodoAnterior.paraDTO(),
                anterior.totalVendas,
                anterior.pedidos,
                anterior.vendas,
                ticketAnterior,
                anterior.cancelados,
                variacao(atual.totalVendas, anterior.totalVendas),
                variacao(BigDecimal.valueOf(atual.pedidos), BigDecimal.valueOf(anterior.pedidos)),
                variacao(ticketAtual, ticketAnterior),
                anterior.pedidos > 0);

        return new ResumoDashboardResponse(
                periodo.paraDTO(),
                atual.totalVendas,
                atual.vendas,
                atual.pedidos,
                ticketAtual,
                atual.cancelados,
                atual.valorCancelado,
                percentual(atual.cancelados, atual.pedidos),
                atual.emAndamento,
                atual.valorEmAndamento,
                atual.naoClassificados,
                comparacao,
                geradoEm);
    }

    // ------------------------------------------------------------------ RF02

    public static VendasPeriodoResponse vendas(List<Pedido> atuais, FiltroPeriodo periodo,
            List<Pedido> anteriores, FiltroPeriodo periodoAnterior) {
        String agrupamento = periodo.dias() > LIMITE_DIAS_AGRUPAMENTO_DIARIO ? AGRUPAMENTO_MES : AGRUPAMENTO_DIA;
        List<PontoVendaDTO> pontos = serie(atuais, periodo, agrupamento);
        List<PontoVendaDTO> pontosAnterior = serie(anteriores, periodoAnterior, agrupamento);
        return new VendasPeriodoResponse(
                periodo.paraDTO(),
                periodoAnterior.paraDTO(),
                agrupamento,
                pontos,
                pontosAnterior,
                somaVendas(pontos),
                somaVendas(pontosAnterior));
    }

    private static List<PontoVendaDTO> serie(List<Pedido> pedidos, FiltroPeriodo periodo, String agrupamento) {
        boolean mensal = AGRUPAMENTO_MES.equals(agrupamento);
        // dias (ou meses) sem pedido também aparecem, com zero
        Map<LocalDate, Acumulador> intervalos = new LinkedHashMap<>();
        LocalDate cursor = mensal ? periodo.inicio().withDayOfMonth(1) : periodo.inicio();
        while (!cursor.isAfter(periodo.fim())) {
            intervalos.put(cursor, new Acumulador());
            cursor = mensal ? cursor.plusMonths(1) : cursor.plusDays(1);
        }
        for (Pedido p : seguro(pedidos)) {
            if (!periodo.contem(p.getDataHoraEntrada())) {
                continue;
            }
            LocalDate dia = p.getDataHoraEntrada().toLocalDate();
            LocalDate chave = mensal ? dia.withDayOfMonth(1) : dia;
            Acumulador a = intervalos.get(chave);
            if (a == null) {
                continue;
            }
            a.pedidos++;
            Optional<StatusPedido> status = StatusPedido.interpretar(p.getStatus());
            if (status.isPresent() && status.get() == StatusPedido.FINALIZADO) {
                a.vendas++;
                a.valor = a.valor.add(valor(p));
            } else if (status.isPresent() && status.get() == StatusPedido.CANCELADO) {
                a.cancelados++;
            }
        }
        List<PontoVendaDTO> pontos = new ArrayList<>();
        intervalos.forEach((inicio, a) -> pontos.add(new PontoVendaDTO(
                mensal ? YearMonth.from(inicio).format(ROTULO_MES) : inicio.format(ROTULO_DIA),
                inicio, a.valor, a.vendas, a.pedidos, a.cancelados)));
        return pontos;
    }

    // ------------------------------------------------------------------ RF03

    public static StatusPedidosResponse status(List<Pedido> pedidos, FiltroPeriodo periodo) {
        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        long total = 0;
        long naoClassificados = 0;
        for (Pedido p : seguro(pedidos)) {
            if (!periodo.contem(p.getDataHoraEntrada())) {
                continue;
            }
            total++;
            Optional<StatusPedido> status = StatusPedido.interpretar(p.getStatus());
            if (status.isPresent()) {
                contagem.merge(status.get(), 1L, Long::sum);
            } else {
                naoClassificados++;
            }
        }
        List<StatusQuantidadeDTO> itens = new ArrayList<>();
        for (StatusPedido s : StatusPedido.values()) {
            long q = contagem.get(s);
            itens.add(new StatusQuantidadeDTO(s.name(), s.getDescricao(), q, percentual(q, total)));
        }
        return new StatusPedidosResponse(periodo.paraDTO(), total, itens, naoClassificados);
    }

    // ------------------------------------------------------------------ RF04

    public static ProdutosMaisVendidosResponse produtosMaisVendidos(List<Pedido> pedidos, FiltroPeriodo periodo,
            int limite) {
        Map<String, AcumuladorProduto> porProduto = new LinkedHashMap<>();
        for (Pedido p : seguro(pedidos)) {
            if (!periodo.contem(p.getDataHoraEntrada())
                    || StatusPedido.interpretar(p.getStatus()).orElse(null) != StatusPedido.FINALIZADO
                    || p.getItens() == null) {
                continue;
            }
            BigDecimal fatorDesconto = fatorLiquido(p);
            for (ItemPedido item : p.getItens()) {
                if (item == null || item.getQuantidade() <= 0) {
                    continue;
                }
                String chave = item.getProdutoId() != null && !item.getProdutoId().isBlank()
                        ? "id:" + item.getProdutoId()
                        : "nome:" + nomeOu(item.getNomeProduto());
                AcumuladorProduto a = porProduto.computeIfAbsent(chave, k -> new AcumuladorProduto());
                a.produtoId = item.getProdutoId();
                // o nome exibido é o da venda mais recente
                if (a.dataNome == null || p.getDataHoraEntrada().isAfter(a.dataNome)) {
                    a.nome = nomeOu(item.getNomeProduto());
                    a.dataNome = p.getDataHoraEntrada();
                }
                BigDecimal bruto = subtotalItem(item);
                a.quantidade += item.getQuantidade();
                a.bruto = a.bruto.add(bruto);
                a.liquido = a.liquido.add(bruto.multiply(fatorDesconto).setScale(2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal totalLiquido = porProduto.values().stream().map(a -> a.liquido).reduce(ZERO, BigDecimal::add);
        List<AcumuladorProduto> ordenados = new ArrayList<>(porProduto.values());
        ordenados.sort(Comparator.comparingLong((AcumuladorProduto a) -> a.quantidade).reversed()
                .thenComparing((AcumuladorProduto a) -> a.liquido, Comparator.reverseOrder())
                .thenComparing(a -> a.nome, String.CASE_INSENSITIVE_ORDER));

        List<ProdutoRankingDTO> itens = new ArrayList<>();
        for (int i = 0; i < ordenados.size() && i < limite; i++) {
            AcumuladorProduto a = ordenados.get(i);
            BigDecimal participacao = totalLiquido.signum() == 0 ? ZERO
                    : a.liquido.multiply(CEM).divide(totalLiquido, 2, RoundingMode.HALF_UP);
            itens.add(new ProdutoRankingDTO(i + 1, a.produtoId, a.nome, a.quantidade, a.bruto, a.liquido,
                    participacao));
        }
        return new ProdutosMaisVendidosResponse(periodo.paraDTO(), "Itens de pedidos FINALIZADOS", limite,
                ordenados.size(), itens);
    }

    // ------------------------------------------------------------------ auxiliares

    private static Totais totais(List<Pedido> pedidos, FiltroPeriodo periodo) {
        Totais t = new Totais();
        for (Pedido p : seguro(pedidos)) {
            if (!periodo.contem(p.getDataHoraEntrada())) {
                continue;
            }
            t.pedidos++;
            Optional<StatusPedido> status = StatusPedido.interpretar(p.getStatus());
            if (status.isEmpty()) {
                t.naoClassificados++;
                continue;
            }
            switch (status.get()) {
                case FINALIZADO:
                    t.vendas++;
                    t.totalVendas = t.totalVendas.add(valor(p));
                    break;
                case CANCELADO:
                    t.cancelados++;
                    t.valorCancelado = t.valorCancelado.add(valor(p));
                    break;
                default:
                    t.emAndamento++;
                    t.valorEmAndamento = t.valorEmAndamento.add(valor(p));
            }
        }
        return t;
    }

    /** Nulo quando não há vendas (zero daria a impressão de vendas de R$ 0,00). */
    private static BigDecimal ticket(Totais t) {
        return t.vendas == 0 ? null
                : t.totalVendas.divide(BigDecimal.valueOf(t.vendas), 2, RoundingMode.HALF_UP);
    }

    /** Nula quando o período anterior é zero. */
    static BigDecimal variacao(BigDecimal atual, BigDecimal anterior) {
        if (atual == null || anterior == null || anterior.signum() == 0) {
            return null;
        }
        return atual.subtract(anterior).multiply(CEM).divide(anterior, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentual(long parte, long total) {
        if (total == 0) {
            return ZERO;
        }
        return BigDecimal.valueOf(parte).multiply(CEM).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal valor(Pedido p) {
        return p.getValorTotal() == null ? ZERO : p.getValorTotal().setScale(2, RoundingMode.HALF_UP);
    }

    /** valorTotal / subtotal: rateia o desconto do pedido entre os itens no ranking. */
    private static BigDecimal fatorLiquido(Pedido p) {
        if (p.getSubtotal() == null || p.getSubtotal().signum() == 0 || p.getValorTotal() == null) {
            return BigDecimal.ONE;
        }
        return p.getValorTotal().divide(p.getSubtotal(), 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal subtotalItem(ItemPedido item) {
        if (item.getSubtotal() != null) {
            return item.getSubtotal().setScale(2, RoundingMode.HALF_UP);
        }
        if (item.getPrecoUnitario() != null) {
            return item.getPrecoUnitario().multiply(BigDecimal.valueOf(item.getQuantidade()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private static BigDecimal somaVendas(List<PontoVendaDTO> pontos) {
        return pontos.stream().map(PontoVendaDTO::totalVendas).reduce(ZERO, BigDecimal::add);
    }

    private static String nomeOu(String nome) {
        return nome == null || nome.isBlank() ? "Produto sem nome" : nome.trim();
    }

    private static List<Pedido> seguro(List<Pedido> pedidos) {
        if (pedidos == null) {
            return List.of();
        }
        List<Pedido> lista = new ArrayList<>(pedidos.size());
        for (Pedido p : pedidos) {
            if (p != null) {
                lista.add(p);
            }
        }
        return lista;
    }

    private static final class Totais {
        long pedidos;
        long vendas;
        long cancelados;
        long emAndamento;
        long naoClassificados;
        BigDecimal totalVendas = ZERO;
        BigDecimal valorCancelado = ZERO;
        BigDecimal valorEmAndamento = ZERO;
    }

    private static final class Acumulador {
        long pedidos;
        long vendas;
        long cancelados;
        BigDecimal valor = ZERO;
    }

    private static final class AcumuladorProduto {
        String produtoId;
        String nome = "Produto sem nome";
        LocalDateTime dataNome;
        long quantidade;
        BigDecimal bruto = ZERO;
        BigDecimal liquido = ZERO;
    }
}
