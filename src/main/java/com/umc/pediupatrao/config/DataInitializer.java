package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Preparação segura dos usuários iniciais.
 *
 * 1) Migração: o usuário legado "admin" (sem perfil) vira ADMIN; usuários legados com role
 *    "USER" ou sem role passam a ATENDENTE (menor privilégio).
 * 2) Se não existir nenhum ADMIN, cria o administrador inicial usando as variáveis de ambiente
 *    PEDIU_ADMIN_USERNAME / PEDIU_ADMIN_PASSWORD (nenhuma senha fica no código ou no repositório).
 * 3) Opcionalmente (PEDIU_SEED_ENABLED=true) cria os usuários de demonstração gerente01 e
 *    atendente01, cujas senhas vêm de PEDIU_SEED_GERENTE_PASSWORD / PEDIU_SEED_ATENDENTE_PASSWORD.
 * As senhas nunca são escritas em log.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final int SENHA_MINIMA = 8;

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, PasswordEncoder encoder,
            @Value("${pediupatrao.admin.username:admin}") String adminUsername,
            @Value("${pediupatrao.admin.password:}") String adminPassword,
            @Value("${pediupatrao.seed.enabled:false}") boolean seedHabilitado,
            @Value("${pediupatrao.seed.gerente-password:}") String senhaGerente,
            @Value("${pediupatrao.seed.atendente-password:}") String senhaAtendente) {
        return args -> {
            migrarUsuariosLegados(repo, adminUsername);

            if (repo.countByRole(Perfil.ADMIN.name()) == 0) {
                criarSeValido(repo, encoder, adminUsername, adminPassword, Perfil.ADMIN);
                if (repo.countByRole(Perfil.ADMIN.name()) == 0) {
                    log.warn("Nenhum usuário ADMIN existe. Defina PEDIU_ADMIN_USERNAME e PEDIU_ADMIN_PASSWORD "
                            + "(mínimo de {} caracteres) para criar o administrador inicial.", SENHA_MINIMA);
                }
            }

            if (seedHabilitado) {
                criarSeValido(repo, encoder, "gerente01", senhaGerente, Perfil.GERENTE);
                criarSeValido(repo, encoder, "atendente01", senhaAtendente, Perfil.ATENDENTE);
            }
        };
    }

    private void migrarUsuariosLegados(UsuarioRepository repo, String adminUsername) {
        for (Usuario u : repo.findAll()) {
            String role = u.getRole();
            Perfil novo;
            if (role == null || role.isBlank()) {
                boolean adminLegado = "admin".equals(u.getUsername()) || adminUsername.equals(u.getUsername());
                novo = adminLegado ? Perfil.ADMIN : Perfil.ATENDENTE;
            } else if ("USER".equalsIgnoreCase(role.trim())) {
                novo = Perfil.ATENDENTE;
            } else {
                continue; // perfil já definido (ou desconhecido: permanece desabilitado até o ADMIN corrigir)
            }
            u.setRole(novo.name());
            repo.save(u);
            log.info("Usuário legado '{}' migrado para o perfil {}.", u.getUsername(), novo);
        }
    }

    private void criarSeValido(UsuarioRepository repo, PasswordEncoder encoder, String username,
            String senha, Perfil perfil) {
        if (repo.existsByUsername(username)) {
            return;
        }
        if (senha == null || senha.length() < SENHA_MINIMA) {
            log.warn("Usuário '{}' não criado: senha não informada ou com menos de {} caracteres.",
                    username, SENHA_MINIMA);
            return;
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(encoder.encode(senha));
        usuario.setRole(perfil.name());
        repo.save(usuario);
        log.info("Usuário '{}' criado com o perfil {}.", username, perfil);
    }
}
