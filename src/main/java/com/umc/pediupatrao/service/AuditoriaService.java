package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.AuditoriaException;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Serviço de auditoria. Grava eventos append-only no MongoDB (coleção "auditoria").
 *
 * - O usuário e o perfil vêm do contexto de segurança (UsuarioContexto), nunca da requisição.
 * - Campos sensíveis (senha, token, credenciais) são descartados antes da gravação.
 * - Falhas de persistência lançam AuditoriaException; nunca são silenciadas.
 * - Não existem métodos de atualização ou exclusão de eventos.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private static final Pattern CAMPO_SENSIVEL =
            Pattern.compile(".*(senha|password|passwd|token|secret|credencia).*", Pattern.CASE_INSENSITIVE);
    private static final int TAMANHO_MAXIMO_VALOR = 1000;
    public static final int LIMITE_PADRAO = 200;
    public static final int LIMITE_MAXIMO = 500;

    private final AuditoriaRepository auditoriaRepository;
    private final MongoTemplate mongoTemplate;
    private final UsuarioContexto contexto;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, MongoTemplate mongoTemplate,
            UsuarioContexto contexto, Clock clock) {
        this.auditoriaRepository = auditoriaRepository;
        this.mongoTemplate = mongoTemplate;
        this.contexto = contexto;
        this.clock = clock;
    }

    /** Registra um evento. Lança AuditoriaException se não for possível persistir. */
    public AuditoriaEvento registrar(TipoOperacao operacao, String entidadeTipo, String entidadeId,
            List<CampoAlterado> alteracoes, String justificativa) {
        AuditoriaEvento evento = new AuditoriaEvento();
        evento.setId(null); // sempre inserção de um novo evento
        evento.setDataHora(LocalDateTime.now(clock));
        evento.setUsuario(contexto.usuario());
        evento.setPerfil(contexto.perfil());
        evento.setOperacao(operacao);
        evento.setEntidadeTipo(entidadeTipo);
        evento.setEntidadeId(entidadeId);
        evento.setAlteracoes(sanitizar(alteracoes));
        evento.setJustificativa(justificativa == null ? null : limitar(justificativa.trim()));
        try {
            return auditoriaRepository.save(evento);
        } catch (RuntimeException e) {
            log.error("Falha ao persistir auditoria (operacao={}, entidade={}:{})",
                    operacao, entidadeTipo, entidadeId, e);
            throw new AuditoriaException("Não foi possível registrar a auditoria da operação.", false, e);
        }
    }

    /**
     * Registra o evento; se falhar, executa a compensação (desfaz a operação de negócio já gravada)
     * e propaga AuditoriaException indicando se a operação foi desfeita. Quando as transações do
     * MongoDB estão habilitadas, a exceção também provoca o rollback da transação.
     */
    public void registrarComCompensacao(Runnable compensacao, TipoOperacao operacao, String entidadeTipo,
            String entidadeId, List<CampoAlterado> alteracoes, String justificativa) {
        try {
            registrar(operacao, entidadeTipo, entidadeId, alteracoes, justificativa);
        } catch (RuntimeException falha) {
            boolean desfeita = false;
            try {
                compensacao.run();
                desfeita = true;
            } catch (RuntimeException erroCompensacao) {
                log.error("Falha ao compensar operação após erro de auditoria (entidade={}:{})",
                        entidadeTipo, entidadeId, erroCompensacao);
            }
            throw new AuditoriaException(desfeita
                    ? "A operação foi desfeita porque não foi possível registrar a auditoria."
                    : "ATENÇÃO: a operação pode ter sido aplicada sem o registro de auditoria. "
                            + "Informe o administrador do sistema.",
                    desfeita, falha);
        }
    }

    /** Consulta (somente ADMIN e GERENTE), do mais recente para o mais antigo. */
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<AuditoriaEvento> consultar(String entidadeTipo, String entidadeId, String usuario,
            TipoOperacao operacao, Integer limite) {
        Query query = new Query();
        if (temTexto(entidadeTipo)) {
            query.addCriteria(Criteria.where("entidadeTipo").is(entidadeTipo.trim().toUpperCase(Locale.ROOT)));
        }
        if (temTexto(entidadeId)) {
            query.addCriteria(Criteria.where("entidadeId").is(entidadeId.trim()));
        }
        if (temTexto(usuario)) {
            query.addCriteria(Criteria.where("usuario").is(usuario.trim()));
        }
        if (operacao != null) {
            query.addCriteria(Criteria.where("operacao").is(operacao.name()));
        }
        int max = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        query.with(Sort.by(Sort.Direction.DESC, "dataHora")).limit(max);
        return mongoTemplate.find(query, AuditoriaEvento.class);
    }

    /** Descarta campos sensíveis e normaliza os valores. */
    List<CampoAlterado> sanitizar(List<CampoAlterado> alteracoes) {
        List<CampoAlterado> resultado = new ArrayList<>();
        if (alteracoes == null) {
            return resultado;
        }
        for (CampoAlterado c : alteracoes) {
            if (c == null || c.getCampo() == null || CAMPO_SENSIVEL.matcher(c.getCampo()).matches()) {
                continue;
            }
            if (Objects.equals(c.getValorAnterior(), c.getValorNovo())) {
                continue;
            }
            resultado.add(new CampoAlterado(c.getCampo(), limitar(c.getValorAnterior()), limitar(c.getValorNovo())));
        }
        return resultado;
    }

    private static String limitar(String valor) {
        if (valor == null) {
            return null;
        }
        return valor.length() > TAMANHO_MAXIMO_VALOR ? valor.substring(0, TAMANHO_MAXIMO_VALOR) + "…" : valor;
    }

    private static boolean temTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
