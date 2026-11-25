# Dashboard Administrativo

Esta parte da entrega acrescenta ao Pediu Patrão um painel para quem administra a pizzaria acompanhar as vendas e os pedidos. O painel consome uma API REST que eu criei para ele, e os dados vêm do MongoDB, das mesmas coleções que o sistema já usava.

Não criei entidade nem coleção nova. Tudo é calculado a partir dos pedidos que o `PedidoService` já grava: itens com preço congelado, desconto, valor total, status e data de entrada. A única mudança no banco é um índice em `pedidos.dataHoraEntrada`, criado na inicialização.

O código está na branch `feature/dashboard-administrativo-api`, criada a partir de `develop`.

## Requisitos funcionais

**RF01 – Indicadores gerais de vendas.** Total de vendas, quantidade de pedidos, ticket médio, quantidade de cancelados (com a taxa e o valor que deixou de entrar) e pedidos em andamento. Cada indicador mostra a variação em relação ao período anterior de mesmo tamanho. Se o período anterior não tem dados, aparece "Sem dados do período anterior" em vez de uma porcentagem.

**RF02 – Análise de vendas.** Três gráficos:

- evolução das vendas, com o período anterior tracejado por cima, para comparar;
- quantidade de pedidos por dia, separando finalizados, em andamento e cancelados;
- distribuição dos pedidos por status.

Para períodos de mais de 62 dias, os gráficos passam a agrupar por mês.

**RF03 – Situação dos pedidos.** Quantidade e porcentagem de pedidos em cada um dos sete status do sistema: recebido, em preparação, pronto, saiu para entrega, retirado, finalizado e cancelado. Os status zerados também aparecem. Não criei nenhum status novo.

**RF04 – Produtos mais vendidos.** Ranking com nome, quantidade vendida e valor gerado por produto. Usa o nome e o preço gravados no item de cada pedido, então uma mudança de preço no cadastro não altera o histórico. Dá para escolher entre top 5, 10 ou 20.

**RF05 – Filtros.** Hoje, últimos 7 dias, últimos 30 dias (o padrão), mês atual ou um intervalo com data inicial e final. Ao mudar o filtro, todos os blocos são atualizados. As datas são validadas na tela e de novo no servidor.

**RF06 – Integração com a API.** A página não recebe nenhum número pronto do servidor. Ela chama os quatro endpoints descritos mais abaixo, que consultam o MongoDB pelo `PedidoRepository`.

**RF07 – Atualização e tratamento dos dados.** Tem botão de atualizar e uma opção de atualização automática a cada 5 minutos (desligada por padrão, e só consulta com a aba aberta). Cada bloco mostra "Carregando…" enquanto espera, uma mensagem quando não há dados e outra quando dá erro. Valores aparecem em reais (R$ 1.234,56), datas em dd/mm/aaaa e valores nulos como "—".

## Regras de negócio

- Só conta como venda o pedido **FINALIZADO**. É o único que não pode mais ser cancelado nem alterado, então é o único valor que dá para chamar de faturado.
- Pedido cancelado nunca entra no total de vendas, no ticket médio nem no ranking. Ele aparece só no indicador de cancelados.
- Pedidos em recebido, em preparação, pronto, saiu para entrega ou retirado aparecem como "em andamento".
- A data que vale é a de entrada do pedido, que é gravada pelo servidor. O filtro inclui o dia inicial e o final inteiros.
- O valor de cada venda é o `valorTotal` gravado no pedido, já com desconto. O dashboard não recalcula preço nenhum; essa regra continua só no `PedidoService`.
- Ticket médio é o total de vendas dividido pela quantidade de pedidos finalizados. Sem vendas, ele fica vazio, não zero.
- No ranking, o desconto do pedido é dividido entre os itens na proporção do valor de cada um. Assim a soma do ranking bate com o total de vendas.
- Pedidos do modelo antigo, com status que não existe mais, contam como pedido, mas não entram em nenhum status. A API informa quantos são.

