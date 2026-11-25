package com.umc.pediupatrao.service.dashboard;

import com.umc.pediupatrao.dto.dashboard.PeriodoDTO;
import com.umc.pediupatrao.exception.NegocioException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Período do dashboard (RF05): predefinido ({@code periodo}) ou personalizado ({@code inicio} e
 * {@code fim}). Sem parâmetros, últimos 30 dias. Datas inclusivas, no fuso do servidor.
 */
public record FiltroPeriodo(String tipo, LocalDate inicio, LocalDate fim) {

    /** Evita consultas muito grandes ao banco. */
    public static final int MAXIMO_DIAS = 366;

    public static final String HOJE = "HOJE";
    public static final String ULTIMOS_7_DIAS = "ULTIMOS_7_DIAS";
    public static final String ULTIMOS_30_DIAS = "ULTIMOS_30_DIAS";
    public static final String MES_ATUAL = "MES_ATUAL";
    public static final String PERSONALIZADO = "PERSONALIZADO";
    public static final String ANTERIOR = "ANTERIOR";

    public FiltroPeriodo {
        if (inicio == null || fim == null) {
            throw new NegocioException("Período inválido.");
        }
        if (fim.isBefore(inicio)) {
            throw new NegocioException("A data final não pode ser anterior à data inicial.");
        }
    }

    public static FiltroPeriodo resolver(String periodo, String inicio, String fim, LocalDate hoje) {
        boolean temPeriodo = periodo != null && !periodo.isBlank();
        boolean temInicio = inicio != null && !inicio.isBlank();
        boolean temFim = fim != null && !fim.isBlank();
        String tipo = temPeriodo ? periodo.trim().toUpperCase(Locale.ROOT) : null;

        if (!temPeriodo && !temInicio && !temFim) {
            tipo = ULTIMOS_30_DIAS;
        }
        if (tipo == null || PERSONALIZADO.equals(tipo)) {
            return personalizado(inicio, fim, temInicio, temFim, hoje);
        }
        if (temInicio || temFim) {
            throw new NegocioException("Informe um período predefinido ou as datas inicial e final, não os dois.");
        }
        switch (tipo) {
            case HOJE:
                return new FiltroPeriodo(HOJE, hoje, hoje);
            case ULTIMOS_7_DIAS:
                return new FiltroPeriodo(ULTIMOS_7_DIAS, hoje.minusDays(6), hoje);
            case ULTIMOS_30_DIAS:
                return new FiltroPeriodo(ULTIMOS_30_DIAS, hoje.minusDays(29), hoje);
            case MES_ATUAL:
                return new FiltroPeriodo(MES_ATUAL, hoje.withDayOfMonth(1), hoje);
            default:
                throw new NegocioException("Período inválido. Use HOJE, ULTIMOS_7_DIAS, ULTIMOS_30_DIAS, "
                        + "MES_ATUAL ou informe as datas inicial e final.");
        }
    }

    private static FiltroPeriodo personalizado(String inicio, String fim, boolean temInicio, boolean temFim,
            LocalDate hoje) {
        if (!temInicio || !temFim) {
            throw new NegocioException("Informe a data inicial e a data final.");
        }
        LocalDate dataInicio = data(inicio, "inicial");
        LocalDate dataFim = data(fim, "final");
        if (dataFim.isBefore(dataInicio)) {
            throw new NegocioException("A data final não pode ser anterior à data inicial.");
        }
        if (dataInicio.isAfter(hoje)) {
            throw new NegocioException("A data inicial não pode estar no futuro.");
        }
        if (dataFim.isAfter(hoje)) {
            throw new NegocioException("A data final não pode estar no futuro.");
        }
        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new NegocioException("O intervalo máximo de consulta é de " + MAXIMO_DIAS + " dias.");
        }
        return new FiltroPeriodo(PERSONALIZADO, dataInicio, dataFim);
    }

    private static LocalDate data(String valor, String qual) {
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new NegocioException("Data " + qual + " inválida. Use o formato AAAA-MM-DD.");
        }
    }

    public long dias() {
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }

    /** Período imediatamente anterior, com a mesma quantidade de dias. */
    public FiltroPeriodo anterior() {
        long dias = dias();
        return new FiltroPeriodo(ANTERIOR, inicio.minusDays(dias), inicio.minusDays(1));
    }

    public LocalDateTime inicioInclusivo() {
        return inicio.atStartOfDay();
    }

    /** 00:00 do dia seguinte ao fim, para consultar com "menor que". */
    public LocalDateTime fimExclusivo() {
        return fim.plusDays(1).atStartOfDay();
    }

    public boolean contem(LocalDateTime dataHora) {
        return dataHora != null && !dataHora.isBefore(inicioInclusivo()) && dataHora.isBefore(fimExclusivo());
    }

    public PeriodoDTO paraDTO() {
        return new PeriodoDTO(tipo, inicio, fim, dias());
    }
}
