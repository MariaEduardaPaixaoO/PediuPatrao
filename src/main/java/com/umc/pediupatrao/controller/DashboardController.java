package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.dashboard.ProdutosMaisVendidosResponse;
import com.umc.pediupatrao.dto.dashboard.ResumoDashboardResponse;
import com.umc.pediupatrao.dto.dashboard.StatusPedidosResponse;
import com.umc.pediupatrao.dto.dashboard.VendasPeriodoResponse;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.dashboard.FiltroPeriodo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API do dashboard (somente GET; ADMIN e GERENTE). Documentação em docs/api-dashboard.md.
 * Datas e limite chegam como texto para que um valor inválido gere a mensagem do FiltroPeriodo
 * em vez do erro genérico de conversão do Spring.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public ResumoDashboardResponse resumo(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        return dashboardService.resumo(filtro(periodo, inicio, fim));
    }

    @GetMapping("/vendas")
    public VendasPeriodoResponse vendas(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        return dashboardService.vendas(filtro(periodo, inicio, fim));
    }

    @GetMapping("/pedidos/status")
    public StatusPedidosResponse statusPedidos(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {
        return dashboardService.statusPedidos(filtro(periodo, inicio, fim));
    }

    @GetMapping("/produtos-mais-vendidos")
    public ProdutosMaisVendidosResponse produtosMaisVendidos(
            @RequestParam(required = false) String periodo,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim,
            @RequestParam(required = false) String limite) {
        return dashboardService.produtosMaisVendidos(filtro(periodo, inicio, fim), limite(limite));
    }

    private FiltroPeriodo filtro(String periodo, String inicio, String fim) {
        return dashboardService.resolverPeriodo(periodo, inicio, fim);
    }

    private static Integer limite(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new NegocioException("O limite do ranking deve ser um número inteiro.");
        }
    }
}
