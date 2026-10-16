package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Página de consulta da auditoria (somente leitura; ADMIN e GERENTE).
 */
@Controller
public class AuditoriaViewController {

    private final AuditoriaService auditoriaService;

    public AuditoriaViewController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String consultar(
            @RequestParam(required = false) String entidadeTipo,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String operacao,
            Model model) {
        TipoOperacao tipoOperacao = null;
        if (operacao != null && !operacao.isBlank()) {
            try {
                tipoOperacao = TipoOperacao.valueOf(operacao.trim());
            } catch (IllegalArgumentException e) {
                model.addAttribute("erro", "Tipo de operação inválido.");
            }
        }
        List<AuditoriaEvento> eventos = auditoriaService.consultar(
                entidadeTipo, entidadeId, usuario, tipoOperacao, AuditoriaService.LIMITE_PADRAO);
        model.addAttribute("eventos", eventos);
        model.addAttribute("filtroEntidadeTipo", entidadeTipo == null ? "" : entidadeTipo);
        model.addAttribute("filtroEntidadeId", entidadeId == null ? "" : entidadeId);
        model.addAttribute("filtroUsuario", usuario == null ? "" : usuario);
        model.addAttribute("filtroOperacao", operacao == null ? "" : operacao);
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("limite", AuditoriaService.LIMITE_PADRAO);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
