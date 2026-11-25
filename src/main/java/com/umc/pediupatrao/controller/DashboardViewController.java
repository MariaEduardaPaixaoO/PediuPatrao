package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.dashboard.FiltroPeriodo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Página do dashboard. Os números são carregados pelo navegador a partir de /api/dashboard. */
@Controller
public class DashboardViewController {

    private final DashboardService dashboardService;

    public DashboardViewController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("hoje", dashboardService.hoje());
        model.addAttribute("maximoDias", FiltroPeriodo.MAXIMO_DIAS);
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
