# Recarga em desenvolvimento

Este capítulo explica a recarga de classes no `gradle run`: o que é recarregado sem reiniciar a JVM, a diferença entre recarga leve e completa, como ela funciona, como configurar e quais são os limites. A recarga é própria do Grox, por classloaders por geração, sem spring-loaded nem devtools.

## O que acontece ao salvar

Com `./gradlew run` em `development`, salvar um controller, service, taglib, domain class ou classe de `src/main/groovy` recarrega a aplicação **sem reiniciar a JVM**. Servidor, porta e sessões continuam os mesmos, e o usuário segue logado.

Views GSP, assets e estáticos têm o reload próprio (relidos a cada mudança, sem recarga de classes), que funciona igual antes e depois de uma recarga.

## Leve e completa

As classes ficam em duas camadas:

| Camada | O que entra | Na mudança |
|---|---|---|
| Recarregável | Tudo de `grails-app/{controllers,services,taglib,init,jobs}` | **Recarga leve** (100 ms a 1 s): troca controllers, services, taglibs, interceptors, jobs, UrlMappings e `resources.groovy`. Hibernate, segurança e dados continuam; o BootStrap **não** roda de novo |
| Estável | Domain classes, `src/main/groovy`, `grails-app/utils` | **Recarga completa** (1 a 3 s, contra 10 a 15 s de um restart): recria o Hibernate e roda o `destroy` e o `init` do BootStrap. As requisições esperam a nova geração, em vez de receberem erro |

Outros arquivos:

| Arquivo | Recarga |
|---|---|
| `messages*.properties` | Leve |
| `resources.groovy` | Leve |
| `application.yml`, `application.groovy`, `runtime.groovy` | Completa |
| Views, assets, estáticos | Nenhuma (reload próprio) |

### Classes fixadas

Um artefato recarregável referenciado por uma classe estável (por exemplo, um service usado como tipo de campo em `src/main/groovy`) fica **fixado na camada estável** e só recarrega na completa. O log avisa quando isso acontece. Para manter a recarga leve, referencie services pelo nome (`def livroService`) nas classes de `src/`.

## Como funciona

- O `run` inicia um Gradle contínuo (`--continuous :app:groxReloadStamp`) que compila a cada mudança, de forma incremental.
- A tarefa `groxReloadStamp` grava um carimbo ao fim de cada compilação **bem-sucedida**, e é ele que dispara a recarga. Uma compilação com erro não derruba nada: a versão anterior continua no ar.
- A app roda em classloaders próprios, com o bytecode lido na hora da recarga (o compilador pode apagar o diretório de classes sem afetar a versão no ar).
- Cada recarga sobe uma nova *geração*. A entrada do Netty troca de geração sem fechar conexões.
- Se a recarga completa falhar (erro no BootStrap, mapeamento inválido), as requisições recebem **503** com o erro até a próxima mudança corrigir.

## Bibliotecas `org.grox.lib`

As bibliotecas `org.grox.lib` **do mesmo build** de que a app depende, direta ou transitivamente, recarregam como a própria app. É o caso de um `core` com as domains e os services usado por várias apps (o `app-c-core` do app-c):

```groovy
// app/build.gradle
dependencies { implementation project(':core') }   // core: plugins { id 'org.grox.lib' }
```

- O `run` passa os diretórios de classes e resources das bibliotecas junto com os da app (`grox.reload.classes`/`resources`) e os põe no classpath antes dos jars.
- O Gradle contínuo (`groxReloadStamp`) compila também as bibliotecas.
- As camadas são as mesmas: service, controller e taglib da biblioteca pedem a recarga leve; domain, `src/main/groovy` e configuração pedem a completa. As classes fixadas também valem entre módulos: um service da biblioteca referenciado por uma classe de `src/main/groovy` (da biblioteca ou da app) fica na camada estável.
- Ficam de fora, no classloader do sistema: projetos sem o plugin (o próprio framework, bibliotecas Java comuns) e dependências de outros builds (o Grox por composite build, jars publicados). Mudança neles exige reiniciar.
- **Tempo de compilação:** quando a biblioteca muda, o Gradle recompila a app inteira, porque o classpath dela mudou (no Groovy, sem a *compilation avoidance*, que ainda está em preview no Gradle). No app-c, mudar um service do `app-c-core` leva cerca de 3 minutos: 10 s para o `app-c-core` (incremental), 3 s para o jar, 2 min para o `app-c-web` inteiro e 6 s para a recarga leve.

