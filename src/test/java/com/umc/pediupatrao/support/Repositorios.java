package com.umc.pediupatrao.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repositórios "em memória" (mocks do Mockito apoiados em mapas) para testar as regras de negócio
 * sem depender de um servidor MongoDB. Os objetos são copiados ao gravar e ao ler, como em um banco real.
 */
public final class Repositorios {

    private Repositorios() {
    }

    public static PedidoRepository pedidos(Map<String, Pedido> store) {
        PedidoRepository repo = mock(PedidoRepository.class);
        AtomicLong seq = new AtomicLong();
        when(repo.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("ped" + seq.incrementAndGet());
            }
            p.setVersion(p.getVersion() == null ? 0L : p.getVersion() + 1);
            store.put(p.getId(), p.copiar());
            return p;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> {
            Pedido p = store.get((String) inv.getArgument(0));
            return Optional.ofNullable(p == null ? null : p.copiar());
        });
        when(repo.findAllByOrderByDataHoraEntradaDesc()).thenAnswer(inv -> {
            List<Pedido> lista = new ArrayList<>();
            store.values().forEach(p -> lista.add(p.copiar()));
            return lista;
        });
        when(repo.existsByClienteId(anyString())).thenAnswer(inv ->
                store.values().stream().anyMatch(p -> inv.getArgument(0).equals(p.getClienteId())));
        doAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return null;
        }).when(repo).deleteById(anyString());
        return repo;
    }

    public static ClienteRepository clientes(Map<String, Cliente> store) {
        ClienteRepository repo = mock(ClienteRepository.class);
        AtomicLong seq = new AtomicLong();
        when(repo.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("cli" + seq.incrementAndGet());
            }
            store.put(c.getId(), copia(c));
            return c;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> {
            Cliente c = store.get((String) inv.getArgument(0));
            return Optional.ofNullable(c == null ? null : copia(c));
        });
        when(repo.findAll()).thenAnswer(inv -> {
            List<Cliente> lista = new ArrayList<>();
            store.values().forEach(c -> lista.add(copia(c)));
            return lista;
        });
        doAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return null;
        }).when(repo).deleteById(anyString());
        return repo;
    }

    public static UsuarioRepository usuarios(Map<String, Usuario> store) {
        UsuarioRepository repo = mock(UsuarioRepository.class);
        AtomicLong seq = new AtomicLong();
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("usr" + seq.incrementAndGet());
            }
            store.put(u.getId(), copia(u));
            return u;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> {
            Usuario u = store.get((String) inv.getArgument(0));
            return Optional.ofNullable(u == null ? null : copia(u));
        });
        when(repo.findByUsername(anyString())).thenAnswer(inv -> store.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getUsername())).findFirst().map(Repositorios::copia));
        when(repo.existsByUsername(anyString())).thenAnswer(inv -> store.values().stream()
                .anyMatch(u -> inv.getArgument(0).equals(u.getUsername())));
        when(repo.countByRole(anyString())).thenAnswer(inv -> store.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getRole())).count());
        when(repo.findAll()).thenAnswer(inv -> new ArrayList<>(store.values()));
        doAnswer(inv -> {
            store.remove((String) inv.getArgument(0));
            return null;
        }).when(repo).deleteById(anyString());
        return repo;
    }

    public static AuditoriaRepository auditoria(List<AuditoriaEvento> eventos) {
        AuditoriaRepository repo = mock(AuditoriaRepository.class);
        AtomicLong seq = new AtomicLong();
        when(repo.save(any(AuditoriaEvento.class))).thenAnswer(inv -> {
            AuditoriaEvento e = inv.getArgument(0);
            e.setId("aud" + seq.incrementAndGet());
            eventos.add(e);
            return e;
        });
        return repo;
    }

    public static Cliente copia(Cliente c) {
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

    public static Usuario copia(Usuario u) {
        Usuario n = new Usuario();
        n.setId(u.getId());
        n.setUsername(u.getUsername());
        n.setPassword(u.getPassword());
        n.setRole(u.getRole());
        return n;
    }
}
