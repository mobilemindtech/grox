# Apresentação

Este livro é a documentação de quem **usa** o Grox: para começar uma aplicação, para migrar um projeto Grails e como referência no dia a dia (opções de configuração, APIs, exemplos, recomendações e solução de problemas).

O Grox é um framework web leve sobre os **atores do Groovy 6** (`groovy.concurrent.Actor`). Ele **emula a API do Grails** (controllers, services, GORM, GSP, TagLibs, layouts, segurança no estilo spring-security) **sem Spring e sem os jars do Grails**. O servidor HTTP é o Netty, os layouts usam o SiteMesh 3 e a camada de dados é um GORM próprio sobre o Hibernate 7.4.

## As duas trilhas

O livro tem duas trilhas de leitura, que se encontram nos guias por recurso e na referência:

| Trilha | Para quem | Começa em |
|---|---|---|
| **Migrar um projeto Grails** | Quem tem uma aplicação Grails (3 a 8) e quer rodá-la sobre o Grox com o mínimo de alterações | [Visão geral da migração](migracao/visao-geral.md) |
| **Usar sem Grails** | Quem quer uma aplicação nova, sem o layout `grails-app/`, com rotas por closure ou anotação e só os módulos necessários (core, GORM, GSP, segurança) | [Visão geral dos módulos](sem-grails/visao-geral.md) |

## Como ler

- **Novo no Grox:** leia a [Introdução](introducao/o-que-e.md) inteira e o [Início rápido](introducao/inicio-rapido.md). Depois siga a trilha que combina com o seu caso.
- **Já conhece o Grails:** o [que muda em relação ao Grails](migracao/diferencas.md) resume as diferenças de comportamento. Os guias por recurso descrevem cada parte da API com as particularidades do Grox.
- **Procurando uma opção ou uma API:** vá direto para a [Referência](referencia/configuracao.md). A busca do livro (tecla `s`) também ajuda.
- **Algo não funciona:** veja [Solução de problemas](recomendacoes/problemas.md), [Perguntas frequentes](recomendacoes/faq.md) e as [Armadilhas do Groovy 6](apendices/groovy6.md).

## Convenções

| Notação | Significado |
|---|---|
| ✅ | Suportado como no Grails |
| 🟡 | Suportado em parte (a observação diz o que falta) |
| ❌ | Ausente |
| *(extensão Grox)* | Recurso ou chave que não existe no Grails |
| `grox.x.y` | Chave de configuração (no `application.yml`, `application.groovy`, variável de ambiente ou `-D`) |
| `sample-app/`, `docs/desenvolvimento.md` | Caminhos no repositório do Grox |

Os exemplos de código seguem o `sample-app/` (layout Grails) e o `sample-plain/` (sem Grails), que são aplicações executáveis e também as suítes de integração do framework: o que aparece aqui roda lá.

## Versões

| Componente | Versão |
|---|---|
| Groovy | 6.0.0 |
| JDK | 27 (baixado pelo toolchain do Gradle) |
| Netty | 4.2 |
| Hibernate | 7.4 |
| HikariCP | 7.1 |
| SiteMesh | 3.2.3 |
| Lettuce (sessão no Redis) | 7.8 |

O projeto só adota versões estáveis das dependências.

## Status

O Grox é um **protótipo**. Ele já roda projetos Grails reais (os candidatos apps A, B e C, descritos em [Casos reais](migracao/casos-reais.md)), mas a API ainda pode mudar e há lacunas conhecidas, listadas na [Compatibilidade com o Grails](referencia/compatibilidade.md). O framework ainda não é publicado num repositório Maven: as aplicações são subprojetos do mesmo build Gradle (veja o [Início rápido](introducao/inicio-rapido.md)).

## Veja também

- [O que é o Grox](introducao/o-que-e.md)
- [Início rápido](introducao/inicio-rapido.md)
- [Manter este livro](apendices/manutencao.md)
