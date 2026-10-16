package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.NaoEncontradoException;
import com.umc.pediupatrao.exception.NegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Catálogo de produtos. Consulta: ADMIN, GERENTE e ATENDENTE. Manutenção: ADMIN e GERENTE.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;

    public ProdutoService(ProdutoRepository produtoRepository) {
        this.produtoRepository = produtoRepository;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Produto novoProduto(Produto produto) {
        produto.setId(null);
        validar(produto);
        return produtoRepository.save(produto);
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public void excluir(String id) {
        produtoRepository.deleteById(id);
    }

    /** Salva um novo produto (sem id) ou atualiza um existente. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Produto salvar(Produto produto) {
        validar(produto);
        if (produto.getId() == null) {
            if (produto.getAtivo() == null) {
                produto.setAtivo(Boolean.TRUE);
            }
            return produtoRepository.save(produto);
        }
        if (!produtoRepository.existsById(produto.getId())) {
            throw new NaoEncontradoException("Produto não encontrado para atualização.");
        }
        return produtoRepository.save(produto);
    }

    private void validar(Produto p) {
        if (p.getNome() == null || p.getNome().isBlank()) {
            throw new NegocioException("O nome do produto é obrigatório.");
        }
        if (p.getPreco() == null || p.getPreco() <= 0) {
            throw new NegocioException("O preço do produto deve ser maior que zero.");
        }
    }
}
