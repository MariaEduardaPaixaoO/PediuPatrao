package com.umc.pediupatrao.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único do servidor. Todos os horários de entrada, saída, cancelamento e auditoria
 * vêm daqui, nunca do navegador.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${pediupatrao.timezone:America/Sao_Paulo}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
