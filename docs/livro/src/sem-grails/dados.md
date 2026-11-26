# Dados com @Entity

O módulo `grox-gorm` traz a API do GORM sobre o Hibernate 7.4 também para apps sem `grails-app/`. A única diferença é como a domain class é reconhecida: fora de `grails-app/domain`, ela leva `@grails.gorm.annotation.Entity`, como no GORM standalone. Este capítulo mostra a domain class, os services transacionais, a sessão de dados nas rotas, a carga inicial e onde está o resto da API.

## Domain class

```groovy
package livraria

import grails.gorm.annotation.Entity

@Entity
class Livro {
    String titulo
    String autor
    Integer ano
    Editora editora                  // many-to-one
    Date dateCreated                 // preenchido no insert
    Date lastUpdated                 // preenchido a cada save

    static hasMany = [resenhas: Resenha]

    static constraints = {
        titulo blank: false, size: 1..200
        autor blank: false
        ano nullable: true, min: 1450
        editora nullable: true
    }

    static mapping = {
        sort 'titulo'
    }
}
```

`@Entity` dá à classe o mesmo que a AST do `grails-app/domain`:
- a API estática e de instância do GORM (`get`, `list`, finders, `where`, `save`, `validate`...), pela trait `GormEntity<Livro>`, já com o tipo da classe;
- `Long id` e `Long version`;
- as coleções de `hasMany` e as referências de `belongsTo`;
- o construtor com `Map` (`new Livro(params)`), que converte tipos e ignora chaves desconhecidas.

O IntelliJ IDEA completa essa API e os dynamic finders nas classes `@Entity` pelo descritor GDSL do framework ([Autocomplete no IntelliJ IDEA](../recursos/estrutura.md#autocomplete-no-intellij-idea)).

A classe precisa estar no pacote da aplicação (ou num subpacote) para ser mapeada. As constraints, o `static mapping`, as associações, o naming em snake_case, a validação com os códigos do Grails e o multi-tenancy (`implements MultiTenant<Livro>`) funcionam como numa app Grails.

## Banco

```yaml
dataSource:
  driverClassName: org.postgresql.Driver
  url: "${DB_URL:jdbc:postgresql://localhost/livraria}"
  username: "${DB_USER:livraria}"
  password: "${DB_PASSWORD:}"
  dbCreate: update            # create-drop, create, update, validate, none (padrão)
  logSql: false
  properties:
    maximumPoolSize: 10       # repassado ao HikariCP
```

O driver entra como `runtimeOnly` no build. O Hibernate sobe em paralelo com o resto da aplicação, e só se houver domain classes ou `dataSource`. Com `dbCreate: update`, o Grox só cria tabelas, colunas, chaves e índices, nunca altera colunas existentes (`grox.schema.update: safe`). Antes de apontar para um banco existente, veja o DDL com `grox.schema.updateScript`.

## Services

Classes com o sufixo `Service` no pacote da aplicação são services: singletons criados na subida e injetados pelo nome.

```groovy
package livraria

import grails.gorm.transactions.Transactional

@Transactional
class LivroService {

    List<Livro> todos() {
        Livro.list(sort: 'titulo')
    }

    Livro buscar(Long id) {
        Livro.get(id)
    }

    /** O livro salvo ou, com erros de validação, sem id. */
    Livro salvar(Map dados) {
        def livro = new Livro(dados)
        livro.save()
        livro
    }

    Map<String, Long> porAutor() {
        Livro.list().countBy { it.autor }.collectEntries { k, v -> [k, v as Long] }
    }
}
```

- `@grails.gorm.transactions.Transactional` (ou `@ReadOnly`) na classe ou nos métodos faz cada chamada externa rodar numa transação. O `@org.springframework.transaction.annotation.Transactional` também vale. Chamadas internas (`this.x()`) não passam pelo proxy.
- `static actor = true` faz o service rodar atrás de um ator: as chamadas são serializadas, e o estado interno dispensa sincronização. Evite ciclos entre services-ator.
- `@PostConstruct` roda depois que todos os services e beans foram injetados.

## Sessão de dados nas rotas

Cada handler (rota por closure ou método `@Handler`) roda com uma sessão de dados disponível, como as actions de um controller:
- a sessão Hibernate só é aberta no primeiro acesso ao banco; rotas sem banco não pagam por ela;
- ela continua aberta até o fim da renderização da view, então associações lazy funcionam na GSP (open session in view);
- ela é propagada para services-ator, junto com a autenticação e o tenant;
- `save()` e `delete()` participam da transação aberta ou abrem e fazem commit de uma. Alterações em entidades carregadas **só são gravadas por `save()`** ou dentro de uma transação.

Fora de uma requisição (no `main`, em testes, em threads próprias), cada chamada do GORM abre e fecha a própria sessão. Para agrupar operações, use `withTransaction` ou `withNewSession`.

## Dados iniciais

Há duas formas, que podem conviver. A primeira é uma classe `BootStrap` no pacote da aplicação, como no Grails: os services são injetados pelo nome, `init` roda na subida (antes do servidor, com uma sessão de dados aberta) e `destroy` no desligamento.

```groovy
package livraria

class BootStrap {
    LivroService livroService

    def init = { servletContext ->          // servletContext é null, como nas apps Grails do Grox
        environments {
            development { livroService.salvar(titulo: 'Dom Casmurro', autor: 'Machado de Assis', ano: 1899) }
        }
    }

    def destroy = {
    }
}
```

A segunda é o bloco `bootstrap { init { } destroy { } }` da [DSL de projeto](dsl-projeto.md#bootstrap--), que roda antes das classes. Nos dois, `environments { }` escolhe o bloco do ambiente atual, e `log` e `config` estão disponíveis. Numa app que também tem o módulo Grails, é ele quem roda as classes `BootStrap` (uma vez só). Na recarga leve nada roda de novo.

`org.grox.core.Env` informa o ambiente em qualquer lugar (`Env.currentName`, `Env.development`).

## O que funciona igual ao GORM

Toda a camada de dados é a mesma das apps Grails. Os guias detalhados valem sem mudança:

| Assunto | Capítulo |
|---|---|
| Propriedades, associações, `static mapping`, naming, esquema | [Domain classes](../recursos/domain.md) |
| `get`, `list`, finders, `where { }`, `createCriteria`, HQL | [Consultas](../recursos/consultas.md) |
| Constraints, binding, `typeMismatch`, mensagens | [Validação, binding e mensagens](../recursos/validacao.md) |
| Transações, `withTransaction`, listeners, dirty checking | [Sessões, transações e eventos](../recursos/transacoes.md) |
| `MultiTenant`, `Tenants.withId`, resolvers | [Multi-tenancy](../recursos/multi-tenancy.md) |

Para usuários e roles em domain classes (e o tenant do usuário logado como resolver padrão), acrescente o módulo `grox-security-gorm`: veja [Segurança sem o plugin](seguranca.md#usuários-em-domain-classes).

## Veja também

- [Domain classes](../recursos/domain.md)
- [Services e injeção de dependências](../recursos/services.md)
- [Rotas por anotação](rotas-anotacao.md)
- [Configuração](../referencia/configuracao.md)
