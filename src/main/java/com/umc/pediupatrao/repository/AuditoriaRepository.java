package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repositório propositalmente restrito (append-only): expõe apenas gravação de novos eventos
 * e leitura por id. Não há métodos de atualização em lote nem de exclusão. As consultas filtradas
 * são feitas pelo AuditoriaService.
 */
public interface AuditoriaRepository extends Repository<AuditoriaEvento, String> {

    <S extends AuditoriaEvento> S save(S evento);

    Optional<AuditoriaEvento> findById(String id);
}
