# Armadilhas do Groovy 6

O Grox roda sobre o Groovy 6, que mudou alguns comportamentos em relação ao Groovy 3, 4 e 5 usados pelo Grails. Esta lista reúne o que já apareceu ao escrever e migrar aplicações, agrupado por tema. As armadilhas internas do framework ficam em `docs/desenvolvimento.md`, no repositório.

## Dependências

| Sintoma | Causa e solução |
|---|---|
| `NoClassDefFoundError: org/codehaus/groovy/runtime/callsite/CallSite` | O call site caching foi removido no Groovy 6, e jars compilados com Groovy ≤ 5 (Grails, GORM, asset-pipeline, plugins, bibliotecas Groovy antigas) o usam. Verifique o Groovy de cada biblioteca antes de adotar; quando não houver versão para o Groovy 6, importe os fontes e compile junto |
| `groovy.typecheckers.SqlChecker` não existe | No 6.0.0 se chama `SqlInjectionChecker` |
| `Date.format()` não existe | Está no módulo `groovy-dateutil`, que não vem incluído. Use `java.time` (`DateTimeFormatter`) ou declare o módulo |
| HTTPBuilder (groovyx 0.8) não funciona | Troque pelo `groovy.http.HttpBuilder` do módulo `groovy-http-builder`, sobre o `java.net.http.HttpClient`. Atenção: `res.json` faz o parse sem olhar o `Content-Type`; cheque o status antes |

## Closures e escopo

| Sintoma | Causa e solução |
|---|---|
| `call(...)` dentro de uma closure chama o próprio `Closure.call` | Não nomeie métodos auxiliares como `call` |
| Variável local com o nome de um método do delegate (`def json = ...` e depois `json(x)` na closure) | A chamada vira `json.call(x)` na variável. Renomeie a variável |
| `owner` dentro de closure numa classe que tem um campo `owner` | `owner` é o dono da closure, não o campo. Capture `this.owner` antes |
| Parâmetro com o mesmo nome em closures aninhadas | Erro "current scope already contains a variable". Use nomes distintos |
| Bloco `{ ... }` solto não executa | Em Groovy, é uma closure. Use `if (true) { }` ou declare as variáveis fora |
| Método `private` chamado de closure numa classe com subclasses | Funciona, mas passa por `MissingMethodException` a cada chamada (lento). Use `protected` |
| Classe aninhada estática chamando método estático da externa sem qualificar | Idem: exceção interna a cada chamada. Qualifique (`Externa.metodo()`) |

## Groovy truth e operadores

| Sintoma | Causa e solução |
|---|---|
| `mapa[chave] ?: padrao` troca o valor `0` pelo padrão | `0` é falso. Use `mapa.containsKey(chave) ? mapa[chave] : padrao` |
| `x ?: true` com `x == false` vira `true` | Use `x == null ? padrao : x` |
| `x ?: [:]` troca um mapa vazio por outro objeto | Mapa vazio é falso. Use `x != null ? x : padrao` |
| `valor == false` com valor não booleano | Comparação por reflexão com exceção. Use `valor instanceof Boolean && !valor` |
| `obj as Classe` com uma classe comum | A coerção não chama o construtor. Use `new Classe(...)`; `as JSON` funciona por extensão |

## `switch`

| Sintoma | Causa e solução |
|---|---|
| `switch (classe) { case Date -> }` não casa | `case Classe` testa **instância** (`isCase`), não igualdade de `Class`. Use `if (t == Date)` |
| `switch` com `->` como última instrução do método | Use `return switch (...) { }` explicitamente |
| `case 'a' -> { return v }` | "switch expression does not support return". Use `if` |
| Bloco com várias instruções em `case ... -> { }` de uma switch-expressão | Termine com `yield valor` |

## Classes, propriedades e traits

| Sintoma | Causa e solução |
|---|---|
| Propriedade `x` devolve `Boolean` em vez de `String` | `isX()` e `getX()` na mesma classe disputam a propriedade. Use nomes distintos |
| Variável chamada `def` | É palavra reservada: "Unexpected input" |
| `super(Outer.this.x)` em classe interna | Use uma classe aninhada `static` que recebe o objeto externo por parâmetro |
| Classe interna anônima num trait | "Cannot have non-static inner class inside a trait": use uma classe própria |
| `@DelegatesTo(value = Tipo<A>)` | "Cannot select from a parameterized type": use `value = Tipo` |
| Sobrecargas `f(Map, Object)` e `f(Object, Map)` chamadas com dois mapas | Ambíguas. Prefira um único método `(Object, Object)` que decide pelo tipo |
| Sobrecargas `f(String, List)` e `f(String, Object[])` com `null` | "Ambiguous method overloading" em chamada dinâmica. Tipe o argumento (`(List) null`) |
| `alvo.invokeMethod(...)` com `alvo` tipado como `Object` | Erro interno do invokedynamic. Use `InvokerHelper.invokeMethod(alvo, nome, args)` |

## Sintaxe e strings

| Sintoma | Causa e solução |
|---|---|
| Aspas simples dentro de `'...'` | O parser acusa "Missing ')'" em outra linha. Use `'''...'''` ou aspas duplas |
| Pontos, `/`, `;`, `[` ou `<` no nome de um método entre aspas (testes) | "Illegal method name" na JVM ao executar. Troque por `_` |

## Gradle e testes

| Sintoma | Causa e solução |
|---|---|
| Testes que sobem a aplicação interferem entre si | O GORM e o datastore são globais na JVM. Use `forkEvery = 1` na tarefa `test` |
| O compilador fica sem memória em projetos grandes | `groovyOptions.forkOptions.memoryMaximumSize = '4g'` |
| `HttpClient` do JDK reenvia um GET em testes de `Connection: close` | Use socket cru para testar desligamento |
| Configuration cache do Gradle quebra numa tarefa própria | Não capture `Project`/`SourceSet` em closures de tarefa (nem em `onlyIf`). Calcule os valores antes |
| `pkill -f padrão` em script mata o próprio shell | O padrão casa com a linha de comando do script. Encerre pelo pid |

## Veja também

- [Armadilhas da migração](../migracao/armadilhas.md)
- [Solução de problemas](../recomendacoes/problemas.md)
- [Perguntas frequentes](../recomendacoes/faq.md)
