// Dashboard administrativo. Os cálculos ficam na API (/api/dashboard); aqui só formatação e gráficos (Chart.js 2.9).
(function (global) {
    'use strict';

    // ------------------------------------------------------------------ formatação (usada também pelos testes em Node)

    var moeda = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    var inteiro = new Intl.NumberFormat('pt-BR', {maximumFractionDigits: 0});
    var decimal = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1});

    function numero(v) {
        if (v === null || v === undefined || v === '') return null;
        var n = Number(v);
        return isFinite(n) ? n : null;
    }

    function formatarMoeda(v) {
        var n = numero(v);
        return n === null ? '—' : moeda.format(n);
    }

    function formatarInteiro(v) {
        var n = numero(v);
        return n === null ? '—' : inteiro.format(n);
    }

    function formatarPercentual(v) {
        var n = numero(v);
        return n === null ? '—' : decimal.format(n) + '%';
    }

    // Sem new Date(): "2026-11-24" seria lido como UTC e poderia virar 23/11 no Brasil
    function formatarData(iso) {
        if (!iso || typeof iso !== 'string') return '—';
        var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
        return m ? m[3] + '/' + m[2] + '/' + m[1] : '—';
    }

    function formatarDataHora(iso) {
        if (!iso || typeof iso !== 'string') return '—';
        var m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
        return m ? m[3] + '/' + m[2] + '/' + m[1] + ' ' + m[4] + ':' + m[5] : '—';
    }

    function escapar(valor) {
        return String(valor === null || valor === undefined ? '' : valor)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function textoVariacao(v) {
        var n = numero(v);
        if (n === null) return {texto: 'Sem dados do período anterior', classe: 'text-muted', icone: 'bi-dash'};
        if (n > 0) return {texto: '+' + decimal.format(n) + '% vs. período anterior', classe: 'text-success',
            icone: 'bi-arrow-up-right'};
        if (n < 0) return {texto: decimal.format(n) + '% vs. período anterior', classe: 'text-danger',
            icone: 'bi-arrow-down-right'};
        return {texto: 'Igual ao período anterior', classe: 'text-muted', icone: 'bi-arrow-right'};
    }

    // Mesmas regras do FiltroPeriodo, para avisar antes de chamar a API. Retorna a mensagem ou null.
    function validarDatas(inicio, fim, hoje, maximoDias) {
        var re = /^\d{4}-\d{2}-\d{2}$/;
        if (!inicio || !fim) return 'Informe a data inicial e a data final.';
        if (!re.test(inicio) || !re.test(fim)) return 'Data inválida. Use o formato AAAA-MM-DD.';
        if (fim < inicio) return 'A data final não pode ser anterior à data inicial.';
        if (hoje && inicio > hoje) return 'A data inicial não pode estar no futuro.';
        if (hoje && fim > hoje) return 'A data final não pode estar no futuro.';
        var dias = Math.round((Date.UTC(+fim.slice(0, 4), +fim.slice(5, 7) - 1, +fim.slice(8, 10))
            - Date.UTC(+inicio.slice(0, 4), +inicio.slice(5, 7) - 1, +inicio.slice(8, 10))) / 86400000) + 1;
        if (maximoDias && dias > maximoDias) return 'O intervalo máximo de consulta é de ' + maximoDias + ' dias.';
        return null;
    }

    function parametros(filtro, extras) {
        var p = [];
        if (filtro.periodo) {
            p.push('periodo=' + encodeURIComponent(filtro.periodo));
        } else {
            p.push('inicio=' + encodeURIComponent(filtro.inicio));
            p.push('fim=' + encodeURIComponent(filtro.fim));
        }
        Object.keys(extras || {}).forEach(function (k) {
            p.push(encodeURIComponent(k) + '=' + encodeURIComponent(extras[k]));
        });
        return '?' + p.join('&');
    }

    var util = {
        formatarMoeda: formatarMoeda, formatarInteiro: formatarInteiro, formatarPercentual: formatarPercentual,
        formatarData: formatarData, formatarDataHora: formatarDataHora, escapar: escapar,
        textoVariacao: textoVariacao, validarDatas: validarDatas, parametros: parametros
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = util;
        return;
    }

    // ------------------------------------------------------------------ página

    var CORES_STATUS = {
        RECEBIDO: '#0d6efd', EM_PREPARACAO: '#ffc107', PRONTO: '#0dcaf0', SAIU_PARA_ENTREGA: '#343a40',
        RETIRADO: '#6f42c1', FINALIZADO: '#198754', CANCELADO: '#dc3545'
    };
    var INTERVALO_AUTO_MS = 5 * 60 * 1000;

    var raiz, api, loginUrl, hoje, maximoDias;
    var filtro = {periodo: 'ULTIMOS_30_DIAS', inicio: null, fim: null};
    var graficos = {};
    var controladorAtual = null;
    var temporizador = null;

    function $(id) { return document.getElementById(id); }

    function iniciar() {
        raiz = $('dashboard');
        if (!raiz) return;
        api = raiz.getAttribute('data-api');
        loginUrl = raiz.getAttribute('data-login') || '/login';
        hoje = raiz.getAttribute('data-hoje');
        maximoDias = Number(raiz.getAttribute('data-maximo-dias')) || 366;

        if (typeof Chart !== 'undefined') {
            Chart.defaults.global.defaultFontFamily = getComputedStyle(document.body).fontFamily;
            Chart.defaults.global.defaultFontColor = '#495057';
        }

        Array.prototype.forEach.call(raiz.querySelectorAll('[data-periodo]'), function (botao) {
            botao.addEventListener('click', function () {
                filtro = {periodo: botao.getAttribute('data-periodo'), inicio: null, fim: null};
                $('dashErroDatas').textContent = '';
                marcarBotoes();
                carregar();
            });
        });

        $('dashFormDatas').addEventListener('submit', function (e) {
            e.preventDefault();
            var inicio = $('dashInicio').value;
            var fim = $('dashFim').value;
            var erro = validarDatas(inicio, fim, hoje, maximoDias);
            $('dashErroDatas').textContent = erro || '';
            if (erro) return;
            filtro = {periodo: null, inicio: inicio, fim: fim};
            marcarBotoes();
            carregar();
        });

        $('dashAtualizar').addEventListener('click', function () { carregar(); });
        $('dashLimite').addEventListener('change', function () {
            carregarRanking(undefined).catch(function (e) { if (e && e.name !== 'AbortError') mostrarErroGeral(e); });
        });

        $('dashAutoAtualizar').addEventListener('change', function (e) {
            if (temporizador) clearInterval(temporizador);
            temporizador = null;
            if (e.target.checked) {
                // aba em segundo plano não consulta o banco
                temporizador = setInterval(function () {
                    if (document.visibilityState === 'visible') carregar();
                }, INTERVALO_AUTO_MS);
            }
        });

        carregar();
    }

    function marcarBotoes() {
        Array.prototype.forEach.call(raiz.querySelectorAll('[data-periodo]'), function (b) {
            b.setAttribute('aria-pressed', String(b.getAttribute('data-periodo') === filtro.periodo));
        });
    }

    // Ao trocar o filtro rapidamente, a resposta antiga não pode sobrescrever a nova
    function novoSinal() {
        if (controladorAtual) controladorAtual.abort();
        controladorAtual = typeof AbortController !== 'undefined' ? new AbortController() : null;
        return controladorAtual ? controladorAtual.signal : undefined;
    }

    function buscar(caminho, extras, sinal) {
        return fetch(api + caminho + parametros(filtro, extras), {
            headers: {Accept: 'application/json'}, credentials: 'same-origin', signal: sinal
        }).then(function (r) {
            if (r.ok) return r.json();
            var erro = new Error();
            erro.status = r.status;
            return r.json().catch(function () { return {}; }).then(function (corpo) {
                if (r.status === 401) erro.message = 'Sua sessão expirou. Entre novamente para ver o dashboard.';
                else if (r.status === 403) erro.message = 'Seu perfil não tem permissão para ver o dashboard.';
                else if (r.status === 400) erro.message = corpo.mensagem || 'Filtro inválido.';
                else erro.message = 'O servidor não conseguiu calcular os dados (HTTP ' + r.status + ').';
                throw erro;
            });
        }, function (e) {
            if (e && e.name === 'AbortError') throw e;
            var erro = new Error('Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.');
            erro.status = 0;
            throw erro;
        });
    }

    function carregar() {
        var sinal = novoSinal();
        $('dashErroGeral').classList.add('d-none');
        $('dashAtualizadoEm').textContent = 'Carregando…';
        ['blocoEvolucao', 'blocoPedidosPeriodo', 'blocoDistribuicao', 'blocoSituacao', 'blocoRanking']
            .forEach(function (id) { carregando(id, true); });
        raiz.setAttribute('aria-busy', 'true');

        var tarefas = [
            buscar('/resumo', null, sinal).then(renderizarResumo),
            buscar('/vendas', null, sinal).then(renderizarVendas, falhaBloco(['blocoEvolucao', 'blocoPedidosPeriodo'])),
            buscar('/pedidos/status', null, sinal).then(renderizarStatus,
                falhaBloco(['blocoDistribuicao', 'blocoSituacao'])),
            carregarRanking(sinal)
        ];
        Promise.all(tarefas.map(function (t) { return t.then(function () { return null; }, function (e) { return e; }); }))
            .then(function (erros) {
                var reais = erros.filter(function (e) { return e && e.name !== 'AbortError'; });
                if (erros.some(function (e) { return e && e.name === 'AbortError'; }) && !reais.length) return;
                raiz.setAttribute('aria-busy', 'false');
                if (reais.length) {
                    mostrarErroGeral(reais[0]);
                    $('dashAtualizadoEm').textContent = 'Falha na última atualização';
                }
            });
    }

    function carregarRanking(sinal) {
        carregando('blocoRanking', true);
        return buscar('/produtos-mais-vendidos', {limite: $('dashLimite').value}, sinal)
            .then(renderizarRanking, falhaBloco(['blocoRanking']));
    }

    function mostrarErroGeral(erro) {
        var el = $('dashErroGeral');
        var html = '<i class="bi bi-exclamation-triangle-fill me-2" aria-hidden="true"></i>' + escapar(erro.message);
        if (erro.status === 401) html += ' <a class="alert-link" href="' + escapar(loginUrl) + '">Entrar</a>';
        el.innerHTML = html;
        el.classList.remove('d-none');
        if (erro.status === 400) {
            $('dashErroDatas').textContent = erro.message;
        }
    }

    function falhaBloco(ids) {
        return function (erro) {
            if (!erro || erro.name !== 'AbortError') {
                ids.forEach(function (id) {
                    aviso(id, 'bi-exclamation-circle', erro ? erro.message : 'Erro ao carregar.', true);
                });
            }
            throw erro;
        };
    }

    function carregando(id, sim) {
        var el = $(id);
        if (!el) return;
        el.classList.toggle('dash-carregando', sim);
        if (sim) removerAviso(id);
    }

    function aviso(id, icone, texto, ehErro) {
        var el = $(id);
        carregando(id, false);
        removerAviso(id);
        var div = document.createElement('div');
        div.className = 'dash-aviso' + (ehErro ? ' dash-aviso-erro' : '');
        div.setAttribute('role', ehErro ? 'alert' : 'status');
        div.innerHTML = '<i class="bi ' + icone + '" aria-hidden="true"></i><span>' + escapar(texto) + '</span>';
        el.appendChild(div);
    }

    function removerAviso(id) {
        var el = $(id);
        var existente = el && el.querySelector('.dash-aviso');
        if (existente) existente.remove();
    }

    function variacaoEm(id, valor) {
        var v = textoVariacao(valor);
        $(id).className = 'small ' + v.classe;
        $(id).innerHTML = '<i class="bi ' + v.icone + '" aria-hidden="true"></i> ' + escapar(v.texto);
    }

    // ------------------------------------------------------------------ RF01

    function renderizarResumo(r) {
        var p = r.periodo || {};
        var a = (r.comparacao && r.comparacao.periodoAnterior) || {};
        $('dashPeriodoTexto').textContent = 'Período: ' + formatarData(p.inicio) + ' a ' + formatarData(p.fim)
            + ' (' + formatarInteiro(p.dias) + (p.dias === 1 ? ' dia' : ' dias') + ') · comparado com '
            + formatarData(a.inicio) + ' a ' + formatarData(a.fim);
        if (!filtro.periodo) {
            $('dashInicio').value = p.inicio || '';
            $('dashFim').value = p.fim || '';
        }

        $('kpiTotalVendas').textContent = formatarMoeda(r.totalVendas);
        variacaoEm('kpiTotalVendasVar', r.comparacao && r.comparacao.variacaoVendasPercentual);
        $('kpiQtdVendas').textContent = formatarInteiro(r.quantidadeVendas) + ' pedido(s) finalizado(s)';

        $('kpiPedidos').textContent = formatarInteiro(r.quantidadePedidos);
        variacaoEm('kpiPedidosVar', r.comparacao && r.comparacao.variacaoPedidosPercentual);
        $('kpiAndamento').textContent = 'Em andamento: ' + formatarInteiro(r.pedidosEmAndamento)
            + ' (' + formatarMoeda(r.valorEmAndamento) + ')';

        $('kpiTicket').textContent = formatarMoeda(r.ticketMedio);
        variacaoEm('kpiTicketVar', r.comparacao && r.comparacao.variacaoTicketMedioPercentual);

        $('kpiCancelados').textContent = formatarInteiro(r.quantidadeCancelados);
        $('kpiTaxaCancelamento').className = 'small ' + (Number(r.quantidadeCancelados) > 0 ? 'text-danger' : 'text-muted');
        $('kpiTaxaCancelamento').textContent = formatarPercentual(r.taxaCancelamentoPercentual) + ' dos pedidos';
        $('kpiValorCancelado').textContent = formatarMoeda(r.valorCancelado) + ' não faturados';

        $('dashAtualizadoEm').textContent = 'Atualizado em ' + formatarDataHora(r.geradoEm);
    }

    // ------------------------------------------------------------------ RF02

    function grafico(nome, canvasId, config) {
        if (typeof Chart === 'undefined') {
            throw new Error('Biblioteca de gráficos não carregada.');
        }
        if (graficos[nome]) graficos[nome].destroy();
        graficos[nome] = new Chart($(canvasId).getContext('2d'), config);
        return graficos[nome];
    }

    function renderizarVendas(r) {
        var pontos = r.pontos || [];
        var anteriores = r.pontosAnterior || [];
        var rotulos = pontos.map(function (p) { return p.rotulo; });
        var mensal = r.agrupamento === 'MES';
        var temPedidos = pontos.some(function (p) { return p.quantidadePedidos > 0; });
        var temAnterior = anteriores.some(function (p) { return p.quantidadePedidos > 0; });

        $('evolucaoLegenda').textContent = 'Total: ' + formatarMoeda(r.totalVendas) + ' · anterior: '
            + formatarMoeda(r.totalVendasAnterior) + (mensal ? ' · agrupado por mês' : ' · por dia');

        var datasets = [{
            label: 'Vendas no período', data: pontos.map(function (p) { return Number(p.totalVendas) || 0; }),
            borderColor: '#c0392b', backgroundColor: 'rgba(192,57,43,.12)', borderWidth: 2, pointRadius: 3,
            lineTension: .25, fill: true
        }];
        if (temAnterior) {
            datasets.push({
                label: 'Período anterior', data: rotulos.map(function (_, i) {
                    return anteriores[i] ? Number(anteriores[i].totalVendas) || 0 : null;
                }),
                borderColor: '#6c757d', borderDash: [6, 4], borderWidth: 2, pointRadius: 0, fill: false,
                lineTension: .25
            });
        }
        grafico('evolucao', 'graficoEvolucao', {
            type: 'line',
            data: {labels: rotulos, datasets: datasets},
            options: {
                responsive: true, maintainAspectRatio: false,
                legend: {position: 'bottom'},
                tooltips: {mode: 'index', intersect: false, callbacks: {
                    title: function (itens) {
                        var p = pontos[itens[0].index];
                        return p ? (mensal ? p.rotulo : formatarData(p.inicio)) : '';
                    },
                    label: function (item, dados) {
                        return dados.datasets[item.datasetIndex].label + ': ' + formatarMoeda(item.yLabel);
                    }
                }},
                scales: {yAxes: [{ticks: {beginAtZero: true, callback: function (v) { return formatarMoeda(v); }}}]}
            }
        });
        $('graficoEvolucao').setAttribute('aria-label', 'Evolução das vendas: total de '
            + formatarMoeda(r.totalVendas) + ' no período, contra ' + formatarMoeda(r.totalVendasAnterior)
            + ' no período anterior.');
        carregando('blocoEvolucao', false);
        if (!temPedidos) aviso('blocoEvolucao', 'bi-inbox', 'Nenhum pedido registrado neste período.');

        grafico('pedidos', 'graficoPedidos', {
            type: 'bar',
            data: {labels: rotulos, datasets: [
                {label: 'Finalizados', backgroundColor: '#198754',
                    data: pontos.map(function (p) { return p.quantidadeVendas; })},
                {label: 'Em andamento', backgroundColor: '#0d6efd',
                    data: pontos.map(function (p) {
                        return Math.max(0, p.quantidadePedidos - p.quantidadeVendas - p.quantidadeCancelados);
                    })},
                {label: 'Cancelados', backgroundColor: '#dc3545',
                    data: pontos.map(function (p) { return p.quantidadeCancelados; })}
            ]},
            options: {
                responsive: true, maintainAspectRatio: false, legend: {position: 'bottom'},
                tooltips: {mode: 'index', intersect: false},
                scales: {
                    xAxes: [{stacked: true}],
                    yAxes: [{stacked: true, ticks: {beginAtZero: true, precision: 0}}]
                }
            }
        });
        carregando('blocoPedidosPeriodo', false);
        if (!temPedidos) aviso('blocoPedidosPeriodo', 'bi-inbox', 'Nenhum pedido registrado neste período.');
    }

    // ------------------------------------------------------------------ RF03

    function renderizarStatus(r) {
        var itens = r.itens || [];
        $('situacaoTotal').textContent = formatarInteiro(r.total) + ' pedido(s)';

        $('listaSituacao').innerHTML = itens.map(function (s) {
            var cor = CORES_STATUS[s.status] || '#adb5bd';
            var pct = Number(s.percentual) || 0;
            return '<li class="dash-status-item">'
                + '<div class="d-flex justify-content-between small">'
                + '<span><span class="dash-ponto" aria-hidden="true" style="background:' + cor + '"></span>' + escapar(s.descricao)
                + '</span><span class="fw-semibold">' + formatarInteiro(s.quantidade)
                + ' <span class="text-muted fw-normal">(' + formatarPercentual(s.percentual) + ')</span></span></div>'
                + '<div class="progress dash-status-barra" role="progressbar" aria-label="' + escapar(s.descricao)
                + '" aria-valuenow="' + pct + '" aria-valuemin="0" aria-valuemax="100">'
                + '<div class="progress-bar" style="width:' + pct + '%;background:' + cor + '"></div></div></li>';
        }).join('');
        $('situacaoNaoClassificados').textContent = r.naoClassificados > 0
            ? formatarInteiro(r.naoClassificados) + ' pedido(s) do formato antigo sem status reconhecido.' : '';
        carregando('blocoSituacao', false);

        var comPedidos = itens.filter(function (s) { return s.quantidade > 0; });
        grafico('distribuicao', 'graficoDistribuicao', {
            type: 'doughnut',
            data: {
                labels: comPedidos.map(function (s) { return s.descricao; }),
                datasets: [{
                    data: comPedidos.map(function (s) { return s.quantidade; }),
                    backgroundColor: comPedidos.map(function (s) { return CORES_STATUS[s.status] || '#adb5bd'; }),
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true, maintainAspectRatio: false, cutoutPercentage: 60,
                legend: {position: 'bottom', labels: {boxWidth: 12}},
                tooltips: {callbacks: {label: function (item, dados) {
                    var s = comPedidos[item.index];
                    return dados.labels[item.index] + ': ' + formatarInteiro(s.quantidade) + ' ('
                        + formatarPercentual(s.percentual) + ')';
                }}}
            }
        });
        $('graficoDistribuicao').setAttribute('aria-label', 'Distribuição por status: ' + (comPedidos.length
            ? comPedidos.map(function (s) { return s.descricao + ' ' + s.quantidade; }).join(', ')
            : 'nenhum pedido') + '.');
        carregando('blocoDistribuicao', false);
        if (!r.total) {
            aviso('blocoDistribuicao', 'bi-pie-chart', 'Sem pedidos para distribuir neste período.');
        }
    }

    // ------------------------------------------------------------------ RF04

    function renderizarRanking(r) {
        var itens = r.itens || [];
        carregando('blocoRanking', false);
        if (!itens.length) {
            $('tabelaRanking').innerHTML = '<tr><td colspan="6" class="text-center text-muted py-4">'
                + '<i class="bi bi-inbox me-1" aria-hidden="true"></i>'
                + 'Nenhum produto vendido (pedido finalizado) neste período.</td></tr>';
            return;
        }
        $('tabelaRanking').innerHTML = itens.map(function (p) {
            var pct = Number(p.participacaoPercentual) || 0;
            return '<tr>'
                + '<td class="text-center fw-semibold">' + escapar(p.posicao) + '</td>'
                + '<td>' + escapar(p.nome) + '</td>'
                + '<td class="text-end">' + formatarInteiro(p.quantidadeVendida) + '</td>'
                + '<td class="text-end d-none d-md-table-cell text-muted">' + formatarMoeda(p.valorBruto) + '</td>'
                + '<td class="text-end fw-semibold">' + formatarMoeda(p.valorLiquido) + '</td>'
                + '<td class="d-none d-sm-table-cell"><div class="d-flex align-items-center gap-2">'
                + '<div class="progress flex-grow-1 dash-status-barra" role="progressbar" aria-label="Participação de '
                + escapar(p.nome) + '" aria-valuenow="' + pct + '" aria-valuemin="0" aria-valuemax="100">'
                + '<div class="progress-bar" style="width:' + pct + '%;background:#c0392b"></div></div>'
                + '<span class="small text-muted">' + formatarPercentual(pct) + '</span></div></td>'
                + '</tr>';
        }).join('');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', iniciar);
    } else {
        iniciar();
    }
})(typeof window !== 'undefined' ? window : this);
