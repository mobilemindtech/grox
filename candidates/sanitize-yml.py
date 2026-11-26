#!/usr/bin/env python3
"""
Gera o application.yml de um candidato sem credenciais: cada valor sensível vira
${PREFIXO_CAMINHO:} (ex.: environments.development.dataSource.password →
${APPB_DEVELOPMENT_DATASOURCE_PASSWORD:}). O dataSource de cada ambiente passa a
dbCreate ${PREFIXO_DB_CREATE:none} e URL padrão H2 em memória (modo MySQL), como no app A.

Trabalha por linha (mantém comentários, ordem e chaves repetidas). Revise o resultado antes de
versionar: ele troca pelo nome da chave, não entende o significado do valor.

uso: sanitize-yml.py PREFIXO origem.yml destino.yml
"""
import re
import sys

SENSITIVE = re.compile(r'(?i)^(password|passwd|secret|username|user|host|apikey|api_key|token|accesstoken|'
                       r'authtoken|clientsecret|client_secret|key|privatekey|sid|apiid|appid|applicationid|empresa)$')
KEY_LINE = re.compile(r'^(\s*)(-\s+)?([^:#\s][^:#]*?)\s*:\s*(.*)$')
H2 = 'jdbc:h2:mem:app;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE,KEY,COUNT'


def env_name(prefix, path):
    parts = [p for p in path if p != 'environments']
    return prefix + '_' + re.sub(r'[^A-Za-z0-9]+', '_', '_'.join(parts)).upper().strip('_')


# chaves compostas (prodPassword, authUsername, partnerApiKey, smsAppSecret, webPush...PrivateKey, accessKey...)
SENSITIVE_PART = re.compile(r'(?i)(password|passwd|secret|apikey|api_key|accesskey|access_key|privatekey|private_key|'
                            r'token|username|^auth$)')


def is_sensitive(key, value, path):
    if SENSITIVE.match(key) or SENSITIVE_PART.search(key):
        return True
    v = value.strip('"\'')
    if key.lower() == 'url' and ('jdbc:' in v or re.search(r'://[^/\s]*@', v) or 'dataSource' in path):
        return True
    return False


def main(prefix, src, dst):
    out = []
    stack = []   # (indentação, chave)
    for line in open(src, encoding='utf-8').read().splitlines():
        m = KEY_LINE.match(line)
        if not m or line.lstrip().startswith('#'):
            out.append(line)
            continue
        indent = len(m.group(1)) + (len(m.group(2)) if m.group(2) else 0)
        key, rest = m.group(3).strip(), m.group(4)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        path = [k for _, k in stack] + [key]
        value, comment = rest, ''
        cm = re.match(r'^(.*?)(\s+#.*)$', rest)
        if cm and not rest.lstrip().startswith(('"', "'")):
            value, comment = cm.group(1), cm.group(2)
        v = value.strip()
        if not v or v in ('|', '>'):
            stack.append((indent, key))
            out.append(line)
            continue
        in_env_ds = len(path) >= 4 and path[0] == 'environments' and path[2] == 'dataSource'
        head = line[:len(line) - len(rest)]
        if in_env_ds and key == 'dbCreate':
            out.append(f'{head}"${{{prefix}_DB_CREATE:none}}"   # [Grox] nunca update por padrão contra um banco real{comment}')
        elif in_env_ds and key == 'url':
            out.append(f'{head}"${{{env_name(prefix, path)}:{H2}}}"{comment}')
        elif v.startswith('${') or v.startswith('"${'):
            if is_sensitive(key, v, path):
                # placeholder já existente: o padrão pode ser a credencial real (${MYSQL_PASSWORD:segredo})
                out.append(head + re.sub(r'\$\{([^:}]+):[^}]*\}', r'${\1:}', rest))
            else:
                out.append(line)
        elif is_sensitive(key, v, path):
            out.append(f'{head}"${{{env_name(prefix, path)}:}}"{comment}')
        else:
            out.append(line)
    open(dst, 'w', encoding='utf-8').write('\n'.join(out) + '\n')


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
