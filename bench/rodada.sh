#!/bin/bash
# Rodada padrão do histórico de desempenho (docs/benchmark-historico.md): Grails 8, Grox compat e Grox puro na app
# de livros, em duas ordens intercaladas, e a /usuario/index do sample-app com login. Com BASE=<raiz de outro
# checkout já compilado> mede também essa versão em A/B (base x HEAD), o jeito de achar uma regressão.
# uso: [BASE=dir] bench/rodada.sh > rodada.log; bench/resumo.py rodada.log
# antes (aqui e no BASE, sem o Grails): ./gradlew :bench:installDist :bench:livros:installDist \
#        :sample-plain:installDist :sample-app:installDist && ./gradlew -p bench/livros-grails bootJar
# Rode com a máquina ociosa (load average < 1): o script não espera.
cd "$(dirname "$0")/.."
marca() { echo "##### $1 $(date +%T) load $(cut -d' ' -f1 /proc/loadavg)"; }
roda() {
  marca "$1"
  case $1 in
    grails) bench/run-livros.sh grails ;;
    compat-base) GROX_BIN=$BASE/bench/livros/build/install/livros/bin/livros bench/run-livros.sh grox ;;
    compat) bench/run-livros.sh grox ;;
    puro-base) PLAIN_BIN=$BASE/sample-plain/build/install/sample-plain/bin/sample-plain bench/run-livros.sh plain ;;
    puro) bench/run-livros.sh plain ;;
    logado-base) DIST=$BASE/sample-app/build/install/sample-app bench/run-logado.sh base /usuario/index ;;
    logado) bench/run-logado.sh head /usuario/index ;;
  esac
}
if [ -n "$BASE" ]; then
  IDA="grails compat-base compat puro-base puro"; LOG="logado-base logado logado logado-base"
else
  IDA="grails compat puro"; LOG="logado logado"
fi
VOLTA=$(echo $IDA | tr ' ' '\n' | tac | tr '\n' ' ')
echo "##### commit $(git rev-parse --short HEAD) base ${BASE:+$(git -C "$BASE" rev-parse --short HEAD)} $(java -version 2>&1 | head -1)"
for v in $IDA $VOLTA $LOG; do roda $v; done
