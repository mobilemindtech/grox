#!/bin/bash
# Mede páginas que exigem login: sobe o sample-app empacotado, faz login por formulário (sessão, sem o bcrypt
# do Basic a cada requisição) e roda o LoadGen com o cookie. Mostra também o tempo de CPU do processo por
# requisição, menos sensível a outros processos na máquina que a vazão.
# uso: bench/run-logado.sh rotulo /url "<JAVA_OPTS extras>"   (CONN="1 64" SECS=10 WARM=30 JIT=60 DIST=diretório do installDist)
# JIT: aquecimento inicial com 64 conexões, antes de tudo. Sem ele, a primeira medição (1 conexão) ainda pega o JIT
# compilando: o p50 da /usuario/index dava 5 a 6 ms em vez de ~2 ms.
# antes: ./gradlew :sample-app:installDist :bench:installDist
cd "$(dirname "$0")/.."
SP=${TMPDIR:-/tmp}
D=${DIST:-sample-app/build/install/sample-app}
URL=${2:-/usuario/index}
fuser -k 8080/tcp 2>/dev/null; sleep 1
JAVA_OPTS="-Dgrails.env=development -Dgrox.gsp.reload=false -Dorg.slf4j.simpleLogger.defaultLogLevel=warn $3" nohup $D/bin/sample-app > $SP/bench-$1.log 2>&1 &
PID=$!
for i in $(seq 1 240); do curl -s -o /dev/null localhost:8080/actuator/health && break; sleep 0.5; done
J=$SP/bench-cookies-$1.txt; rm -f $J
CSRF=$(curl -s -c $J -b $J localhost:8080/login/auth | grep -oP 'name="_csrf" value="\K[^"]+')
curl -s -o /dev/null -c $J -b $J --data-urlencode "username=admin" --data-urlencode "password=admin" --data-urlencode "_csrf=$CSRF" localhost:8080/login/authenticate
COOKIE=$(awk '!/^#/ && NF>=7 {print $6"="$7}' $J | head -1)
test "$(curl -s -o /dev/null -w '%{http_code}' -H "Cookie: $COOKIE" localhost:8080$URL)" = 200 || { echo "login falhou"; kill $PID; exit 1; }
JPID=$(pgrep -f "$D/lib" | head -1)
cpu() { awk '{print $14+$15}' /proc/$JPID/stat; }
TCK=$(getconf CLK_TCK)
bench/build/install/bench/bin/bench "http://localhost:8080$URL" 64 ${JIT:-60} 0 "Cookie: $COOKIE" > /dev/null
for c in ${CONN:-1 64}; do
  bench/build/install/bench/bin/bench "http://localhost:8080$URL" $c ${WARM:-30} 0 "Cookie: $COOKIE" > /dev/null
  C0=$(cpu)
  R=$(bench/build/install/bench/bin/bench "http://localhost:8080$URL" $c ${SECS:-10} 0 "Cookie: $COOKIE")
  C1=$(cpu)
  N=$(echo "$R" | grep -oP 'requisições: \K[0-9]+')
  echo "$R" | sed "s/^/[$1 c=$c] /"
  echo "[$1 c=$c]   CPU ms/req: $(echo "scale=2; ($C1-$C0)*1000/$TCK/$N" | bc)"
done
kill $PID; wait $PID 2>/dev/null
