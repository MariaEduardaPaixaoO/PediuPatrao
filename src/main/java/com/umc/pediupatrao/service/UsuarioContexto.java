package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso ao usuário autenticado. É a ÚNICA fonte de identidade para registros de responsável e
 * de auditoria: nenhum valor de usuário enviado em requisições é considerado.
 */
@Component
public class UsuarioContexto {

    private Authentication autenticacao() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Usuário não autenticado.");
        }
        return auth;
    }

    public String usuario() {
        return autenticacao().getName();
    }

    public boolean possuiPerfil(Perfil perfil) {
        return autenticacao().getAuthorities().stream()
                .anyMatch(a -> perfil.getAuthority().equals(a.getAuthority()));
    }

    /** Perfil do usuário autenticado (ADMIN, GERENTE ou ATENDENTE); "SEM_PERFIL" se nenhum. */
    public String perfil() {
        Authentication auth = autenticacao();
        for (Perfil p : Perfil.values()) {
            boolean tem = auth.getAuthorities().stream()
                    .anyMatch(a -> p.getAuthority().equals(a.getAuthority()));
            if (tem) {
                return p.name();
            }
        }
        return "SEM_PERFIL";
    }
}
