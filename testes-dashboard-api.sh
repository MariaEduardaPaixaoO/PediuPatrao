#!/usr/bin/env bash
# Chama a API do dashboard com um usuário real e mostra os códigos HTTP e as respostas.
# Uso: ./testes-dashboard-api.sh USUARIO SENHA   (a aplicação deve estar rodando em localhost:8080)
BASE=${BASE:-http://localhost:8080}
U="$1"; P="$2"
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

echo "GET  /api/dashboard/resumo sem login -> $(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/dashboard/resumo") (esperado 401)"

TOKEN=$(curl -s -c "$JAR" "$BASE/login" | grep -oP 'name="_csrf"\s+value="\K[^"]+')
[ -z "$TOKEN" ] && { echo "Não achei o token do login"; exit 1; }
LOGIN=$(curl -s -b "$JAR" -c "$JAR" -o /dev/null -w "%{http_code}" \
  --data-urlencode "username=$U" --data-urlencode "password=$P" --data-urlencode "_csrf=$TOKEN" "$BASE/login")
echo "login $U -> HTTP $LOGIN (esperado 302)"

st()   { curl -s -b "$JAR" -o /dev/null -w "%{http_code}" "$@"; }
show() { echo; echo "== GET $1"; curl -s -b "$JAR" -w "\n[HTTP %{http_code}]\n" "$BASE$1"; }

show "/api/dashboard/resumo?periodo=ULTIMOS_30_DIAS"
show "/api/dashboard/vendas?periodo=ULTIMOS_7_DIAS"
show "/api/dashboard/pedidos/status?periodo=MES_ATUAL"
show "/api/dashboard/produtos-mais-vendidos?periodo=ULTIMOS_30_DIAS&limite=5"

echo; echo "== Casos de erro (esperado 400)"
show "/api/dashboard/resumo?inicio=2026-11-10&fim=2026-11-01"
show "/api/dashboard/resumo?inicio=24/11/2026&fim=2026-11-24"
show "/api/dashboard/resumo?periodo=SEMPRE"
show "/api/dashboard/produtos-mais-vendidos?limite=abc"

echo; echo "POST /api/dashboard/resumo -> $(st -X POST "$BASE/api/dashboard/resumo") (esperado 403)"
