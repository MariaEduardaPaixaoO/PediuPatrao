package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de administração de usuários: acesso exclusivo do ADMIN (SecurityConfig + @PreAuthorize).
 * Nenhuma resposta inclui senha/hash.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public List<UsuarioResponse> listarTodos() {
        return usuarioService.listarTodos().stream().map(UsuarioResponse::de).toList();
    }

    @GetMapping("/{id}")
    public UsuarioResponse buscarPorId(@PathVariable String id) {
        Usuario usuario = usuarioService.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("Usuário não encontrado."));
        return UsuarioResponse.de(usuario);
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criarUsuario(@RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(usuarioService.criar(request)));
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizarUsuario(@PathVariable String id, @RequestBody UsuarioRequest request) {
        return UsuarioResponse.de(usuarioService.atualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
