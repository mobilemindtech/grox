# Armadilhas da migração

Este capítulo reúne os problemas encontrados ao migrar os projetos reais (apps A, B e C), com o sintoma e a solução. Alguns são do Groovy 6, outros do Hibernate 6+ (que também quebram no Grails 7 e 8), outros de diferenças do Grox.

## Bibliotecas e build

| Sintoma | Causa | Solução |
|---|---|---|
| `NoClassDefFoundError: org/codehaus/groovy/runtime/callsite/CallSite` | Jar compilado com Groovy ≤ 5 (plugins do Grails, GORM, asset-pipeline, HTTPBuilder, GPars, bibliotecas internas) | Recompile os fontes com o Groovy 6 (importe-os como subprojeto) ou troque a biblioteca. Bibliotecas Java puras funcionam |
| HTTPBuilder (`groovyx.net.http`, 0.8) não carrega | Mesmo motivo | Troque pelo `groovy.http.HttpBuilder` (módulo `groovy-http-builder` do Groovy 6, sobre o `java.net.http.HttpClient`) ou pelo `HttpClient` do JDK. Veja abaixo |
| Worker do compilador pendurado ou `OutOfMemoryError` em projeto grande | 512 MB padrão | `tasks.withType(GroovyCompile).configureEach { groovyOptions.forkOptions.memoryMaximumSize = '4g' }` |
| Imports de `grails.*` ou `org.springframework.*` não resolvem | Classe ainda não emulada | Veja [Compatibilidade](../referencia/compatibilidade.md); remova o import sem uso ou troque a API |
| `@DelegatesTo(value=Scope<A>)` não compila | Groovy 6 não aceita tipo parametrizado em anotação | `@DelegatesTo(value=Scope)` |
| `Date.format()` não existe | Está no módulo `groovy-dateutil` | Declare `groovy-dateutil` ou use `java.time` |
| Testes Spock com `grails-testing` | Dependem do Grails | Não rodam: escreva testes de integração HTTP (veja [Testes](../sem-grails/testes.md)) |

### HTTPBuilder → `groovy.http.HttpBuilder`

As diferenças que importam:

- Basic auth vira o cabeçalho `Authorization` montado à mão.
- `res.json` faz o parse **sem olhar o `Content-Type`** e falha com corpo vazio ou que não é JSON. Cheque o status antes e só então leia o JSON; no erro, use o corpo como texto (`res.body`).
- Para APIs que mandam o erro em JSON, faça o parse condicional ao `Content-Type`.

```groovy
import groovy.http.HttpBuilder
import groovy.http.HttpResult
import java.time.Duration

HttpBuilder newHttpBuilder() {
    HttpBuilder.http {
        connectTimeout Duration.ofMinutes(1)
        requestTimeout Duration.ofMinutes(1)
        header 'Accept', 'application/json'
    }
}

def criar(Map payload) {
    HttpResult res = newHttpBuilder().post('/pagamentos') {
        header 'Authorization', "Bearer $accessToken"
        json(payload)
    }
    if (!(res.status in 200..299)) {
        log.error "falha ${res.status}: ${res.body}"     // corpo como texto
        throw new IllegalStateException("status ${res.status}")
    }
    res.body ? res.json : null                           // parse só depois do status
}
```

## Hibernate 6+ (também falha no Grails 7 e 8)

| Sintoma | Causa | Solução |
|---|---|---|
| `session.createSQLQuery` não existe | Removido no Hibernate 6 | `session.createNativeQuery(sql)` |
| HQL com nome de coluna (`arquivo_morto = 'false'`, `tenant_id`) falha | O parser do Hibernate 5 repassava ao SQL; o do 6+ recusa | Use os nomes das **propriedades** no HQL |
| HQL com `?0` ou `?` | O Hibernate 7 recusa | O Grox renumera para `?1, ?2...` sozinho; nada a fazer |
| Parâmetro `GString` (`"%${q}%"`) recusado | Hibernate 7 | O Grox converte para `String` em HQL, finders, criteria e `where` |
| `select count(*) ... order by` recusado no H2 | O H2 é mais rígido que o MySQL | Só afeta testes com H2; ajuste a consulta se quiser testá-la |
| Ordenar por coleção `hasMany` (`sort=perfis`) | O Hibernate não aceita | Bug da aplicação: ordene por propriedade simples |

## Banco existente

