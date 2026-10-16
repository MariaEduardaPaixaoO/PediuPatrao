package com.umc.pediupatrao.support;

import com.umc.pediupatrao.entity.Perfil;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilitário para simular o usuário autenticado nos testes de serviço. */
public final class ContextoTeste {

    private ContextoTeste() {
    }

    public static void autenticar(String username, Perfil perfil) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                username, "n/a", List.of(new SimpleGrantedAuthority(perfil.getAuthority()))));
        SecurityContextHolder.setContext(contexto);
    }

    public static void limpar() {
        SecurityContextHolder.clearContext();
    }
}
