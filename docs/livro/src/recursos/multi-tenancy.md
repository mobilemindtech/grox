# Multi-tenancy

Este capítulo cobre o multi-tenancy do GORM no modo **DISCRIMINATOR**: uma coluna de tenant em cada tabela, preenchida e filtrada automaticamente. Mostra a configuração, as domain classes `MultiTenant`, os resolvers, a API `Tenants`, as anotações e os cuidados com SQL nativo e BootStrap.

Os modos DATABASE e SCHEMA não são suportados.

## Configuração

```yaml
grails:
  gorm:
    multiTenancy:
      mode: DISCRIMINATOR
      tenantResolverClass: org.grox.security.gorm.UserTenantResolver
```

- Sem `mode`, o padrão é `NONE`, mas o Grox liga `DISCRIMINATOR` se houver classes `MultiTenant`.
- Com a segurança ativa, o resolver padrão é o `UserTenantResolver` *(extensão Grox)*: o tenant é o do usuário logado.

## Domain classes

```groovy
import grails.gorm.MultiTenant

class Tarefa implements MultiTenant<Tarefa> {
    String titulo
    String tenantId       // se omitida, a AST adiciona
}

class Projeto implements MultiTenant<Projeto> {
    Long empresa
    static mapping = { tenantId name: 'empresa' }   // outra propriedade como tenant
}
```

O tenant pode ser `String` ou numérico (inclusive primitivo).

O que acontece:

- A propriedade vira a coluna de tenant pelo suporte nativo do Hibernate (`@TenantId`).
- No insert, ela é preenchida com o tenant atual.
- **Todas** as consultas são filtradas: `list`, `get`, `count`, finders, `where`, Criteria e HQL.
- Registros de outro tenant simplesmente não existem: `get(id)` devolve `null`.
- A propriedade nunca é vinculada por binding nem por JSON, e não aparece no JSON das entidades.
- Acessar uma classe `MultiTenant` sem tenant resolvido lança `TenantNotFoundException`, que vira **403** nas requisições. Classes comuns não são afetadas.

## Sem anotações

Controllers e services **não precisam de anotação**, como no GORM em modo DISCRIMINATOR:

1. a segurança autentica o usuário;
2. no primeiro acesso ao banco na requisição, a sessão do Hibernate é aberta e pede o tenant ao `tenantResolverClass`;
3. a partir daí, o Hibernate filtra as consultas e preenche o tenant nos inserts.

O tenant acompanha a requisição para services-ator, tarefas assíncronas e o console web.

## Resolvers

| Resolver | Tenant |
|---|---|
| `org.grox.security.gorm.UserTenantResolver` *(extensão Grox)* | `tenantId` do usuário logado (`userLookup.tenantPropertyName`, padrão `tenantId`) ou a claim `tenant` do JWT |
| `FixedTenantResolver` | Um valor fixo |
| `SystemPropertyTenantResolver` | `-Dgorm.tenantId=acme` |
| `SessionTenantResolver` | Atributo `gorm.tenantId` da sessão HTTP; sem ele, `TenantNotFoundException` |
| `AllTenantsResolver` | Interface para resolvers que também listam os tenants |

O `UserTenantResolver` mudou de pacote: antes era `org.grox.security.UserTenantResolver`.

### Resolver próprio

```groovy
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.springframework.web.context.request.RequestContextHolder

class CabecalhoTenantResolver implements TenantResolver {
    Serializable resolveTenantIdentifier() {
        String tenant = RequestContextHolder.currentRequestAttributes().request.getHeader('X-Tenant')
        if (!tenant) throw new TenantNotFoundException('cabeçalho X-Tenant ausente')
        tenant
    }
}
```

- É instanciado com o construtor sem argumentos e recebe injeção por nome.
- Roda **a cada abertura de sessão do Hibernate**: precisa ser barato.
- Pode devolver `null`.
- Pode consultar o banco (por exemplo `springSecurityService.currentUser`): o Grox o executa numa sessão de dados isolada, com proteção contra reentrância. Sem isso haveria recursão infinita, porque o Hibernate pede o tenant ao abrir a sessão.

`SubDomainTenantResolver`, `HttpHeaderTenantResolver` e `CookieTenantResolver` não vêm prontos, mas são fáceis de escrever como acima.

## API `Tenants`

```groovy
import grails.gorm.multitenancy.Tenants

Tenants.currentId()                       // tenant atual (TenantNotFoundException se não houver)
Tenants.currentIdOrNull()
Tenants.withId('acme') { Tarefa.list() }  // executa com outro tenant (abre nova sessão se preciso)
Tarefa.withTenant('acme') { ... }
Tenants.withCurrent { ... }               // com o tenant resolvido
Tenants.withoutId { Tarefa.list() }       // sem filtro: todos os tenants
```

Dentro de `withoutId`, os inserts não recebem tenant: atribua a propriedade explicitamente. `eachTenant` não existe.

## Anotações em services

```groovy
import grails.gorm.multitenancy.*

class RelatorioService {
    @WithoutTenant
    Long totalGeral() { Tarefa.count() }          // todos os tenants

    @Tenant({ 'acme' })
    List<Tarefa> daAcme() { Tarefa.list() }       // tenant fixo

    @CurrentTenant
    List<Tarefa> minhas() { Tarefa.list() }       // exige tenant resolvido
}
```

Valem na classe ou no método, combinam com `@Transactional` e funcionam por proxy (só em chamadas externas). São **opcionais**: só servem para exceções.

## BootStrap e jobs

No BootStrap e nos jobs não há usuário logado. Crie e leia os dados com `withId`:

```groovy
def init = { servletContext ->
    Tenants.withId('acme') {
        new Tarefa(titulo: 'Primeira').save()
    }
}
```

## SQL nativo

`createNativeQuery`, `createSQLQuery` e `groovy.sql.Sql` **não são filtrados**. Inclua o tenant na consulta:

```groovy
sql.rows('select * from tarefa where tenant_id = ?', [Tenants.currentId()])
```

Os filtros do plugin hibernate-filter não podem ligar nem desligar o filtro de tenant (`_tenantId`): use `Tenants.withoutId`.

## Diferenças para o Grails

| Recurso | Grox |
|---|---|
| Modos DATABASE e SCHEMA | ❌ |
| `UserTenantResolver` (tenant do usuário logado) | *(extensão Grox)* |
| `SubDomainTenantResolver`, `HttpHeaderTenantResolver`, `CookieTenantResolver` | ❌ (implemente um `TenantResolver`) |
| `eachTenant` | ❌ |
| Resolver que consulta o banco | Roda em sessão isolada |
| Evento de token REST | Já vê o usuário no contexto, então o resolver enxerga o tenant |

## Veja também

- [Domain classes](domain.md) (`hasOne` com filho multi-tenant)
- [Segurança: autenticação e usuários](seguranca-autenticacao.md)
- [Services e injeção de dependências](services.md)
- [Configuração](../referencia/configuracao.md)
