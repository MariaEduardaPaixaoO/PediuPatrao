package com.umc.pediupatrao.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Autenticação: cada perfil recebe a authority correta (e somente ela).
 */
class UsuarioDetailsServiceTest {

    private final UsuarioRepository repo = mock(UsuarioRepository.class);
    private final UsuarioDetailsService service = new UsuarioDetailsService(repo);

    @Test
    void atribuiAuthorityDeCadaUmDosTresPerfis() {
        assertEquals(Set.of("ROLE_ADMIN"), authorities("admin", "ADMIN"));
        assertEquals(Set.of("ROLE_GERENTE"), authorities("gerente01", "GERENTE"));
        assertEquals(Set.of("ROLE_ATENDENTE"), authorities("atendente01", "ATENDENTE"));
    }

    @Test
    void usuarioLegadoComRoleUserViraAtendenteDeMenorPrivilegio() {
        assertEquals(Set.of("ROLE_ATENDENTE"), authorities("legado", "USER"));
    }

    @Test
    void usuarioSemPerfilReconhecidoFicaDesabilitadoESemPermissoes() {
        for (String role : new String[] {null, "", "SUPERUSER", "ROLE_ADMIN"}) {
            Usuario u = usuario("semperfil", role);
            when(repo.findByUsername("semperfil")).thenReturn(Optional.of(u));
            UserDetails d = service.loadUserByUsername("semperfil");
            assertFalse(d.isEnabled(), "Role inválida não pode autenticar: " + role);
            assertTrue(d.getAuthorities().isEmpty());
        }
    }

    @Test
    void usuarioInexistenteLancaExcecao() {
        when(repo.findByUsername("fantasma")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("fantasma"));
    }

    private Set<String> authorities(String username, String role) {
        when(repo.findByUsername(username)).thenReturn(Optional.of(usuario(username, role)));
        UserDetails d = service.loadUserByUsername(username);
        assertTrue(d.isEnabled());
        return d.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toSet());
    }

    private static Usuario usuario(String username, String role) {
        Usuario u = new Usuario();
        u.setUsername(username);
        u.setPassword("$2a$10$hashqualquerparateste");
        u.setRole(role);
        return u;
    }
}
