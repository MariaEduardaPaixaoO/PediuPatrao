package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioContexto;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Páginas: login, início, usuários, clientes, produtos e configurações.
 * (Pedidos e auditoria possuem controladores próprios.)
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;
    private final UsuarioContexto contexto;

    public HomeController(ClienteService clienteService, ProdutoService produtoService,
            UsuarioService usuarioService, UsuarioContexto contexto) {
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
        this.contexto = contexto;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        prepararFormularioUsuario(model, new UsuarioRequest(), null, false);
        return "layout";
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Usuario> encontrado = usuarioService.buscarPorId(id);
        if (encontrado.isEmpty()) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }
        Usuario u = encontrado.get();
        UsuarioRequest form = new UsuarioRequest();
        form.setUsername(u.getUsername());
        form.setRole(Perfil.resolverArmazenado(u.getRole()).map(Perfil::name).orElse(""));
        prepararFormularioUsuario(model, form, u.getId(), u.getUsername().equals(contexto.usuario()));
        return "layout";
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute UsuarioRequest usuario, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.criar(usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
            return "redirect:/usuarios";
        } catch (NegocioException | AuditoriaException e) {
            usuario.setPassword(null);
            prepararFormularioUsuario(model, usuario, null, false);
            model.addAttribute("erro", e.getMessage());
            return "layout";
        }
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id, @ModelAttribute UsuarioRequest usuario,
            Model model, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizar(id, usuario);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
            return "redirect:/usuarios";
        } catch (NegocioException | AuditoriaException e) {
            usuario.setPassword(null);
            boolean proprio = usuarioService.buscarPorId(id)
                    .map(u -> u.getUsername().equals(contexto.usuario())).orElse(false);
            prepararFormularioUsuario(model, usuario, id, proprio);
            model.addAttribute("erro", e.getMessage());
            return "layout";
        }
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            usuarioService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (NegocioException | AuditoriaException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private void prepararFormularioUsuario(Model model, UsuarioRequest form, String usuarioId, boolean proprio) {
        model.addAttribute("usuario", form);
        model.addAttribute("usuarioId", usuarioId);
        model.addAttribute("editandoProprio", proprio);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
    }

    // ========================
    // PRODUTOS
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        List<Produto> produtos = produtoService.listarProdutos();
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    // ========================
    // CLIENTES
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        model.addAttribute("content", "clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        model.addAttribute("clienteId", null);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.criar(cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
            return "redirect:/clientes";
        } catch (NegocioException | AuditoriaException e) {
            model.addAttribute("cliente", cliente);
            model.addAttribute("clienteId", null);
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("content", "clientes/form :: content");
            return "layout";
        }
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Optional<Cliente> encontrado = clienteService.buscarPorId(id);
        if (encontrado.isEmpty()) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }
        model.addAttribute("cliente", encontrado.get());
        model.addAttribute("clienteId", id);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id, @ModelAttribute Cliente cliente, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.atualizar(id, cliente);
            redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
            return "redirect:/clientes";
        } catch (NegocioException | AuditoriaException e) {
            model.addAttribute("cliente", cliente);
            model.addAttribute("clienteId", id);
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("content", "clientes/form :: content");
            return "layout";
        }
    }

    // ========================
    // OUTRAS ROTAS
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
