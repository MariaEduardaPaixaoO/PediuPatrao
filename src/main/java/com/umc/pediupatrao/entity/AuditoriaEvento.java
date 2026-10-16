package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Evento de auditoria (append-only): nunca é alterado nem excluído pela aplicação.
 * Não contém senhas, tokens ou credenciais.
 */
@Document(collection = "auditoria")
public class AuditoriaEvento {

    @Id
    private String id;

    private LocalDateTime dataHora;
    /** Usuário autenticado que executou a operação (obtido do contexto de segurança). */
    private String usuario;
    /** Perfil do usuário no momento da operação. */
    private String perfil;
    private TipoOperacao operacao;
    private String entidadeTipo;
    private String entidadeId;
    private List<CampoAlterado> alteracoes = new ArrayList<>();
    private String justificativa;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDateTime getDataHora() { return dataHora; }
    public void setDataHora(LocalDateTime dataHora) { this.dataHora = dataHora; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getPerfil() { return perfil; }
    public void setPerfil(String perfil) { this.perfil = perfil; }

    public TipoOperacao getOperacao() { return operacao; }
    public void setOperacao(TipoOperacao operacao) { this.operacao = operacao; }

    public String getEntidadeTipo() { return entidadeTipo; }
    public void setEntidadeTipo(String entidadeTipo) { this.entidadeTipo = entidadeTipo; }

    public String getEntidadeId() { return entidadeId; }
    public void setEntidadeId(String entidadeId) { this.entidadeId = entidadeId; }

    public List<CampoAlterado> getAlteracoes() { return alteracoes; }
    public void setAlteracoes(List<CampoAlterado> alteracoes) { this.alteracoes = alteracoes; }

    public String getJustificativa() { return justificativa; }
    public void setJustificativa(String justificativa) { this.justificativa = justificativa; }
}
