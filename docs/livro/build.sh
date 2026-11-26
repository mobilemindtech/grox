#!/usr/bin/env bash
# Gera o livro (mdBook) em docs/livro/build e confere os links internos entre capítulos.
# Uso: docs/livro/build.sh [build|serve]
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
MDBOOK_VERSION=0.4.52

mdbook_bin() {
    if command -v mdbook >/dev/null 2>&1; then
        command -v mdbook
        return
    fi
    local local_bin="$DIR/.mdbook/mdbook"
    if [ ! -x "$local_bin" ]; then
        local arch
        case "$(uname -s)-$(uname -m)" in
            Linux-x86_64) arch=x86_64-unknown-linux-gnu ;;
            Linux-aarch64) arch=aarch64-unknown-linux-musl ;;
            Darwin-x86_64) arch=x86_64-apple-darwin ;;
            Darwin-arm64) arch=aarch64-apple-darwin ;;
            *) echo "plataforma sem binário do mdbook; instale com: cargo install mdbook --version $MDBOOK_VERSION" >&2; exit 1 ;;
        esac
        mkdir -p "$DIR/.mdbook"
        echo "baixando mdbook $MDBOOK_VERSION ($arch)" >&2
        curl -fsSL "https://github.com/rust-lang/mdBook/releases/download/v$MDBOOK_VERSION/mdbook-v$MDBOOK_VERSION-$arch.tar.gz" \
            | tar xz -C "$DIR/.mdbook"
    fi
    echo "$local_bin"
}

# Links relativos para .md (fora de blocos de código) precisam apontar para arquivos existentes.
check_links() {
    local erros=0 arquivo linha alvo destino
    while IFS= read -r arquivo; do
        while IFS= read -r linha; do
            alvo="${linha%%#*}"
            [ -z "$alvo" ] && continue
            destino="$(dirname "$arquivo")/$alvo"
            if [ ! -f "$destino" ]; then
                echo "link quebrado em ${arquivo#$DIR/}: $linha" >&2
                erros=$((erros + 1))
            fi
        done < <(awk '/^[[:space:]]*```/ { codigo = !codigo; next } !codigo' "$arquivo" \
                 | grep -oE '\]\([^)[:space:]]+\.md(#[^)]*)?\)' | sed -E 's/^\]\((.*)\)$/\1/' | grep -vE '^[a-z]+://' || true)
    done < <(find "$DIR/src" -name '*.md')
    if [ "$erros" -gt 0 ]; then
        echo "$erros link(s) quebrado(s)" >&2
        exit 1
    fi
}

# Âncoras (capitulo.md#secao) conferidas contra os ids do HTML gerado.
check_anchors() {
    command -v python3 >/dev/null 2>&1 || { echo "python3 ausente: âncoras não conferidas" >&2; return 0; }
    python3 -I - "$DIR" <<'PY'
import glob, html, os, re, sys
base = sys.argv[1]
erros = 0
for md in glob.glob(f'{base}/src/**/*.md', recursive=True):
    texto = re.sub(r'```.*?```', '', open(md, encoding='utf-8').read(), flags=re.S)
    links = re.findall(r'\]\(([^)\s]*?\.md)?#([^)\s]+)\)', texto)
    for arquivo, ancora in links:
        alvo = os.path.normpath(os.path.join(os.path.dirname(md), arquivo)) if arquivo else md
        rel = os.path.relpath(alvo, f'{base}/src')[:-3]
        pagina = f'{base}/build/' + (os.path.join(os.path.dirname(rel), 'index') if os.path.basename(rel) == 'README' else rel) + '.html'
        ids = set(re.findall(r'id="([^"]+)"', open(pagina, encoding='utf-8').read())) if os.path.exists(pagina) else set()
        if html.unescape(ancora) not in ids and ancora not in ids:
            erros += 1
            print(f'âncora quebrada em {os.path.relpath(md, base)}: {arquivo}#{ancora}', file=sys.stderr)
sys.exit(1 if erros else 0)
PY
}

check_links
MDBOOK="$(mdbook_bin)"
case "${1:-build}" in
    build) "$MDBOOK" build "$DIR" && check_anchors && echo "livro gerado em ${DIR}/build/index.html" ;;
    serve) "$MDBOOK" serve "$DIR" ;;
    publish)
      
    "$MDBOOK" build "$DIR" && check_anchors && echo "livro gerado em ${DIR}/build/index.html" && \
      cd build && tar -cvzf grox.tar.gz * && \
      scp grox.tar.gz s1:~/files/docs/grox && \
      ssh s1 -t "cd ~/files/docs/grox && tar -xvzf grox.tar.gz" && \
      echo "livro publicado"
      ;;
    *) echo "uso: $0 [build|serve]" >&2; exit 2 ;;
esac
