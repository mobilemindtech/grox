#!/bin/bash
# Benchmark Grails x Grox (docs/benchmark.md): a mesma app de livros em três versões, uma de cada vez na porta 8080.
#   grails: bench/livros-grails (Grails 8, jar do bootJar)
#   grox:   bench/livros (o mesmo grails-app/ no Grox, com o módulo Grails)
#   plain:  sample-plain (Grox sem o módulo Grails: rotas do grox-dsl)
# PLAIN_BIN e GROX_BIN trocam o executável do sample-plain e do bench/livros (A/B de duas versões).
# uso: bench/run-livros.sh grails|grox|plain [url...]   (CONN=64 SECS=10 WARM=20; CONN=1 mede a latência sem fila)
# antes: ./gradlew :bench:installDist :bench:livros:installDist :sample-plain:installDist
#        ./gradlew -p bench/livros-grails bootJar
set -e
cd "$(dirname "$0")/.."
SP=${TMPDIR:-/tmp}
APP=$1; shift
URLS=("$@"); [ ${#URLS[@]} -eq 0 ] && URLS=(/ola/mundo /livros /api/livros/7)
fuser -k 8080/tcp 2>/dev/null || true; sleep 2
OPTS="-Dgrails.env=production -Dorg.slf4j.simpleLogger.defaultLogLevel=warn $JAVA_EXTRA"
case $APP in
  grails) nohup java $OPTS -jar bench/livros-grails/build/libs/livros-grails-1.0.jar > $SP/bench-$APP.log 2>&1 & ;;
  grox)   JAVA_OPTS="$OPTS" nohup ${GROX_BIN:-bench/livros/build/install/livros/bin/livros} > $SP/bench-$APP.log 2>&1 & ;;
  plain)  JAVA_OPTS="$OPTS" nohup ${PLAIN_BIN:-sample-plain/build/install/sample-plain/bin/sample-plain} > $SP/bench-$APP.log 2>&1 & ;;
  *) echo "uso: $0 grails|grox|plain [url...]" >&2; exit 2 ;;
esac
PID=$!
inicio=$(date +%s%N)
until curl -s -o /dev/null localhost:8080/ola/x; do sleep 0.2; done
echo "$APP: respondendo em $(( ($(date +%s%N) - inicio) / 1000000 )) ms (pid $PID)"
# o sample-plain não tem BootStrap: os mesmos 20 livros do BootStrap do bench/livros, pela API
if [ "$APP" = plain ]; then
  for n in $(seq 1 20); do
    curl -s -o /dev/null -u admin:admin -H 'Content-Type: application/json' \
      -d "{\"titulo\":\"$(printf 'Livro %02d' $n)\",\"autor\":\"Autor $((n % 5))\",\"ano\":$((1990 + n))}" localhost:8080/api/livros
  done
fi
# o Grails atende antes de o BootStrap terminar
until [ "$(curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/livros/20)" = 200 ]; do sleep 0.2; done
for u in "${URLS[@]}"; do
  echo "== $APP $u"
  bench/build/install/bench/bin/bench "http://localhost:8080$u" ${CONN:-64} ${SECS:-10} ${WARM:-20}
  # com 1 conexão (já aquecido): o custo de cada requisição, sem fila
  bench/build/install/bench/bin/bench "http://localhost:8080$u" 1 ${SECS:-10} 3 | tail -2
done
ps -o rss= -p $PID | awk '{printf "RSS no fim: %d MB\n", $1/1024}'
kill $PID || true
