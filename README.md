# PediuPatrão

Sistema de pedidos de uma pizzaria, feito em Spring Boot com MongoDB e Thymeleaf. Este repositório é a minha entrega da Atividade Prática M1 – Parte 2, que pedia para evoluir o projeto original com controle de acesso por perfil, um fluxo de estados para os pedidos, desconto e cancelamento restritos ao gerente e uma auditoria gravada no banco.

**Dashboard Administrativo:** [vídeo da apresentação](https://drive.google.com/file/d/1VM_bzstASb4FyYWvf6m0ndD-8ev8XFum/view?usp=sharing) · [documentação](docs/dashboard.md)

## O que precisa para rodar

- Java 21
- Maven (o `mvnw` já vem no projeto)
- Um MongoDB. Eu usei o Atlas (plano gratuito), mas um Mongo local também funciona.

Spring Boot 3.4.4, Spring Security 6.4, Spring Data MongoDB e Thymeleaf.

## Configuração

Nada de senha fica no código. A aplicação lê variáveis de ambiente, ou um arquivo `.env` na pasta onde você roda o comando. Existe um `.env.example` para copiar:

```
cp .env.example .env
```

Variáveis:

- `MONGODB_URI`: string de conexão. Padrão `mongodb://localhost:27017`. No Atlas é a `mongodb+srv://...` (se a senha tiver `@`, `#` ou `/`, é preciso codificar na URL).
- `MONGODB_DATABASE`: nome do banco. Usei `pizzaria_m1` para não misturar com dados do projeto antigo.
- `MONGODB_TRANSACOES`: `true` só funciona em replica set (o Atlas é). Em Mongo local simples deixe `false`.
- `PEDIU_TIMEZONE`: fuso dos horários gravados. Padrão `America/Sao_Paulo`.
- `PEDIU_ADMIN_USERNAME` e `PEDIU_ADMIN_PASSWORD`: o admin inicial. Só é criado se ainda não existir nenhum ADMIN. Senha com no mínimo 8 caracteres.
- `PEDIU_SEED_ENABLED`, `PEDIU_SEED_GERENTE_PASSWORD`, `PEDIU_SEED_ATENDENTE_PASSWORD`: se `PEDIU_SEED_ENABLED=true`, cria `gerente01` e `atendente01` na primeira subida, com as senhas informadas.

O `.env` está no `.gitignore`. Não suba esse arquivo.

## Rodando

```
./mvnw spring-boot:run
```

Depois é só abrir http://localhost:8080 e entrar com o admin definido no `.env`.

## Testes

```
./mvnw test
```

São 143 testes (97 da parte anterior e 46 do dashboard) e não precisam de Mongo (usam repositórios simulados com Mockito e `@WebMvcTest`). Rodei na minha máquina com Java 21 e todos passaram. Eles cobrem login dos três perfis, 401 e 403 por perfil, desconto e cancelamento bloqueados para ADMIN e ATENDENTE, auditoria bloqueada para ATENDENTE, autoelevação de perfil, cálculo do pedido, limite de 20%, transições válidas e inválidas, entrada e saída, cancelamento com justificativa e bloqueio depois da saída, pedido cancelado imutável, persistência da auditoria e valores antes/depois nas alterações de cliente.

Um aviso para quem ler o log: aparecem uns `ERROR ... Falha ao persistir auditoria` com stack trace durante os testes. É esperado, o teste simula o banco caindo para conferir que a operação é desfeita.

Além dos testes, dá para verificar na prática as restrições do backend chamando a API com cada usuário (login com cookie e token CSRF) e conferindo os códigos 403. Foi isso que mostrei no vídeo.

## Dashboard Administrativo

Painel para o ADMIN e o GERENTE acompanharem as vendas e os pedidos, no item **Dashboard** do menu (`/dashboard`). Mostra total de vendas, ticket médio, cancelados, gráficos de vendas e de status, a situação dos pedidos e os produtos mais vendidos, com filtro por período. Os dados vêm de uma API REST nova, em `/api/dashboard`.

Só conta como venda o pedido finalizado; os cancelados ficam de fora do faturamento.

A documentação completa (requisitos, endpoints, testes e decisões) está em [`docs/dashboard.md`](docs/dashboard.md).

Para testar a API com a aplicação rodando:

```
./testes-dashboard-api.sh gerente01 'SENHA'
```

## Perfis

| | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e perfis | sim | não | não |
| Consultar auditoria | sim | sim | não |
| Ver o dashboard administrativo | sim | sim | não |
| Consultar pedidos | sim | sim | sim |
| Registrar e editar pedidos | não | sim | sim |
| Mudar status e registrar saída | não | sim | sim |
| Aplicar desconto | não | sim | não |
| Cancelar pedido | não | sim | não |
| Consultar clientes | não | sim | sim |
| Cadastrar e editar clientes | não | não | sim |
| Excluir clientes | não | sim | não |
| Manter produtos | sim | sim | não |

A autorização é feita no backend, em duas camadas: regras de rota no `SecurityConfig` e `@PreAuthorize` nos serviços. Os botões escondidos na tela são só conforto, a regra não depende deles. Sem login, a API responde 401; logado sem permissão, 403.

Sobre usuários: só o ADMIN gerencia usuários e só os três perfis existem. As telas e a API usam DTOs, então não dá para mandar um campo a mais e mudar perfil por *mass assignment*. Ninguém altera o próprio perfil ou login, ninguém se exclui, o último ADMIN não pode ser removido ou rebaixado, e as sessões de quem teve o perfil ou a senha alterados são encerradas. A rota `/api/usuarios` do projeto original era pública e foi fechada. O CSRF continua ligado em tudo: os formulários levam o token e os `fetch` das telas mandam o cabeçalho `X-CSRF-TOKEN`.

## Fluxo do pedido

```
RECEBIDO -> EM_PREPARACAO -> PRONTO -> SAIU_PARA_ENTREGA (entrega)
                                    -> RETIRADO (retirada)
                                       -> FINALIZADO
```

`CANCELADO` é uma saída à parte, só do gerente, e só antes da saída.

- A entrada (data, hora e usuário) é gravada na criação, com o relógio e o usuário do servidor. O navegador não manda nenhum desses dados.
- A saída só pode ser registrada com o pedido em `PRONTO`, e grava data, hora e responsável.
- As transições são validadas no `PedidoService`. Por exemplo, não dá para finalizar um pedido que está em preparação.
- Pedido cancelado ou finalizado não aceita mais alteração.
- Cada mudança entra no histórico do pedido (de, para, usuário, perfil e horário).
- Preços e totais são calculados no servidor com `BigDecimal` (2 casas, `HALF_UP`), usando o preço do cadastro de produtos. O que vier de total ou preço na requisição é ignorado. A quantidade por item vai de 1 a 99.
- Tem `@Version` na entidade para evitar duas pessoas sobrescrevendo o mesmo pedido.

## Desconto e cancelamento

Os dois são só do GERENTE e só antes da saída (`RECEBIDO`, `EM_PREPARACAO` ou `PRONTO`).

- **Desconto**: percentual de 0 a 20, com até 2 casas. Um novo desconto substitui o anterior e o valor total é recalculado. Fica registrado na auditoria com o percentual e o total antes e depois.
- **Cancelamento**: a justificativa é obrigatória (5 a 500 caracteres). Nada é apagado, o pedido só muda para `CANCELADO` e guarda quem cancelou, quando e por quê.

Se alguém tentar `PUT /api/pedidos/{id}/status?status=CANCELADO`, o ATENDENTE recebe 403, e o gerente é orientado a usar o endpoint de cancelamento, que exige a justificativa.

## Auditoria

Fica na coleção `auditoria`, com entidade (`AuditoriaEvento`), repositório e serviço próprios. Cada evento guarda o usuário e o perfil dele naquele momento, a data e hora, o tipo de operação, a entidade e o id, os campos alterados com valor anterior e novo, e a justificativa quando existe.

O que é auditado: criação e alteração de pedidos, mudanças de status, entrada e saída, descontos, cancelamentos, criação, alteração e exclusão de clientes (telefone e endereço aparecem campo a campo) e a administração de usuários.

Como funciona por dentro:

- O usuário vem do contexto de segurança, nunca da requisição.
- O repositório só expõe gravar e buscar por id. Não existe endpoint nem tela para editar ou apagar evento.
- A consulta (`/auditoria` e `GET /api/auditoria`) é só para ADMIN e GERENTE.
- Senha, hash e token são removidos antes de gravar, e nada disso vai para o log.
- Se a auditoria falha, a operação não é confirmada como feita. Com `MONGODB_TRANSACOES=true` as duas coisas ficam na mesma transação. Sem transação (Mongo simples) eu desfaço a operação de negócio por compensação e devolvo erro. Se até o desfazer falhar, a mensagem avisa que a operação pode ter ficado sem registro.

## Principais mudanças

- **Segurança**: perfis ADMIN, GERENTE e ATENDENTE, `UsuarioDetailsService` por perfil, `SecurityConfig` com regras por rota e por método, 401 nas APIs, sessões registradas, senhas do banco movidas para variáveis de ambiente.
- **Pedidos**: entidade nova com itens, preços congelados, desconto, total, entrada, saída, cancelamento e histórico. Serviço com todas as regras e DTOs de entrada sem campos financeiros.
- **Auditoria**: entidade, repositório, serviço, tela e API de consulta.
- **Clientes**: validação e auditoria. Cliente que já tem pedido não pode ser excluído.
- **Usuários**: serviço com validação e as proteções de perfil descritas acima.
- **Telas**: menu por perfil, logout por POST, telas de pedidos (lista, novo, editar, detalhe com ações e histórico) e de auditoria. A tela de produtos original não carregava e foi refeita. Removi a rota `/teste` e os links do menu que apontavam para páginas que não existem.

## Decisões que tomei

O enunciado deixa algumas coisas em aberto, então registro o que escolhi:

- O ADMIN não acessa clientes. Pelo texto, ele administra usuários e vê pedidos e auditoria.
- O GERENTE só consulta clientes (e pode excluir); quem cadastra e edita é o ATENDENTE.
- Produtos são mantidos por ADMIN e GERENTE.
- Pedidos do modelo antigo ficam só para leitura, porque o formato mudou.

Limitações que conheço: testei as telas no Chrome, em mais nenhum navegador; a auditoria grava telefone e endereço em texto porque é isso que ela precisa mostrar (só ADMIN e GERENTE veem); e com `MONGODB_TRANSACOES=false` a consistência com a auditoria é por compensação, não atômica.

## Preparando usuários de teste com segurança

1. Coloque as senhas no `.env`, nunca no código.
2. Suba a aplicação. O `DataInitializer` cria o ADMIN (se não existir) e, com `PEDIU_SEED_ENABLED=true`, o `gerente01` e o `atendente01`, com senha em BCrypt. Nenhuma senha vai para o log.
3. Sem o seed, dá para entrar como ADMIN e criar os usuários pela tela **Usuários**.
4. Depois de testar, desligue `PEDIU_SEED_ENABLED` e troque ou apague as contas de teste.
5. Se o banco já tinha usuários do projeto antigo, eles são migrados: o `admin` antigo vira ADMIN e os demais viram ATENDENTE (o menor privilégio). Quem tiver perfil desconhecido fica desabilitado. Se o `admin` antigo ainda estiver com a senha original do projeto, troque.