## Requisitos não funcionais

**RNF01 – Segurança e controle de acesso.** Só ADMIN e GERENTE acessam o dashboard, a mesma regra que eu já usava para a auditoria, porque é informação gerencial. A regra está nas rotas (`SecurityConfig`) e no serviço (`@PreAuthorize`). Sem login, a API responde 401 e a página manda para o login. O ATENDENTE recebe 403 e não vê o item no menu. A API só aceita GET. Não mudei nenhuma permissão que já existia.

**RNF02 – Responsividade.** No computador, os quatro cards ficam numa linha; no tablet, em duas; no celular, um embaixo do outro. Os gráficos ocupam a largura disponível, e as colunas menos importantes do ranking somem em telas pequenas.

**RNF03 – Desempenho.** O filtro por data é feito no MongoDB, usando o índice. O histórico de transições dos pedidos não é carregado, porque o dashboard não usa. O intervalo máximo de consulta é de 366 dias, e cada endpoint faz no máximo duas consultas (o período e o período anterior).

**RNF04 – Organização do código.** Segui as camadas do projeto: controller, service e repository. Os cálculos ficam na `DashboardCalculadora`, que não depende do Spring nem do banco e por isso é fácil de testar. As respostas da API são DTOs em `dto/dashboard`. A tela tem arquivos próprios: `dashboard.html`, `dashboard.js` e `dashboard.css`.

**RNF05 – Consistência dos dados.** A API não altera nada no banco. Os valores usam `BigDecimal` com 2 casas e arredondamento `HALF_UP`, igual ao `PedidoService`. As datas como "hoje" e "mês atual" vêm do relógio do servidor, não do navegador.

**RNF06 – Tratamento de erros.** Um filtro inválido responde 400 com a mensagem do problema, no mesmo formato de erro das outras APIs do projeto. Se o MongoDB não responder, a API devolve 503 com uma mensagem genérica, sem mostrar dados da conexão. Na tela, um bloco com erro não impede os outros de carregar.

**RNF07 – Usabilidade e acessibilidade.** Todos os campos têm rótulo, os erros são anunciados para leitores de tela e os gráficos têm uma descrição em texto com os números principais. As cores sempre vêm acompanhadas de texto.

## Modelo de dados

O dashboard lê só a coleção `pedidos`. Estes são os campos que ele usa:

| Campo | Onde | Uso no dashboard |
|---|---|---|
| `dataHoraEntrada` | `Pedido` | filtro por período e agrupamento por dia ou mês |
| `status` | `Pedido` | separa vendas, cancelados e pedidos em andamento |
| `valorTotal` | `Pedido` | valor da venda, já com desconto |
| `subtotal` | `Pedido` | base para dividir o desconto entre os itens no ranking |
| `itens` | `Pedido` | lista de `ItemPedido` |
| `produtoId`, `nomeProduto` | `ItemPedido` | agrupa e identifica o produto no ranking |
| `quantidade`, `precoUnitario`, `subtotal` | `ItemPedido` | quantidade vendida e valor, com o preço da época da venda |

O campo `historico` do pedido não é usado e fica de fora da consulta. Para a busca por período não varrer a coleção inteira, criei o índice `idx_pedidos_dataHoraEntrada`.

As respostas da API são DTOs, separados das entidades, em `dto/dashboard`. Cada endpoint tem o seu: `ResumoDashboardResponse`, `VendasPeriodoResponse`, `StatusPedidosResponse` e `ProdutosMaisVendidosResponse`. Assim a API devolve só os números calculados, sem expor dados de cliente, endereço ou usuário.




## API

Todos os endpoints são GET, ficam em `/api/dashboard` e usam a sessão do login normal do sistema.

### Parâmetros de período

Valem para os quatro endpoints:

