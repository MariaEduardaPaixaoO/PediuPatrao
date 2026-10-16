package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria (somente leitura; ADMIN e GERENTE). Não há endpoints de
 * criação, alteração ou exclusão de eventos.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public List<AuditoriaEvento> consultar(
            @RequestParam(required = false) String entidadeTipo,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao operacao,
            @RequestParam(required = false) Integer limite) {
        return auditoriaService.consultar(entidadeTipo, entidadeId, usuario, operacao, limite);
    }
}
