package com.umc.pediupatrao.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * Perfis de acesso do sistema.
 */
public enum Perfil {
    ADMIN("Administrador"),
    GERENTE("Gerente"),
    ATENDENTE("Atendente");

    private final String descricao;

    Perfil(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Nome da authority usada pelo Spring Security (ex.: ROLE_GERENTE). */
    public String getAuthority() {
        return "ROLE_" + name();
    }

    /**
     * Interpretação ESTRITA de um valor recebido de formulários/APIs.
     * Somente os três perfis oficiais são aceitos (o legado "USER" não é aceito para novas atribuições).
     */
    public static Optional<Perfil> interpretar(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Perfil.valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Interpreta o perfil já armazenado no banco. Usuários legados com role "USER"
     * recebem o perfil de menor privilégio (ATENDENTE). Valores nulos ou desconhecidos
     * resultam em ausência de perfil (o usuário não consegue autenticar).
     */
    public static Optional<Perfil> resolverArmazenado(String roleArmazenada) {
        if (roleArmazenada != null && "USER".equalsIgnoreCase(roleArmazenada.trim())) {
            return Optional.of(ATENDENTE);
        }
        return interpretar(roleArmazenada);
    }
}
