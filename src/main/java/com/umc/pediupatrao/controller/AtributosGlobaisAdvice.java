package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.StatusPedido;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Disponibiliza para todas as páginas o usuário logado e o perfil, usados apenas para MOSTRAR ou
 * OCULTAR botões e menus. A segurança real está no backend (SecurityConfig e @PreAuthorize).
 */
@ControllerAdvice
public class AtributosGlobaisAdvice {

    private static final Map<String, String> STATUS_DESCRICOES = new LinkedHashMap<>();
    private static final Map<String, String> STATUS_CORES = new LinkedHashMap<>();

    static {
        for (StatusPedido s : StatusPedido.values()) {
            STATUS_DESCRICOES.put(s.name(), s.getDescricao());
        }
        STATUS_CORES.put(StatusPedido.RECEBIDO.name(), "bg-primary");
        STATUS_CORES.put(StatusPedido.EM_PREPARACAO.name(), "bg-warning text-dark");
        STATUS_CORES.put(StatusPedido.PRONTO.name(), "bg-info text-dark");
        STATUS_CORES.put(StatusPedido.SAIU_PARA_ENTREGA.name(), "bg-dark");
        STATUS_CORES.put(StatusPedido.RETIRADO.name(), "bg-dark");
        STATUS_CORES.put(StatusPedido.FINALIZADO.name(), "bg-success");
        STATUS_CORES.put(StatusPedido.CANCELADO.name(), "bg-danger");
    }

    @ModelAttribute
    public void atributos(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean logado = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        boolean admin = logado && possui(auth, "ROLE_ADMIN");
        boolean gerente = logado && possui(auth, "ROLE_GERENTE");
        boolean atendente = logado && possui(auth, "ROLE_ATENDENTE");
        model.addAttribute("usuarioLogado", logado ? auth.getName() : null);
        model.addAttribute("perfilLogado", admin ? "ADMIN" : gerente ? "GERENTE" : atendente ? "ATENDENTE" : null);
        model.addAttribute("ehAdmin", admin);
        model.addAttribute("ehGerente", gerente);
        model.addAttribute("ehAtendente", atendente);
        model.addAttribute("podeVerAuditoria", admin || gerente);
        model.addAttribute("podeVerClientes", gerente || atendente);
        model.addAttribute("podeEditarClientes", atendente);
        model.addAttribute("podeExcluirClientes", gerente);
        model.addAttribute("podeGerenciarProdutos", admin || gerente);
        model.addAttribute("podeRegistrarPedidos", gerente || atendente);
        model.addAttribute("statusDescricoes", STATUS_DESCRICOES);
        model.addAttribute("statusCores", STATUS_CORES);
    }

    private static boolean possui(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
