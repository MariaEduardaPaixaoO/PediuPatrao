package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.service.dashboard.FiltroPeriodo;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;

/** @PreAuthorize do DashboardService, independente das regras de rota (como no AutorizacaoServicosTest). */
@SpringBootTest(classes = {DashboardService.class, DashboardAutorizacaoTest.Config.class})
@SuppressWarnings("deprecation")
class DashboardAutorizacaoTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class Config {
        @Bean
        Clock clock() {
            return Clock.system(ZoneId.of("America/Sao_Paulo"));
        }
    }

    @MockBean
    private PedidoRepository pedidoRepository;

    @Autowired
    private DashboardService dashboardService;

    private FiltroPeriodo hoje() {
        return dashboardService.resolverPeriodo("HOJE", null, null);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaODashboard() {
        FiltroPeriodo f = hoje();
        assertThrows(AccessDeniedException.class, () -> dashboardService.resumo(f));
        assertThrows(AccessDeniedException.class, () -> dashboardService.vendas(f));
        assertThrows(AccessDeniedException.class, () -> dashboardService.statusPedidos(f));
        assertThrows(AccessDeniedException.class, () -> dashboardService.produtosMaisVendidos(f, 10));
    }

    @Test
    void semUsuarioAutenticadoNaoConsulta() {
        FiltroPeriodo f = hoje();
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> dashboardService.resumo(f));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsulta() {
        FiltroPeriodo f = hoje();
        assertNotNull(dashboardService.resumo(f));
        assertNotNull(dashboardService.vendas(f));
        assertNotNull(dashboardService.statusPedidos(f));
        assertNotNull(dashboardService.produtosMaisVendidos(f, 10));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsulta() {
        assertNotNull(dashboardService.resumo(hoje()));
    }
}
