# Roteiro do vídeo (≈ 6–8 min, gravação de tela)

**Preparação (fora do vídeo):** MongoDB no ar; `.env` com `PEDIU_ADMIN_PASSWORD`, `PEDIU_SEED_ENABLED=true` e senhas de `gerente01`/`atendente01`;
aplicação rodando; ao menos 1 cliente e 2 produtos cadastrados (o ATENDENTE cadastra o cliente; ADMIN/GERENTE cadastram produtos).
Tenha duas janelas do navegador (ou uma anônima) para alternar de perfil.

1. **Abertura (15 s)** — mostre o nome/RA na tela e o README. Diga que são 3 perfis: ADMIN, GERENTE, ATENDENTE.
2. **Login dos 3 perfis (1 min)** — entre como `admin`, `gerente01` e `atendente01` (um por vez, "Sair" entre eles). Destaque no menu lateral o que cada um vê (ADMIN: Usuários + Auditoria; GERENTE: Clientes + Auditoria; ATENDENTE: Clientes + Pedidos, sem Auditoria).
3. **ADMIN — usuários e perfis (1 min)** — crie um usuário `atendente02` com perfil ATENDENTE; tente editar o **próprio** usuário (campo de perfil bloqueado). Mostre que, ao alterar o perfil de outro usuário, a sessão dele é encerrada.
4. **Tentativas de acesso indevido (1,5 min)** — logado como `atendente01`, abra o console do navegador (F12 → Console) em qualquer página do sistema e execute:
   ```js
   // 1) ATENDENTE tentando consultar a auditoria  → 403
   fetch('/api/auditoria').then(r => console.log('auditoria', r.status));
   // 2) ATENDENTE tentando aplicar desconto num pedido  → 403   (troque ID pelo id real do pedido)
   fetch('/api/pedidos/ID/desconto', {method:'POST', headers: csrfHeaders({'Content-Type':'application/json'}), body: JSON.stringify({percentual: 10})}).then(r => console.log('desconto', r.status));
   // 3) ATENDENTE tentando cancelar  → 403
   fetch('/api/pedidos/ID/cancelar', {method:'POST', headers: csrfHeaders({'Content-Type':'application/json'}), body: JSON.stringify({justificativa: 'teste de acesso'})}).then(r => console.log('cancelar', r.status));
   // 4) ATENDENTE tentando se promover a ADMIN  → 403
   fetch('/api/usuarios/ID', {method:'PUT', headers: csrfHeaders({'Content-Type':'application/json'}), body: JSON.stringify({username:'atendente01', role:'ADMIN'})}).then(r => console.log('promover', r.status));
   // 5) Sem token CSRF → 403
   fetch('/api/pedidos/ID/saida', {method:'POST'}).then(r => console.log('sem csrf', r.status));
   ```
   Repita 2 e 3 como `admin` (também 403: nem o ADMIN aplica desconto ou cancela) e mostre que `gerente01` consegue (200).
   Mostre também a URL `/auditoria` como ATENDENTE → página "Acesso negado".
5. **ATENDENTE — cliente e pedido (1,5 min)** — altere **telefone e endereço** de um cliente (anote os valores antigos e novos). Registre um pedido (cliente, tipo ENTREGA, 2 itens). Mostre o detalhe: status RECEBIDO, entrada com data/hora e `atendente01`, totais calculados pelo servidor.
6. **Transições e saída (1 min)** — avance RECEBIDO → EM_PREPARACAO → PRONTO. Tente via console uma transição inválida: `fetch('/api/pedidos/ID/status?status=FINALIZADO',{method:'PUT',headers:csrfHeaders()}).then(r=>r.status)` → 409. Registre a **saída** e finalize; mostre saída (data/hora + responsável) e o histórico.
7. **GERENTE — desconto e cancelamento (1,5 min)** — em outro pedido ainda não despachado: aplique 10% (total recalculado); tente 25% (erro: limite 20%); cancele com justificativa; mostre que o pedido cancelado não tem mais ações; tente cancelar um pedido que já saiu (bloqueado).
8. **Auditoria (1,5 min)** — como `gerente01` (e depois `admin`), abra **Auditoria**: mostre os eventos da demonstração — criação do pedido, mudanças de status, saída, desconto (percentual/total antes → depois), cancelamento (com justificativa) e a **alteração do cliente** (telefone e logradouro: valor anterior → novo, usuário `atendente01`, perfil e horário). Mostre que não há botões de editar/excluir registros.
9. **Encerramento (15 s)** — rode `./mvnw test` (ou mostre o resultado) e aponte o README.