## Configuração

| Onde | Chave | Padrão | |
|---|---|---|---|
| `build.gradle` | `grox { reload = false }` | `true` | Desliga a recarga no `run` |
| yml | `grox.reload.enabled` | `true` | Também `-Dgrox.reload.enabled=false` |
| `-D` | `grox.reload.compile` | `true` | `false`: não inicia o Gradle contínuo; a IDE compila |
| `-D` | `grox.reload.interval` | `500` | Intervalo do monitoramento, em ms |

Sem o wrapper do Gradle, ou com `-Dgrox.reload.compile=false`, a recarga dispara quando os diretórios de classes param de mudar (a IDE compila).

### Fontes fora da raiz do build

O Gradle contínuo só percebe mudanças dentro do diretório raiz do build. No [build paralelo](../migracao/build-paralelo.md), os fontes ficam fora dele (`grox { source('..') }`). Quando algum diretório de fontes da app ou das bibliotecas está fora da raiz, o `run` passa todos eles em `grox.reload.sources`. A app então os observa e roda o Gradle (`groxReloadStamp`) uma vez a cada mudança, em vez de usar o `--continuous`. O log mostra `Gradle sob demanda (fontes fora da raiz do build)`.

## Limitações

- **Sessão em memória:** instâncias guardadas na sessão continuam sendo da geração antiga. Depois de uma recarga completa, uma domain class na sessão é de outra classe: guarde ids. Com sessão no Redis, o objeto é desserializado com a geração nova.
- **Context classloader:** código de bibliotecas que carrega classes da app pelo context classloader de threads do Netty vê a cópia do classpath do sistema. Nas actions e views, o context classloader já é o da aplicação.
- **Projetos grandes:** o tempo vai quase todo na compilação de domain classes. O Gradle recompila tudo que depende da classe alterada (num projeto de 61 domains, cerca de 24 s contra 2 a 3 s de um controller). Separar as domains numa biblioteca `org.grox.lib` ainda não ajuda: mudar a biblioteca recompila a app inteira (ver [Bibliotecas](#bibliotecas-orggroxlib)).
- **JWT:** tokens continuam válidos na recarga leve. Na completa, só se `rest.token.storage.jwt.secret` estiver configurado (sem ele, o segredo é aleatório por geração).
- **Apps do `grox-dsl`** (sem `grails-app/`) ainda não têm recarga de classes.

A troca de métodos na mesma JVM (HotSwap/DCEVM, sem nova geração) está registrada como melhoria futura.

## Subida rápida no `run`

O `run` usa JIT só C1 (`-XX:TieredStopAtLevel=1`), o que acelera a subida de 25% a 40%. Para medir desempenho, desligue com `grox { fastDevStartup = false }`. Ver [Operação](operacao.md#subida-rápida).

## Diferenças para o Grails

| Recurso | Grails | Grox |
|---|---|---|
| Mecanismo | spring-loaded / devtools (restart) | Gerações de classloaders, leve ou completa |
| Domain classes | Restart | Recarga completa (1 a 3 s) |
| Sessão e login | Perdidos no restart | Mantidos |
| Erro de compilação | | Versão anterior continua no ar |

## Veja também

- [Estrutura de uma aplicação](estrutura.md)
- [Plugins e tarefas Gradle](../referencia/gradle.md)
- [Views GSP](gsp.md)
- [Assets e estáticos](assets.md)
- [Solução de problemas](../recomendacoes/problemas.md)