| Parâmetro | Formato | Descrição |
|---|---|---|
| `periodo` | texto | `HOJE`, `ULTIMOS_7_DIAS`, `ULTIMOS_30_DIAS` ou `MES_ATUAL` |
| `inicio` | `AAAA-MM-DD` | primeiro dia, usado junto com `fim` |
| `fim` | `AAAA-MM-DD` | último dia, usado junto com `inicio` |

Sem nenhum parâmetro, vale os últimos 30 dias. Não dá para mandar `periodo` junto com as datas. Datas no futuro e intervalos maiores que 366 dias são recusados.

### Erros

| Código | Quando |
|---|---|
| 400 | data em formato errado, data final antes da inicial, data no futuro, só uma das datas, intervalo acima de 366 dias, período desconhecido, `periodo` junto com datas ou `limite` inválido |
| 401 | sem login |
| 403 | perfil ATENDENTE, ou método diferente de GET |
| 503 | o MongoDB não respondeu |

O corpo do erro segue o padrão do projeto:

```json
{ "status": 400, "mensagem": "A data final não pode ser anterior à data inicial.", "timestamp": "2026-11-24T19:58:03.412" }
```

### GET /api/dashboard/resumo

Indicadores gerais (RF01).

```
GET /api/dashboard/resumo?periodo=HOJE
```

```json
{
  "periodo": { "tipo": "HOJE", "inicio": "2026-11-24", "fim": "2026-11-24", "dias": 1 },
  "totalVendas": 154.00,
  "quantidadeVendas": 2,
  "quantidadePedidos": 4,
  "ticketMedio": 77.00,
  "quantidadeCancelados": 1,
  "valorCancelado": 150.00,
  "taxaCancelamentoPercentual": 25.00,
  "pedidosEmAndamento": 1,
  "valorEmAndamento": 26.00,
  "pedidosNaoClassificados": 0,
  "comparacao": {
    "periodoAnterior": { "tipo": "ANTERIOR", "inicio": "2026-11-23", "fim": "2026-11-23", "dias": 1 },
    "totalVendasAnterior": 120.00,
    "quantidadePedidosAnterior": 3,
    "quantidadeVendasAnterior": 2,
    "ticketMedioAnterior": 60.00,
    "quantidadeCanceladosAnterior": 0,
    "variacaoVendasPercentual": 28.33,
    "variacaoPedidosPercentual": 33.33,
    "variacaoTicketMedioPercentual": 28.33,
    "dadosSuficientes": true
  },
  "geradoEm": "2026-11-24T19:58:03.412"
}
```

`totalVendas` e `quantidadeVendas` consideram só os finalizados, e `quantidadePedidos` conta todos. As variações vêm `null` quando o período anterior é zero.

### GET /api/dashboard/vendas

Dados dos gráficos (RF02). Traz um ponto por dia (ou por mês, acima de 62 dias), inclusive os dias sem pedido, e os pontos do período anterior na mesma ordem.

```
GET /api/dashboard/vendas?inicio=2026-11-23&fim=2026-11-24
```

```json
{
  "periodo": { "tipo": "PERSONALIZADO", "inicio": "2026-11-23", "fim": "2026-11-24", "dias": 2 },
  "periodoAnterior": { "tipo": "ANTERIOR", "inicio": "2026-11-21", "fim": "2026-11-22", "dias": 2 },
  "agrupamento": "DIA",
  "pontos": [
    { "rotulo": "23/11", "inicio": "2026-11-23", "totalVendas": 0.00, "quantidadeVendas": 0, "quantidadePedidos": 0, "quantidadeCancelados": 0 },
    { "rotulo": "24/11", "inicio": "2026-11-24", "totalVendas": 154.00, "quantidadeVendas": 2, "quantidadePedidos": 4, "quantidadeCancelados": 1 }
  ],
  "pontosAnterior": [
    { "rotulo": "21/11", "inicio": "2026-11-21", "totalVendas": 210.00, "quantidadeVendas": 3, "quantidadePedidos": 3, "quantidadeCancelados": 0 },
    { "rotulo": "22/11", "inicio": "2026-11-22", "totalVendas": 98.00, "quantidadeVendas": 1, "quantidadePedidos": 2, "quantidadeCancelados": 1 }
  ],
  "totalVendas": 154.00,
  "totalVendasAnterior": 308.00
}
```

