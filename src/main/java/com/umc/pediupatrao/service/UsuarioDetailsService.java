package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário e atribui a authority correspondente ao perfil armazenado
 * (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE). Usuários legados com role "USER" recebem
 * ATENDENTE; usuários sem perfil reconhecido ficam desabilitados.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByUsername(username)
                .filter(u -> u.getPassword() != null && !u.getPassword().isBlank())
                .map(usuario -> {
                    Optional<Perfil> perfil = Perfil.resolverArmazenado(usuario.getRole());
                    return (UserDetails) User.withUsername(usuario.getUsername())
                            .password(usuario.getPassword())
                            .authorities(perfil
                                    .map(p -> List.of(new SimpleGrantedAuthority(p.getAuthority())))
                                    .orElse(List.of()))
                            .disabled(perfil.isEmpty())
                            .build();
                })
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
