// node src/test/js/dashboard.test.js
const assert = require('assert');
const u = require('../../main/resources/static/js/dashboard.js');

const casos = [
  ['moeda em reais', () => assert.strictEqual(u.formatarMoeda(1234.5).replace(/\s/g, ' '), 'R$ 1.234,50')],
  ['moeda nula vira traço', () => assert.strictEqual(u.formatarMoeda(null), '—')],
  ['moeda inválida vira traço', () => assert.strictEqual(u.formatarMoeda('abc'), '—')],
  ['data ISO para dd/mm/aaaa', () => assert.strictEqual(u.formatarData('2026-11-24'), '24/11/2026')],
  ['data nula vira traço', () => assert.strictEqual(u.formatarData(null), '—')],
  ['data e hora', () => assert.strictEqual(u.formatarDataHora('2026-11-24T19:58:03.12'), '24/11/2026 19:58')],
  ['percentual', () => assert.strictEqual(u.formatarPercentual(12.345), '12,3%')],
  ['inteiro com milhar', () => assert.strictEqual(u.formatarInteiro(12345), '12.345')],
  ['variação sem base', () => assert.strictEqual(u.textoVariacao(null).texto, 'Sem dados do período anterior')],
  ['variação positiva', () => assert.strictEqual(u.textoVariacao(10).classe, 'text-success')],
  ['variação negativa', () => assert.strictEqual(u.textoVariacao(-5.5).texto, '-5,5% vs. período anterior')],
  ['fim antes do início', () => assert.strictEqual(u.validarDatas('2026-11-10', '2026-11-01', '2026-11-24', 366),
      'A data final não pode ser anterior à data inicial.')],
  ['início no futuro', () => assert.strictEqual(u.validarDatas('2026-11-25', '2026-11-25', '2026-11-24', 366),
      'A data inicial não pode estar no futuro.')],
  ['intervalo maior que 366 dias', () => assert.strictEqual(u.validarDatas('2025-01-01', '2026-11-24', '2026-11-24', 366),
      'O intervalo máximo de consulta é de 366 dias.')],
  ['intervalo de 366 dias aceito', () => assert.strictEqual(u.validarDatas('2025-11-24', '2026-11-24', '2026-11-24', 366), null)],
  ['data faltando', () => assert.strictEqual(u.validarDatas('', '2026-11-24', '2026-11-24', 366),
      'Informe a data inicial e a data final.')],
  ['escape de HTML (XSS)', () => assert.strictEqual(u.escapar('<b>"x"</b>'), '&lt;b&gt;&quot;x&quot;&lt;/b&gt;')],
  ['query com período', () => assert.strictEqual(u.parametros({periodo: 'HOJE'}, {limite: 10}), '?periodo=HOJE&limite=10')],
  ['query com datas', () => assert.strictEqual(u.parametros({inicio: '2026-11-01', fim: '2026-11-24'}),
      '?inicio=2026-11-01&fim=2026-11-24')],
];

let falhas = 0;
for (const [nome, fn] of casos) {
  try { fn(); console.log('  OK    ' + nome); } catch (e) { falhas++; console.log('  FALHA ' + nome + ' -> ' + e.message); }
}
console.log(`Testes executados: ${casos.length}, OK: ${casos.length - falhas}, falhas: ${falhas}`);
process.exit(falhas ? 1 : 0);
