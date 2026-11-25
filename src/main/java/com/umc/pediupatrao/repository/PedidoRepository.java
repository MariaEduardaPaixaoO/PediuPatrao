package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import java.time.LocalDateTime;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteId(String clienteId);

    boolean existsByClienteId(String clienteId);

    List<Pedido> findAllByOrderByDataHoraEntradaDesc();

    /** Pedidos com entrada em [inicio, fimExclusivo), sem o histórico de transições. */
    @Query(value = "{ 'dataHoraEntrada': { $gte: ?0, $lt: ?1 } }", fields = "{ 'historico': 0 }")
    List<Pedido> buscarParaDashboard(LocalDateTime inicio, LocalDateTime fimExclusivo);
}