| Sintoma | Causa | Solução |
|---|---|---|
| `alter table ... modify column` na subida, subida travada em tabela grande | Hibernate 7 com `dbCreate: update` em modo `full` | O padrão do Grox é `grox.schema.update: safe` (nunca altera colunas), inclusive com `hibernate.dialect` explícito. Use `dbCreate: none` em bancos reais |
| Tabelas ou colunas novas no DDL (`User`, `userCriado_id`) | Naming divergente do GORM | Já corrigido no Grox (snake_case também com palavras reservadas). Se aparecer, gere o script e compare |
| Tamanhos de coluna diferentes do banco | Domain sem `maxSize`/`size` alinhado | Ajuste as constraints ou o `mapping` da domain |
| "Unable to determine Dialect" | URL do H2 com driver do MySQL (yml sanitizado sem a variável da URL) | Defina a URL do banco (`..._DATASOURCE_URL`) |
| Domain e tabela de junção disputando o mesmo nome (`grupo_cliente`) | `hasMany` unidirecional e uma domain com o nome da junção | Conflito latente também no Grails; o Grox avisa e, ao gerar o schema, renomeia |

Antes de apontar para um banco real:

```bash
-Dgrox.schema.updateScript=/tmp/ddl.sql -Dgrox.schema.scriptOnly=true
```

## Segurança

| Sintoma | Causa | Solução |
|---|---|---|
| POST de formulário ou AJAX recebe 403 | CSRF ligado por padrão no Grox | `grails.plugin.springsecurity.csrf.enabled: false`, ou `<g:form>`, `<sec:csrfInput/>` e o cabeçalho `X-CSRF-TOKEN` |
| `/api/**` com Bearer funciona, mas outra URL com o mesmo token dá 401 | `chainMap` do spring-security-rest: o resto do site ignora tokens | Comportamento igual ao Grails com esse `chainMap` |
| `/console` exige login mesmo com token | Mesmo motivo (`/console` cai em `/**`) | Entre por sessão |
| Login com usuário de e-mail recusado no seed | Constraint `email` no `username` | Use um e-mail válido (`admin@example.com`) |
| Aplicação não sobe com regra inválida | Expressões de acesso validadas na subida | Corrija a expressão (o log mostra qual) |

## Tenancy

| Sintoma | Causa | Solução |
|---|---|---|
| 403 em requisição sem usuário em classe `MultiTenant` | `TenantNotFoundException` | Resolver que devolva o tenant, ou `Tenants.withId` no código sem usuário (BootStrap, jobs) |
| Dados de outro tenant em SQL nativo | SQL nativo não é filtrado | Inclua `tenant_id = :tenant` com `Tenants.currentId()` |
| Resolver de tenant que consulta o banco entrava em recursão | O Hibernate pede o tenant ao abrir a sessão | Já resolvido: o resolver roda numa sessão isolada |

## Web e GSP

| Sintoma | Causa | Solução |
|---|---|---|
| Views `.gson` (JSON Views) ignoradas | Não suportadas | Use `respond`/`render as JSON`; remova mapeamentos `"500"`/`"404"` para views `.gson` |
| Campo de instância do controller "perde" valor entre requisições | Uma instância por ator | Guarde estado na sessão, num service ou no banco |
| Página lenta trava outras do mesmo controller | Action longa segura o ator (`poolSize`) | Action assíncrona (`task { }`) ou `static concurrency = 'request'` |
| Aviso de vírgula entre atributos de tag ou tag sem `>` | GSP malformada que o Grails aceitava em silêncio | Aceita com aviso; corrija quando puder |
| `NotSerializableException` com o nome do atributo | Sessão no Redis exige `Serializable` | Guarde ids, não entidades nem objetos não serializáveis |
| Atributo de sessão ignorado com aviso ao ler do Redis | Classe fora da allowlist | `grox.session.serialization.allow: [meu.pacote]` |
| Variável local com o nome de um método do builder (`def json = ...; json(x)`) | Groovy chama `json.call(x)` | Renomeie a variável |
| Variável de ambiente `MAIL`, `PATH`, `HOME` escondendo o bloco do yml | Sobreposição por ambiente | Corrigido: só vale para valores-folha |

## Código da aplicação

| Achado | Observação |
|---|---|
| Propriedade removida da domain e ainda usada na GSP (`userInstance?.msn`) | Bug da aplicação, também no Grails |
| Chamada a método comentado | Idem |
| Classe ausente no repositório de origem | Recrie em `overrides/` até entrar no original |
| Nome de classe com erro de digitação que o POI 3 tolerava | Corrija por patch |

## Veja também

- [Passo a passo](passo-a-passo.md)
- [Armadilhas do Groovy 6](../apendices/groovy6.md)
- [Solução de problemas](../recomendacoes/problemas.md)
- [Casos reais: apps A, B e C](casos-reais.md)
