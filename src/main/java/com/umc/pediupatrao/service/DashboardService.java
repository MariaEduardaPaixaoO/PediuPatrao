package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.dashboard.ProdutosMaisVendidosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.StatusPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPeriodoResponse;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.dashboard.DashboardCalculadora;
import com.umc.pediupatrao.service.dashboard.FiltroPeriodo;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Consultas do dashboard administrativo (somente leitura; ADMIN e GERENTE). */
@Service
public class DashboardService {

    public static final int LIMITE_RANKING_PADRAO = 10;
    public static final int LIMITE_RANKING_MAXIMO = 50;

    private final PedidoRepository pedidoRepository;
    private final Clock clock;

    public DashboardService(PedidoRepository pedidoRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clock = clock;
    }

    public LocalDate hoje() {
        return LocalDate.now(clock);
    }

    public FiltroPeriodo resolverPeriodo(String periodo, String inicio, String fim) {
        return FiltroPeriodo.resolver(periodo, inicio, fim, hoje());
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResumoDashboardResponse resumo(FiltroPeriodo periodo) {
        FiltroPeriodo anterior = periodo.anterior();
        return DashboardCalculadora.resumo(buscar(periodo), periodo, buscar(anterior), anterior,
                LocalDateTime.now(clock));
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public VendasPeriodoResponse vendas(FiltroPeriodo periodo) {
        FiltroPeriodo anterior = periodo.anterior();
        return DashboardCalculadora.vendas(buscar(periodo), periodo, buscar(anterior), anterior);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public StatusPedidosResponse statusPedidos(FiltroPeriodo periodo) {
        return DashboardCalculadora.status(buscar(periodo), periodo);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ProdutosMaisVendidosResponse produtosMaisVendidos(FiltroPeriodo periodo, Integer limite) {
        int n = limite == null ? LIMITE_RANKING_PADRAO : limite;
        if (n < 1 || n > LIMITE_RANKING_MAXIMO) {
            throw new NegocioException("O limite do ranking deve estar entre 1 e " + LIMITE_RANKING_MAXIMO + ".");
        }
        return DashboardCalculadora.produtosMaisVendidos(buscar(periodo), periodo, n);
    }

    private List<Pedido> buscar(FiltroPeriodo periodo) {
        return pedidoRepository.buscarParaDashboard(periodo.inicioInclusivo(), periodo.fimExclusivo());
    }
}
