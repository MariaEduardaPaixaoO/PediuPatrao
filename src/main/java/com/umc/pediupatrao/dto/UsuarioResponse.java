package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Usuario;

/**
 * Representação pública de um usuário: nunca inclui a senha (nem seu hash).
 */
public class UsuarioResponse {

    private final String id;
    private final String username;
    private final String role;

    public UsuarioResponse(String id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public static UsuarioResponse de(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getRole());
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
}
