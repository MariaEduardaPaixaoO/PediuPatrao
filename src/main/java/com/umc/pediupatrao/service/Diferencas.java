package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.CampoAlterado;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utilitário para comparar o estado anterior e o posterior de uma entidade e montar a lista de
 * campos alterados (com valores anteriores e novos reais) para a auditoria.
 */
final class Diferencas {

    private Diferencas() {
    }

    /** Compara dois "resumos" (campo -> valor em texto), preservando a ordem dos campos. */
    static List<CampoAlterado> comparar(Map<String, String> antes, Map<String, String> depois) {
        Map<String, String> a = antes == null ? new LinkedHashMap<>() : antes;
        Map<String, String> d = depois == null ? new LinkedHashMap<>() : depois;
        List<String> campos = new ArrayList<>(a.keySet());
        for (String campo : d.keySet()) {
            if (!campos.contains(campo)) {
                campos.add(campo);
            }
        }
        List<CampoAlterado> alteracoes = new ArrayList<>();
        for (String campo : campos) {
            String valorAnterior = a.get(campo);
            String valorNovo = d.get(campo);
            if (!Objects.equals(valorAnterior, valorNovo)) {
                alteracoes.add(new CampoAlterado(campo, valorAnterior, valorNovo));
            }
        }
        return alteracoes;
    }

    /** Coloca no mapa somente valores não nulos e não vazios (texto). */
    static void put(Map<String, String> mapa, String campo, Object valor) {
        if (valor == null) {
            return;
        }
        String texto = valor.toString();
        if (!texto.isBlank()) {
            mapa.put(campo, texto);
        }
    }
}
