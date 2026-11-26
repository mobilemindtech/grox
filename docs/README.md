# Grox: documentação

Grox é um protótipo de framework web leve sobre os **atores do Groovy 6**. Ele **emula a API pública do Grails** (controllers, services, UrlMappings, GSP, TagLibs, layouts) sem Spring. O servidor HTTP é o **Netty**, e os layouts usam o **SiteMesh 3**.

**Documentação do usuário:** o livro em [`livro/`](livro/src/SUMMARY.md) (mdBook: introdução, trilha de migração de projetos Grails, trilha sem Grails, guias por recurso, referência de configuração, recomendações). Gere com `docs/livro/build.sh`. Os documentos abaixo são a documentação interna do projeto.

| Documento | Conteúdo |
|---|---|
| [arquitetura.md](arquitetura.md) | Módulos, pacotes, fluxo de uma requisição, modelo de concorrência, pipeline de views, decisões |
| [guia.md](guia.md) | Como escrever uma aplicação: estrutura, controllers, services, rotas, GSP, TagLibs, i18n, configuração |
| [compatibilidade.md](compatibilidade.md) | O que do Grails é suportado, parcial ou ausente |
| [desenvolvimento.md](desenvolvimento.md) | Como evoluir o framework: build, testes, receitas, armadilhas do Groovy 6 |
| [roadmap.md](roadmap.md) | Fases, próximos passos e pendências conhecidas |
| [benchmark.md](benchmark.md) | Como medir, resultados e o que o profiling mostrou |

O arquivo [`../Readme.md`](../Readme.md) guarda a ideia original do projeto.

## Início rápido

```bash
./gradlew build                 # compila e roda todos os testes
./gradlew :sample-app:run       # sobe o exemplo em http://localhost:8080 (views com reload)
```

Rotas do exemplo:
- `/`: página inicial
- `/usuario`: CRUD HTML
- `/api/usuarios`: API REST em JSON
- `/ola/Nome`: texto simples

Requisitos: JDK 27 (baixado pelo toolchain do Gradle) e Groovy 6.0.0.
