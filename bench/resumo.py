#!/usr/bin/env python3
"""Resume a saída do bench/rodada.sh em tabelas Markdown para o docs/benchmark-historico.md.

uso: bench/resumo.py rodada.log
Para cada versão e rota: req/s com 64 conexões e p50 com 1 conexão (as duas rodadas, separadas por ·),
o tempo até responder e, na /usuario/index com login, req/s, p50 e CPU por requisição.
"""
import re
import sys
from collections import defaultdict

livros = defaultdict(lambda: defaultdict(lambda: {'rps': [], 'p50': []}))
subida = defaultdict(list)
logado = defaultdict(lambda: defaultdict(lambda: {'rps': [], 'p50': [], 'cpu': []}))
versao = url = None
n = 0

for linha in open(sys.argv[1], encoding='utf-8'):
    if m := re.match(r'##### (\S+)', linha):
        versao, url = m.group(1), None
    elif m := re.match(r'\w+: respondendo em (\d+) ms', linha):
        subida[versao].append(int(m.group(1)))
    elif m := re.match(r'== \w+ (\S+)', linha):
        url, n = m.group(1), 0
    elif m := re.match(r'\[\w+ c=(\d+)\]\s+(.*)', linha):
        d = logado[versao][m.group(1)]
        resto = m.group(2)
        if r := re.search(r'req/s: (\d+)', resto):
            d['rps'].append(int(r.group(1)))
        if r := re.search(r'p50: ([\d.]+)', resto):
            d['p50'].append(float(r.group(1)))
        if r := re.search(r'CPU ms/req: ([\d.]+)', resto):
            d['cpu'].append(float(r.group(1)))
    elif url and (r := re.search(r'req/s: (\d+)', linha)):
        if n == 0:
            livros[versao][url]['rps'].append(int(r.group(1)))
        n += 1
    elif url and (r := re.search(r'p50: ([\d.]+)', linha)):
        if n == 2:
            livros[versao][url]['p50'].append(float(r.group(1)))


def br(x, casas=0):
    s = f'{x:,.{casas}f}'
    return s.replace(',', '_').replace('.', ',').replace('_', '.')


def junta(vs, casas=0):
    return ' · '.join(br(v, casas) for v in vs) or '-'


versoes = [v for v in livros]
rotas = sorted({u for v in livros.values() for u in v}, key=['/ola/mundo', '/livros', '/api/livros/7'].index)
if versoes:
    print('| req/s com 64 conexões | ' + ' | '.join(versoes) + ' |')
    print('|---|' + '---:|' * len(versoes))
    for u in rotas:
        print(f'| `{u}` | ' + ' | '.join(junta(livros[v][u]['rps']) for v in versoes) + ' |')
    print()
    print('| p50 com 1 conexão (ms) | ' + ' | '.join(versoes) + ' |')
    print('|---|' + '---:|' * len(versoes))
    for u in rotas:
        print(f'| `{u}` | ' + ' | '.join(junta(livros[v][u]['p50'], 2) for v in versoes) + ' |')
    print()
    print('| | ' + ' | '.join(versoes) + ' |')
    print('|---|' + '---:|' * len(versoes))
    print('| Até responder (s) | ' + ' | '.join(junta([s / 1000 for s in subida[v]], 1) for v in versoes) + ' |')
    print()
if logado:
    print('| `/usuario/index` com login | conexões | req/s | p50 (ms) | CPU ms/req |')
    print('|---|---:|---:|---:|---:|')
    for v, porc in logado.items():
        for c, d in porc.items():
            print(f'| {v} | {c} | {junta(d["rps"])} | {junta(d["p50"], 2)} | {junta(d["cpu"], 2)} |')
