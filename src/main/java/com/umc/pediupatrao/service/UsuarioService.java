package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administração de usuários e perfis (exclusiva do ADMIN, aplicada no backend).
 *
 * Proteções: perfis restritos aos três oficiais (sem mass assignment), nenhum usuário altera o
 * próprio perfil/nome, o último ADMIN não pode ser removido/rebaixado, senhas sempre criptografadas
 * (BCrypt) e fora da auditoria, e sessões do usuário afetado são encerradas após mudanças.
 */
@Service
public class UsuarioService {

    public static final String ENTIDADE = "USUARIO";
    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    public static final int SENHA_MINIMA = 8;
    public static final int SENHA_MAXIMA = 72; // limite do BCrypt

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final UsuarioContexto contexto;
    private final SessionRegistry sessionRegistry;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, UsuarioContexto contexto, SessionRegistry sessionRegistry) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.contexto = contexto;
        this.sessionRegistry = sessionRegistry;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Usuario criar(UsuarioRequest request) {
        if (request == null) {
            throw new NegocioException("Dados do usuário não informados.");
        }
        String username = validarUsername(request.getUsername());
        Perfil perfil = validarPerfil(request.getRole());
        String senha = validarSenha(request.getPassword(), true);
        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflitoException("Já existe um usuário com esse nome de login.");
        }
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(senha));
        usuario.setRole(perfil.name());
        Usuario salvo = usuarioRepository.save(usuario);
        final String id = salvo.getId();
        auditoriaService.registrarComCompensacao(
                () -> usuarioRepository.deleteById(id),
                TipoOperacao.CRIACAO_USUARIO, ENTIDADE, id,
                Diferencas.comparar(null, resumo(salvo.getUsername(), salvo.getRole())), null);
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public Usuario atualizar(String id, UsuarioRequest request) {
        if (request == null) {
            throw new NegocioException("Dados do usuário não informados.");
        }
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        String usernameAnterior = existente.getUsername();
        String roleAnterior = existente.getRole();
        String hashAnterior = existente.getPassword();
        Perfil perfilAnterior = Perfil.resolverArmazenado(roleAnterior).orElse(null);

        String novoUsername = validarUsername(request.getUsername());
        Perfil novoPerfil = validarPerfil(request.getRole());
        String novaSenha = validarSenha(request.getPassword(), false);
        boolean proprio = usernameAnterior.equals(contexto.usuario());

        if (proprio && !novoUsername.equals(usernameAnterior)) {
            throw new NegocioException("Você não pode alterar o seu próprio nome de usuário.");
        }
        if (proprio && novoPerfil != perfilAnterior) {
            throw new NegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (!novoUsername.equals(usernameAnterior) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new ConflitoException("Já existe um usuário com esse nome de login.");
        }
        if (perfilAnterior == Perfil.ADMIN && novoPerfil != Perfil.ADMIN
                && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new ConflitoException("Não é possível rebaixar o último administrador do sistema.");
        }

        Map<String, String> antes = resumo(usernameAnterior, roleAnterior);
        existente.setUsername(novoUsername);
        existente.setRole(novoPerfil.name());
        if (novaSenha != null) {
            existente.setPassword(passwordEncoder.encode(novaSenha));
        }
        Map<String, String> depois = resumo(novoUsername, novoPerfil.name());
        if (novaSenha != null) {
            depois.put("autenticacao", "senha redefinida"); // sem registrar qualquer valor da senha
        }
        var alteracoes = Diferencas.comparar(antes, depois);
        if (alteracoes.isEmpty()) {
            return existente;
        }
        Usuario salvo = usuarioRepository.save(existente);
        final Usuario restauracao = new Usuario();
        restauracao.setId(salvo.getId());
        restauracao.setUsername(usernameAnterior);
        restauracao.setRole(roleAnterior);
        restauracao.setPassword(hashAnterior);
        auditoriaService.registrarComCompensacao(
                () -> usuarioRepository.save(restauracao),
                TipoOperacao.ALTERACAO_USUARIO, ENTIDADE, salvo.getId(), alteracoes, null);

        boolean perfilMudou = perfilAnterior != novoPerfil;
        if (perfilMudou || (novaSenha != null && !proprio)) {
            expirarSessoes(usernameAnterior); // força novo login com as novas permissões/credenciais
        }
        return salvo;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void excluir(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(contexto.usuario())) {
            throw new NegocioException("Você não pode excluir o seu próprio usuário.");
        }
        Perfil perfil = Perfil.resolverArmazenado(existente.getRole()).orElse(null);
        if (perfil == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new ConflitoException("Não é possível excluir o último administrador do sistema.");
        }
        final Usuario copia = new Usuario();
        copia.setId(existente.getId());
        copia.setUsername(existente.getUsername());
        copia.setRole(existente.getRole());
        copia.setPassword(existente.getPassword());
        usuarioRepository.deleteById(id);
        auditoriaService.registrarComCompensacao(
                () -> usuarioRepository.save(copia),
                TipoOperacao.EXCLUSAO_USUARIO, ENTIDADE, id,
                Diferencas.comparar(resumo(copia.getUsername(), copia.getRole()), null), null);
        expirarSessoes(copia.getUsername());
    }

    // ------------------------------------------------------------------ auxiliares

    private String validarUsername(String valor) {
        String username = valor == null ? "" : valor.trim();
        if (!USERNAME_VALIDO.matcher(username).matches()) {
            throw new NegocioException("O login deve ter de 3 a 50 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        return username;
    }

    private Perfil validarPerfil(String valor) {
        return Perfil.interpretar(valor).orElseThrow(() ->
                new NegocioException("Perfil inválido. Perfis permitidos: ADMIN, GERENTE ou ATENDENTE."));
    }

    /** Retorna a senha validada; em edição, senha vazia significa "manter a atual" (retorna null). */
    private String validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new NegocioException("A senha é obrigatória.");
            }
            return null;
        }
        if (senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new NegocioException("A senha deve ter entre " + SENHA_MINIMA + " e " + SENHA_MAXIMA + " caracteres.");
        }
        return senha;
    }

    private static Map<String, String> resumo(String username, String role) {
        Map<String, String> m = new LinkedHashMap<>();
        Diferencas.put(m, "username", username);
        Diferencas.put(m, "perfil", role);
        return m;
    }

    private void expirarSessoes(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails detalhes && detalhes.getUsername().equals(username)) {
                for (SessionInformation sessao : sessionRegistry.getAllSessions(principal, false)) {
                    sessao.expireNow();
                }
            }
        }
    }
}
