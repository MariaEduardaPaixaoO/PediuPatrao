package com.umc.pediupatrao.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Transações do MongoDB (exigem replica set, como no MongoDB Atlas). Quando habilitadas, as
 * operações de negócio e o registro de auditoria são confirmados (commit) ou desfeitos (rollback)
 * juntos. Em instâncias standalone (sem replica set) mantenha desabilitado: nesse caso o
 * AuditoriaService aplica compensação (desfaz a operação se a auditoria falhar).
 */
@Configuration
@ConditionalOnProperty(name = "pediupatrao.mongodb.transacoes", havingValue = "true")
public class MongoTransactionConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
