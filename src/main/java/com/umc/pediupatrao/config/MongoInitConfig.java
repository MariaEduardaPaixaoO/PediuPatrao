package com.umc.pediupatrao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;

@Configuration
public class MongoInitConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoInitConfig.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (!mongoTemplate.collectionExists("pedidos")) {
            mongoTemplate.createCollection("pedidos");
        }
        if (!mongoTemplate.collectionExists("clientes")) {
            mongoTemplate.createCollection("clientes");
        }
        if (!mongoTemplate.collectionExists("usuarios")) {
            mongoTemplate.createCollection("usuarios");
        }
        if (!mongoTemplate.collectionExists("produtos")) {
            mongoTemplate.createCollection("produtos");
        }
        if (!mongoTemplate.collectionExists("horariosAtendimento")) {
            mongoTemplate.createCollection("horariosAtendimento");
        }
        if (!mongoTemplate.collectionExists("auditoria")) {
            mongoTemplate.createCollection("auditoria");
        }
        criarIndicesDashboard();
    }

    /** Índice das consultas por período do dashboard. Se falhar, a aplicação sobe assim mesmo. */
    private void criarIndicesDashboard() {
        try {
            mongoTemplate.indexOps("pedidos").ensureIndex(
                    new Index().on("dataHoraEntrada", Sort.Direction.ASC).named("idx_pedidos_dataHoraEntrada"));
        } catch (RuntimeException e) {
            log.warn("Não foi possível criar o índice de dataHoraEntrada em pedidos: {}", e.getMessage());
        }
    }
}
