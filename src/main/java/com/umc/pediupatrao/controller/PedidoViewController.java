package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioContexto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Páginas de pedidos. Os botões exibidos dependem do perfil, mas a autorização efetiva está na
 * SecurityConfig e nos @PreAuthorize do PedidoService. Valores, responsáveis e horários nunca vêm
 * do formulário: o formulário envia apenas cliente, tipo, produtos e quantidades.
 */
@Controller
public class PedidoViewController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioContexto contexto;

    public PedidoViewController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService, UsuarioContexto contexto) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.contexto = contexto;
    }

    @GetMapping("/pedidos")
    public String listar(@RequestParam(required = false) String status, Model model) {
        List<Pedido> pedidos = pedidoService.listarPedidos();
        Optional<StatusPedido> filtro = StatusPedido.interpretar(status);
        if (filtro.isPresent()) {
            pedidos = pedidos.stream().filter(p -> filtro.get().name().equals(p.getStatus())).toList();
        }
        model.addAttribute("pedidos", pedidos);
        model.addAttribute("statusFiltro", filtro.map(StatusPedido::name).orElse(""));
        model.addAttribute("statusOpcoes", StatusPedido.values());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    public String novo(Model model) {
        prepararFormulario(model, null);
        return "layout";
    }

    @PostMapping("/pedidos/salvar")
    public String salvar(@RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(name = "produtoId", required = false) List<String> produtoIds,
            @RequestParam(name = "quantidade", required = false) List<String> quantidades,
            Model model, RedirectAttributes redirectAttributes) {
        try {
            Pedido criado = pedidoService.criarPedido(montarRequest(clienteId, tipoEntrega, produtoIds, quantidades));
            redirectAttributes.addFlashAttribute("sucesso", "Pedido registrado com sucesso!");
            return "redirect:/pedidos/" + criado.getId();
        } catch (NegocioException | AuditoriaException | OptimisticLockingFailureException e) {
            prepararFormulario(model, null);
            model.addAttribute("erro", mensagem(e));
            model.addAttribute("clienteSelecionado", clienteId);
            model.addAttribute("tipoSelecionado", tipoEntrega);
            return "layout";
        }
    }

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Pedido pedido;
        try {
            pedido = pedidoService.buscarPorId(id);
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        boolean gerente = contexto.possuiPerfil(Perfil.GERENTE);
        boolean operador = gerente || contexto.possuiPerfil(Perfil.ATENDENTE);
        Optional<StatusPedido> atual = StatusPedido.interpretar(pedido.getStatus());

        StatusPedido proximo = null;
        if (atual.isPresent() && pedido.getTipoEntrega() != null) {
            proximo = atual.get().proximos(pedido.getTipoEntrega()).stream().findFirst().orElse(null);
        }
        boolean antesDaSaida = atual.map(StatusPedido::antesDaSaida).orElse(false);

        model.addAttribute("pedido", pedido);
        model.addAttribute("proximoStatus", proximo);
        model.addAttribute("proximaEhSaida", proximo != null && proximo.saida());
        model.addAttribute("podeAvancar", operador && proximo != null);
        model.addAttribute("podeEditar", operador && atual.map(s -> s == StatusPedido.RECEBIDO).orElse(false));
        model.addAttribute("podeDesconto", gerente && antesDaSaida);
        model.addAttribute("podeCancelar", gerente && antesDaSaida);
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    @GetMapping("/pedidos/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.buscarPorId(id);
            prepararFormulario(model, pedido);
            model.addAttribute("clienteSelecionado", pedido.getClienteId());
            model.addAttribute("tipoSelecionado", pedido.getTipoEntrega() == null ? null : pedido.getTipoEntrega().name());
            return "layout";
        } catch (NegocioException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
    }

    @PostMapping("/pedidos/{id}/editar")
    public String atualizar(@PathVariable String id,
            @RequestParam(required = false) String clienteId,
            @RequestParam(required = false) String tipoEntrega,
            @RequestParam(name = "produtoId", required = false) List<String> produtoIds,
            @RequestParam(name = "quantidade", required = false) List<String> quantidades,
            RedirectAttributes redirectAttributes) {
        return executar(redirectAttributes, id, "Pedido atualizado com sucesso!", () ->
                pedidoService.atualizarPedido(id, montarRequest(clienteId, tipoEntrega, produtoIds, quantidades)));
    }

    @PostMapping("/pedidos/{id}/status")
    public String status(@PathVariable String id, @RequestParam String status, RedirectAttributes redirectAttributes) {
        return executar(redirectAttributes, id, "Status atualizado com sucesso!",
                () -> pedidoService.atualizarStatus(id, status));
    }

    @PostMapping("/pedidos/{id}/saida")
    public String saida(@PathVariable String id, RedirectAttributes redirectAttributes) {
        return executar(redirectAttributes, id, "Saída do pedido registrada com sucesso!",
                () -> pedidoService.registrarSaida(id));
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String desconto(@PathVariable String id, @RequestParam String percentual,
            RedirectAttributes redirectAttributes) {
        return executar(redirectAttributes, id, "Desconto aplicado com sucesso!",
                () -> pedidoService.aplicarDesconto(id, lerPercentual(percentual)));
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        return executar(redirectAttributes, id, "Pedido cancelado.",
                () -> pedidoService.cancelar(id, justificativa));
    }

    // ------------------------------------------------------------------ auxiliares

    private String executar(RedirectAttributes redirectAttributes, String id, String sucesso, Runnable acao) {
        try {
            acao.run();
            redirectAttributes.addFlashAttribute("sucesso", sucesso);
        } catch (NegocioException | AuditoriaException | OptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("erro", mensagem(e));
        }
        return "redirect:/pedidos/" + id;
    }

    private static String mensagem(RuntimeException e) {
        if (e instanceof OptimisticLockingFailureException) {
            return "O pedido foi alterado por outro usuário. Recarregue a página e tente novamente.";
        }
        return e.getMessage();
    }

    private void prepararFormulario(Model model, Pedido pedido) {
        List<Produto> ativos = produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()))
                .toList();
        model.addAttribute("pedidoEdicao", pedido);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", ativos);
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
    }

    private PedidoRequest montarRequest(String clienteId, String tipoEntrega,
            List<String> produtoIds, List<String> quantidades) {
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(clienteId);
        request.setTipoEntrega(tipoEntrega);
        List<ItemPedidoRequest> itens = new ArrayList<>();
        int total = Math.max(produtoIds == null ? 0 : produtoIds.size(), quantidades == null ? 0 : quantidades.size());
        for (int i = 0; i < total; i++) {
            String produto = produtoIds != null && i < produtoIds.size() ? produtoIds.get(i) : null;
            String qtd = quantidades != null && i < quantidades.size() ? quantidades.get(i) : null;
            boolean linhaVazia = (produto == null || produto.isBlank()) && (qtd == null || qtd.isBlank());
            if (linhaVazia) {
                continue;
            }
            Integer quantidade;
            try {
                quantidade = qtd == null || qtd.isBlank() ? null : Integer.valueOf(qtd.trim());
            } catch (NumberFormatException e) {
                throw new NegocioException("Quantidade inválida: " + qtd);
            }
            itens.add(new ItemPedidoRequest(produto, quantidade));
        }
        request.setItens(itens);
        return request;
    }

    private static BigDecimal lerPercentual(String texto) {
        try {
            return new BigDecimal(texto.trim().replace(',', '.'));
        } catch (NumberFormatException | NullPointerException e) {
            throw new NegocioException("Percentual de desconto inválido.");
        }
    }
}
