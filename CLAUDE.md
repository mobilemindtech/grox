# CLAUDE.md

Protótipo **Grox**: framework web leve sobre os atores do Groovy 6 (`groovy.concurrent.Actor`) que **emula a API do Grails** sem Spring. Usa Netty 4.2, SiteMesh 3, Hibernate 7.4 (GORM próprio), sessão memória/Redis (Lettuce), segurança estilo spring-security-core/rest e multi-tenancy DISCRIMINATOR. Documentação interna em `docs/`: comece por `docs/arquitetura.md`, e para evoluir o framework leia `docs/desenvolvimento.md`. A documentação do usuário é o livro mdBook em `docs/livro/` (`docs/livro/build.sh` gera e confere os links).

## Comandos
- `./gradlew build`: compila e roda todos os testes (sempre rode antes de concluir uma mudança)
- `./gradlew :framework:test` / `./gradlew :sample-app:test`
- `docs/livro/build.sh`: gera o livro (`serve` para ver em http://localhost:3000)
- `./gradlew :sample-app:run`: http://localhost:8080 (usuários: admin/admin, maria/maria, joao/joao)
- Testes com Redis precisam de `redis-server` no PATH (são pulados se não houver)

## Layout
- `framework/`: em subprojetos `framework/grox-*` (core, gorm, gsp, assets, security, security-gorm, grails, grails-plugins, dsl, pug, markdown); o `framework` agrega todos e guarda os testes. Pacote `org.grox.*`, mais as classes de compatibilidade em `grails.*` (mesmo nome e pacote do Grails). Cada pacote tem um módulo (mapa no `ArquiteturaTest`)
- `sample-app/`: app no layout `grails-app/`; é também a suíte de integração (`SampleAppTest`)
- `sample-plain/`: app sem `grails-app/` nem o módulo Grails (core + gorm + gsp + security + `grox-dsl`, views também em Pug e Markdown); garante o núcleo sem Grails
- `sample-lib/`: biblioteca `org.grox.lib` (artefatos em outro pacote, no layout `grox-app/`) usada pelo `sample-app`
- `gradle-plugin/`: plugins `org.grox.app` e `org.grox.lib` (build incluído); `bench/`: gerador de carga e a app de referência Grails x Grox (`bench/livros`, a mesma `grails-app/` no Grox e, em `bench/livros-grails`, no Grails 8; ver `docs/benchmark.md`)
- `candidates/`: só o `sanitize-yml.py`; o diretório ignora o resto (apps importadas localmente). O build de cada app da empresa sobre o Grox fica no repositório da app (`grox/`, build paralelo, `groxDir` apontando para este diretório)
- Plugins do Grails reimplementados em `org.grox.plugins.*` (console, quartz, excel), só no que os projetos usam; a API pública mantém o pacote original

## Regras do projeto
- **Repositório público** (https://github.com/mobilemindtech/grox): nada das apps da empresa entra aqui (nomes, classes, hosts, configuração, código). Nos textos, elas são "app A", "app B" e "app C"; o detalhe fica nos repositórios delas. O hook `.githooks/pre-commit` (`git config core.hooksPath .githooks`) barra os termos de `~/.config/grox/termos-privados.txt`, e o CI roda o `gitleaks`
- Emular a API do Grails; **não usar jars do Grails ou GORM** (incompatíveis com o Groovy 6: falta `CallSite`)
- Só versões estáveis das dependências (`gradle/libs.versions.toml`)
- Estado de requisição, renderização, sessão de dados, autenticação e tenant em `ScopedValue` (`RequestContext`, `GspRenderContext`, `Persistence`, `SecurityContextHolder`, `Tenants`); os três últimos são propagados a services-ator por `ContextPropagation`. Nada bloqueante no event loop do Netty
- Configuração de segurança em `grails.plugin.springsecurity.*` e de tenancy em `grails.gorm.multiTenancy.*` (mesmas chaves dos plugins do Grails); extensões Grox documentadas em `docs/guia.md`
- Toda feature nova vem com teste e com atualização de `docs/guia.md`, `docs/compatibilidade.md` e `docs/roadmap.md`
- **Documentação no mesmo commit:** toda funcionalidade nova ou mudança de comportamento (API, chave de configuração, padrão, compatibilidade, plugin Gradle) também atualiza o livro em `docs/livro/src/` (mapa do que atualizar em `docs/livro/src/apendices/manutencao.md`) e roda `docs/livro/build.sh`. Mudança sem documentação não está concluída
- Comentários e mensagens em português

## Desempenho
Medir com JFR e `bench/run.sh` antes de otimizar; nada de exceções por requisição; `@CompileStatic` só em classes do framework sem dinamismo. Ao fechar uma série de otimizações, ou depois de mexer no caminho de toda requisição ou de atualizar Groovy/Netty/Hibernate/JDK, rode `BASE=<checkout da última rodada> bench/rodada.sh` (A/B) e registre o resultado em `docs/benchmark-historico.md`: é o que pega regressão.

## Armadilhas do Groovy 6
Ver a tabela em `docs/desenvolvimento.md`. Os casos mais comuns: `call()` dentro de closure, conflito `isX`/`getX`, `{}` solto vira closure, aspas simples dentro de `'...'`, `switch` sobre `Class` e `?:` com valor 0.

## Pendências
Os próximos passos estão em `docs/roadmap.md`, nas seções "Pendente" (Fase 7). Atualize a checklist ao concluir cada item.
