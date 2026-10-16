package com.umc.pediupatrao.exception;

/**
 * Falha ao persistir o registro de auditoria. Indica se a operação de negócio foi desfeita
 * (compensada); nunca se deve informar que uma operação foi auditada quando esta exceção ocorre.
 */
public class AuditoriaException extends RuntimeException {

    private final boolean operacaoDesfeita;

    public AuditoriaException(String mensagem, boolean operacaoDesfeita, Throwable causa) {
        super(mensagem, causa);
        this.operacaoDesfeita = operacaoDesfeita;
    }

    public boolean isOperacaoDesfeita() {
        return operacaoDesfeita;
    }
}
