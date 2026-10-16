package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.ConflitoException;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de clientes. Toda inclusão, alteração e exclusão gera um evento de auditoria com os
 * valores anteriores e novos (com destaque para telefone e endereço).
 *
 * Permissões: consulta (GERENTE, ATENDENTE); inclusão/alteração (ATENDENTE); exclusão (GERENTE).
 */
@Service
public class ClienteService {

    public static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    @Transactional
    public Cliente criar(Cliente dados) {
        if (dados == null) {
            throw new NegocioException("Dados do cliente não informados.");
        }
        Cliente novo = new Cliente();
        copiarCampos(dados, novo, null);
        validar(novo);
        novo.setId(null); // nunca aceitar identificador vindo do cliente HTTP
        Cliente salvo = clienteRepository.save(novo);
        final String id = salvo.getId();
        auditoriaService.registrarComCompensacao(
                () -> clienteRepository.deleteById(id),
                TipoOperacao.CRIACAO_CLIENTE, ENTIDADE, id,
                Diferencas.comparar(null, resumo(salvo)), null);
        return salvo;
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    @Transactional
    public Cliente atualizar(String id, Cliente dados) {
        if (dados == null) {
            throw new NegocioException("Dados do cliente não informados.");
        }
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Cliente não encontrado para atualização."));
        Cliente anterior = copia(existente);
        Map<String, String> antes = resumo(anterior);

        copiarCampos(dados, existente, anterior);
        validar(existente);

        var alteracoes = Diferencas.comparar(antes, resumo(existente));
        if (alteracoes.isEmpty()) {
            return existente; // nada mudou: sem gravação e sem evento
        }
        Cliente salvo = clienteRepository.save(existente);
        auditoriaService.registrarComCompensacao(
                () -> clienteRepository.save(anterior),
                TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), alteracoes, null);
        return salvo;
    }

    /** Compatibilidade com chamadas anteriores: sem id cria, com id atualiza. */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente cliente) {
        if (cliente.getId() == null || cliente.getId().isBlank()) {
            return criar(cliente);
        }
        return atualizar(cliente.getId(), cliente);
    }

    @PreAuthorize("hasRole('GERENTE')")
    @Transactional
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new NaoEncontradoException("Cliente não encontrado."));
        if (pedidoRepository.existsByClienteId(id)) {
            throw new ConflitoException("O cliente possui pedidos registrados e não pode ser excluído "
                    + "(os dados históricos dos pedidos são preservados).");
        }
        Cliente copia = copia(existente);
        Map<String, String> antes = resumo(copia);
        clienteRepository.deleteById(id);
        auditoriaService.registrarComCompensacao(
                () -> clienteRepository.save(copia),
                TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id,
                Diferencas.comparar(antes, null), null);
    }

    // ------------------------------------------------------------------ validações / auxiliares

    private void copiarCampos(Cliente origem, Cliente destino, Cliente anterior) {
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        String uf = limpar(origem.getEstado());
        destino.setEstado(uf == null ? null : uf.toUpperCase());
        // Campo legado: se não vier na requisição, preserva o valor existente.
        String legado = limpar(origem.getEndereco());
        destino.setEndereco(legado != null ? legado : (anterior != null ? anterior.getEndereco() : null));
    }

    private void validar(Cliente c) {
        if (c.getNome() == null) {
            throw new NegocioException("O nome do cliente é obrigatório.");
        }
        if (c.getNome().length() > 120) {
            throw new NegocioException("O nome do cliente deve ter no máximo 120 caracteres.");
        }
        if (c.getTelefone() == null) {
            throw new NegocioException("O telefone do cliente é obrigatório.");
        }
        int digitos = c.getTelefone().replaceAll("\\D", "").length();
        if (digitos < 10 || digitos > 11) {
            throw new NegocioException("Telefone inválido: informe DDD e número (10 ou 11 dígitos).");
        }
        if (c.getCep() != null && c.getCep().replaceAll("\\D", "").length() != 8) {
            throw new NegocioException("CEP inválido: informe 8 dígitos.");
        }
        if (c.getEstado() != null && !c.getEstado().matches("[A-Z]{2}")) {
            throw new NegocioException("Estado inválido: informe a sigla com 2 letras (ex.: SP).");
        }
    }

    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }

    private static Cliente copia(Cliente c) {
        Cliente n = new Cliente();
        n.setId(c.getId());
        n.setNome(c.getNome());
        n.setTelefone(c.getTelefone());
        n.setCep(c.getCep());
        n.setLogradouro(c.getLogradouro());
        n.setNumero(c.getNumero());
        n.setComplemento(c.getComplemento());
        n.setBairro(c.getBairro());
        n.setCidade(c.getCidade());
        n.setEstado(c.getEstado());
        n.setEndereco(c.getEndereco());
        return n;
    }

    static Map<String, String> resumo(Cliente c) {
        Map<String, String> m = new LinkedHashMap<>();
        Diferencas.put(m, "nome", c.getNome());
        Diferencas.put(m, "telefone", c.getTelefone());
        Diferencas.put(m, "cep", c.getCep());
        Diferencas.put(m, "logradouro", c.getLogradouro());
        Diferencas.put(m, "numero", c.getNumero());
        Diferencas.put(m, "complemento", c.getComplemento());
        Diferencas.put(m, "bairro", c.getBairro());
        Diferencas.put(m, "cidade", c.getCidade());
        Diferencas.put(m, "estado", c.getEstado());
        Diferencas.put(m, "endereco", c.getEndereco());
        return m;
    }
}
