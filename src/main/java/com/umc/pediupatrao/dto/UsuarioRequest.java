package com.umc.pediupatrao.dto;

/**
 * Dados aceitos para criar/alterar usuários. Somente estes campos podem ser definidos pelo cliente
 * (não há vínculo direto com a entidade, evitando "mass assignment").
 */
public class UsuarioRequest {

    private String username;
    private String password;
    private String role;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