### GET /api/dashboard/pedidos/status

Situação dos pedidos (RF03). Sempre vêm os sete status, na ordem do fluxo.

```
GET /api/dashboard/pedidos/status?periodo=HOJE
```

```json
{
  "periodo": { "tipo": "HOJE", "inicio": "2026-11-24", "fim": "2026-11-24", "dias": 1 },
  "total": 4,
  "itens": [
    { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 0, "percentual": 0.00 },
    { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 1, "percentual": 25.00 },
    { "status": "PRONTO", "descricao": "Pronto", "quantidade": 0, "percentual": 0.00 },
    { "status": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega", "quantidade": 0, "percentual": 0.00 },
    { "status": "RETIRADO", "descricao": "Retirado", "quantidade": 0, "percentual": 0.00 },
    { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 2, "percentual": 50.00 },
    { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 1, "percentual": 25.00 }
  ],
  "naoClassificados": 0
}
```

### GET /api/dashboard/produtos-mais-vendidos

Ranking (RF04). Além do período, aceita `limite`, de 1 a 50 (padrão 10).

```
GET /api/dashboard/produtos-mais-vendidos?periodo=HOJE&limite=2
```

```json
{
  "periodo": { "tipo": "HOJE", "inicio": "2026-11-24", "fim": "2026-11-24", "dias": 1 },
  "criterio": "Itens de pedidos FINALIZADOS",
  "limite": 2,
  "totalProdutos": 3,
  "itens": [
    { "posicao": 1, "produtoId": "pz1", "nome": "Pizza Calabresa", "quantidadeVendida": 2,
      "valorBruto": 100.00, "valorLiquido": 100.00, "participacaoPercentual": 64.94 },
    { "posicao": 2, "produtoId": "pz2", "nome": "Pizza Margherita", "quantidadeVendida": 1,
      "valorBruto": 45.00, "valorLiquido": 40.50, "participacaoPercentual": 26.30 }
  ]
}
```

`valorBruto` é o preço gravado no item vezes a quantidade, e `valorLiquido` é o mesmo valor depois de dividir o desconto do pedido. A ordem é por quantidade vendida; no empate, vale o maior valor. `totalProdutos` conta todos os produtos vendidos no período, antes de aplicar o limite.

## Testes

Para rodar:

```
./mvnw test
```

Não precisa de MongoDB: os testes usam o repositório simulado com Mockito, como os que o projeto já tinha.

Rodei em 25/11/2026, com Java 21 e Maven 3.9.9:

