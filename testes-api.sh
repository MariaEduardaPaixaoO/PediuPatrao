#!/usr/bin/env bash
# Uso: ./testes-api.sh USUARIO SENHA ID_DO_PEDIDO
BASE=http://localhost:8080
U="$1"; P="$2"; ID="$3"
JAR=$(mktemp)

TOKEN=$(curl -s -c "$JAR" "$BASE/login" | grep -oP 'name="_csrf"\s+value="\K[^"]+')
[ -z "$TOKEN" ] && { echo "Nao achei o token do login"; exit 1; }

LOGIN=$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w "%{http_code}" \
  --data-urlencode "username=$U" --data-urlencode "password=$P" --data-urlencode "_csrf=$TOKEN" "$BASE/login")
echo "login $U -> HTTP $LOGIN (esperado 302)"

CSRF=$(curl -s -b "$JAR" -c "$JAR" "$BASE/pedidos" | grep -oP 'name="_csrf"\s+content="\K[^"]+')
[ -z "$CSRF" ] && { echo "Nao achei o token CSRF da sessao"; exit 1; }

st() { curl -s -b "$JAR" -o /dev/null -w "%{http_code}" "$@"; }
echo "GET  /api/auditoria        -> $(st "$BASE/api/auditoria")"
echo "GET  /api/usuarios         -> $(st "$BASE/api/usuarios")"
echo "POST desconto 10%          -> $(st -X POST -H "X-CSRF-TOKEN: $CSRF" -H 'Content-Type: application/json' -d '{"percentual":10}' "$BASE/api/pedidos/$ID/desconto")"
echo "POST cancelar              -> $(st -X POST -H "X-CSRF-TOKEN: $CSRF" -H 'Content-Type: application/json' -d '{"justificativa":"teste de acesso"}' "$BASE/api/pedidos/$ID/cancelar")"
echo "POST saida SEM token CSRF  -> $(st -X POST "$BASE/api/pedidos/$ID/saida")"
rm -f "$JAR"
