#!/bin/bash
# Memória Grails x Grox (docs/benchmark.md): uso: bench/run-mem.sh grails|grox|plain XMX [SECS]  (EXTRA=-D... soma opções)
# Mede RSS, heap usado após GC completo e NMT (parada, depois de carga na /livros e na /api/livros/7).
cd "$(dirname "$0")/.."
APP=$1; XMX=$2; SECS=${3:-20}
SP=${TMPDIR:-/tmp}
fuser -k 8080/tcp 2>/dev/null; sleep 2
OPTS="$EXTRA -Xmx$XMX -XX:NativeMemoryTracking=summary -Dgrails.env=production -Dorg.slf4j.simpleLogger.defaultLogLevel=warn"
case $APP in
  grails) java $OPTS -jar bench/livros-grails/build/libs/livros-grails-1.0.jar > $SP/mem-$APP.log 2>&1 & ;;
  grox)   JAVA_OPTS="$OPTS" bench/livros/build/install/livros/bin/livros > $SP/mem-$APP.log 2>&1 & ;;
  plain)  JAVA_OPTS="$OPTS" sample-plain/build/install/sample-plain/bin/sample-plain > $SP/mem-$APP.log 2>&1 & ;;
esac
sleep 1
PID=$(pgrep -n -f "Xmx$XMX -XX:NativeMemoryTracking=summary -Dgrails.env")
for i in $(seq 1 600); do curl -s -o /dev/null localhost:8080/ola/x && break; kill -0 $PID 2>/dev/null || { echo "$APP $XMX: morreu na subida"; tail -5 $SP/mem-$APP.log; exit 1; }; sleep 0.2; done
if [ "$APP" = plain ]; then
  for n in $(seq 1 20); do
    curl -s -o /dev/null -u admin:admin -H 'Content-Type: application/json' \
      -d "{\"titulo\":\"Livro $n\",\"autor\":\"Autor\",\"ano\":2000}" localhost:8080/api/livros
  done
fi
for i in $(seq 1 450); do [ "$(curl -s -o /dev/null -w "%{http_code}" localhost:8080/api/livros/20)" = 200 ] && break; sleep 0.2; done; [ $i = 450 ] && { echo "$APP $XMX: não ficou pronta"; grep -m1 OutOfMemory $SP/mem-$APP.log; kill $PID; exit 1; }
snap() {
  sleep 2
  jcmd $PID GC.run >/dev/null
  sleep 1
  local rss=$(ps -o rss= -p $PID | awk '{printf "%d", $1/1024}')
  local used=$(jcmd $PID GC.heap_info | grep -oP 'used \K[0-9]+K' | head -1 | tr -d K)
  local nmt=$(jcmd $PID VM.native_memory summary scale=MB); echo "$nmt" > $SP/nmt-$APP-$XMX-$1.txt
  local tot=$(echo "$nmt" | grep -oP 'Total: reserved=\d+MB, committed=\K\d+' )
  local heap=$(echo "$nmt" | grep -A1 'Java Heap' | grep -oP 'committed=\K\d+' | head -1)
  local cls=$(echo "$nmt" | grep -A1 -- '- *Class (' | grep -oP 'classes #\K\d+' | head -1)
  local meta=$(echo "$nmt" | grep -A1 -- '- *Class (' | grep -oP 'committed=\K\d+' | head -1)
  local thr=$(echo "$nmt" | grep -oP 'threads #\K\d+' | head -1)
  local code=$(echo "$nmt" | grep -A1 -- '- *Code (' | grep -oP 'committed=\K\d+' | head -1)
  printf "%-6s %-5s %-8s RSS=%5sMB heapUsadoAposGC=%5sMB heapCommitted=%5sMB NMTcommitted=%5sMB classes=%6s meta+class=%4sMB threads=%4s code=%3sMB\n" \
    $APP $XMX "$1" $rss $((used/1024)) $heap $tot "$cls" "$meta" "$thr" "$code"
}
snap parada
for u in /livros /api/livros/7; do
  out=$(bench/build/install/bench/bin/bench "http://localhost:8080$u" 64 $SECS 5 2>&1 | tail -3 | tr '\n' ' ')
  kill -0 $PID 2>/dev/null || { echo "$APP $XMX: morreu na carga de $u"; grep -m3 -i OutOfMemory $SP/mem-$APP.log; exit 1; }
  echo "   carga $u: $out"
done
grep -m1 -i OutOfMemory $SP/mem-$APP.log && echo "$APP $XMX: OOM no log"
snap carga
kill $PID; wait 2>/dev/null
