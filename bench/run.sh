#!/bin/bash
# Sobe o sample-app empacotado e roda o LoadGen em cada URL.
# uso: bench/run.sh "<JAVA_OPTS extras>" /url...   (CONN=64 SECS=10 WARM=15)
# antes: ./gradlew :sample-app:installDist :bench:installDist
set -e
cd "$(dirname "$0")/.."
SP=${TMPDIR:-/tmp}
fuser -k 8080/tcp 2>/dev/null || true; sleep 2
JAVA_OPTS="-Dgrails.env=development -Dgrox.gsp.reload=false -Dorg.slf4j.simpleLogger.defaultLogLevel=warn $1" nohup sample-app/build/install/sample-app/bin/sample-app > $SP/bench-app.log 2>&1 &
echo $! > $SP/bench.pid
for i in $(seq 1 120); do curl -s -o /dev/null localhost:8080/actuator/health && break; sleep 0.5; done
TOKEN=$(curl -s -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin"}' localhost:8080/api/login | grep -oP '"access_token":"\K[^"]+')
shift
for u in "$@"; do
  if [[ $u == /api/* ]]; then bench/build/install/bench/bin/bench "http://localhost:8080$u" ${CONN:-64} ${SECS:-10} ${WARM:-15} "Authorization: Bearer $TOKEN"
  else bench/build/install/bench/bin/bench "http://localhost:8080$u" ${CONN:-64} ${SECS:-10} ${WARM:-15}; fi
done
kill $(cat $SP/bench.pid) || true