```
Tests run: 143, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

São os 97 testes que o projeto já tinha, que continuam passando, mais 46 novos. A saída completa está em `docs/evidencias/execucao-mvn-test.txt`.

| Classe | Testes | O que verifica |
|---|---|---|
| `FiltroPeriodoTest` | 9 | períodos predefinidos, intervalo personalizado, período anterior e as datas inválidas |
| `DashboardCalculadoraTest` | 14 | indicadores, comparação, séries por dia e por mês, status, ranking, ausência de dados e valores nulos |
| `DashboardServiceTest` | 5 | datas pelo relógio do servidor, intervalos consultados no banco e limite do ranking |
| `DashboardAutorizacaoTest` | 4 | `@PreAuthorize`: ATENDENTE e usuário sem login barrados; ADMIN e GERENTE liberados |
| `DashboardWebTest` | 14 | os endpoints de ponta a ponta: 401, 403, respostas com dados e sem dados, erros 400 e 503 |

Alguns casos, com a entrada e o que era esperado:

| Entrada | Esperado | Resultado |
|---|---|---|
| Pedidos de R$ 100,00, R$ 60,00 com 10% e R$ 80,00 finalizados, um de R$ 250,00 cancelado | total de vendas R$ 234,00, ticket médio R$ 78,00, 1 cancelado | OK |
| Período atual com R$ 234,00 e anterior com R$ 150,00 | variação de +56,00% | OK |
| Nenhum pedido no período | totais zerados, ticket médio e variação vazios | OK |
| Mesmo produto vendido a R$ 40,00 e depois a R$ 55,00 | ranking soma R$ 95,00 | OK |
| Pedido cancelado com 5 pizzas | não entra no ranking | OK |
| `inicio=2026-11-10&fim=2026-11-01` | 400, "A data final não pode ser anterior à data inicial." | OK |
| `inicio=2026-13-01` | 400, "Data inicial inválida. Use o formato AAAA-MM-DD." | OK |
| Intervalo de 01/01/2025 a 24/11/2026 | 400, intervalo máximo de 366 dias | OK |
| `limite=abc` e `limite=51` | 400 | OK |
| Chamada sem login | 401 nos quatro endpoints | OK |
| Usuário ATENDENTE | 403 na API e na página | OK |
| POST em `/api/dashboard/resumo` | 403 | OK |
| MongoDB fora do ar | 503, sem mostrar dados da conexão | OK |

Também tem um teste em JavaScript das funções de formatação e validação da tela (`node src/test/js/dashboard.test.js`), e o script `testes-dashboard-api.sh`, que faz o login e chama a API com um usuário real:

```
./testes-dashboard-api.sh gerente01 'SENHA'
```

### Teste com a aplicação rodando

| O que fiz | Esperado | Resultado |
|---|---|---|
| Entrei como `gerente01` e abri o Dashboard | cards, gráficos, situação e ranking carregados | OK |
| Criei pedidos, finalizei três e cliquei em Atualizar | total de vendas e pedidos aumentam | OK: R$ 122,15 em 3 vendas, 5 pedidos |
| Cancelei um pedido | cancelados aumentam e o total de vendas não muda | OK: 1 cancelado (R$ 70,00), fora das vendas |
| Troquei entre Hoje, 7 dias e Mês atual | todos os blocos mudam | OK |
| Coloquei a data final antes da inicial | aparece o aviso e nada é consultado | OK |
| Entrei como `atendente01` e abri `/dashboard` | 403, sem o item no menu | OK |

Prints em `docs/img/`.

## Decisões que tomei

- **Quem vê o dashboard.** O dashboard é para ADMIN e GERENTE, pelo mesmo motivo da auditoria. O ATENDENTE cuida dos pedidos do dia e não precisa ver faturamento.
- **O que conta como venda.** Considerei venda só o pedido finalizado. Contar pedidos ainda em andamento inflaria o faturamento com pedidos que podem ser cancelados.
- **Onde ficam filtro e cálculo.** O filtro por data é feito no banco, com índice, e as somas são feitas em Java. Para o volume de uma pizzaria com limite de 366 dias isso não pesa, e deixa as regras fáceis de ler e testar sem banco.
- **Quatro endpoints em vez de um.** Ficou mais organizado e permite recarregar só o ranking quando se troca o top 5/10/20. A tela chama os quatro ao mesmo tempo.
- **Biblioteca dos gráficos.** Usei o Chart.js que já vinha no projeto (`static/plugins`), sem adicionar dependência.
- **Datas como texto.** O controller recebe as datas como texto para devolver uma mensagem clara em português quando o formato está errado, em vez do erro genérico de conversão do Spring.
- **Desconto no ranking.** Dividi o desconto entre os itens para que o ranking feche com o total de vendas.

Limitações que conheço: testei a tela só no Chrome. Os gráficos por dia dependem de ter pedidos em dias diferentes, e como a data de entrada é gravada pelo servidor, isso só acontece com o uso ao longo do tempo. A atualização automática é por intervalo, não em tempo real.
