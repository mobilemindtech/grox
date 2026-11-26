# Perguntas frequentes

Respostas curtas para as dúvidas que mais aparecem, com links para o capítulo que detalha cada assunto.

## Geral

**Posso usar os jars do Grails ou do GORM junto com o Grox?**
Não. Eles são compilados com Groovy ≤ 5 e usam `org.codehaus.groovy.runtime.callsite.CallSite`, que não existe no Groovy 6: falham com `NoClassDefFoundError`. O Grox reimplementa a API (mesmos nomes e pacotes), sem Spring. O mesmo vale para plugins e bibliotecas Groovy de terceiros compiladas com versões antigas: confira antes de adotar.

**E bibliotecas Java comuns?**
Funcionam normalmente (JDBC, POI, Jackson, clientes HTTP...). Bibliotecas que recebem `HttpServletRequest` também, porque `request`, `response` e `session` implementam a Servlet API do jar oficial.

**Preciso do Spring?**
Não. As classes do Spring que o código Grails costuma usar (`HttpStatus`, `@Autowired`, `MessageSource`, `@Transactional`, `PasswordEncoder`...) existem como compatibilidade, sem o framework.

**O Grox é compatível com o Grails 7 ou 8?**
A API emulada é a do Grails 3 em diante, validada com projetos Grails 8 (Hibernate 7). Veja [Compatibilidade com o Grails](../referencia/compatibilidade.md).

**Preciso do layout `grails-app/`?**
Não. Com os módulos `grox-core`, `grox-gorm`, `grox-gsp`, `grox-security` e `grox-dsl`, a aplicação declara rotas por closure ou anotação. Veja [Trilha 2: usar sem Grails](../sem-grails/visao-geral.md).

## Dados

**Alterei uma entidade carregada e a mudança não foi gravada. Por quê?**
No Grox, alterações em entidades **só são gravadas por `save()`** ou dentro de uma transação que faça commit. No Grails, o flush no fim da requisição às vezes grava sozinho; no Grox, não. Chame `save()`.

**`save()` devolveu `null`. O que houve?**
A validação falhou. Veja `obj.errors`, ou use `save(failOnError: true)` para lançar `ValidationException`. Depois de uma validação que falhou, chame `discard()` antes de continuar.

**Por que meu campo de formulário não foi vinculado?**
`id`, `version`, `dateCreated` e `lastUpdated` nunca são vinculados, nem campos com `bindable: false`. Valor que não converte (ex.: `idade=abc`) deixa a propriedade `null` com um erro `typeMismatch` em `errors`. O `tenantId` também nunca é vinculado.

**Datas no formato `dd/MM/yyyy` dão `typeMismatch`.**
Configure `grails.databinding.dateFormats: ['dd/MM/yyyy', ...]` ou use `@BindingFormat('dd/MM/yyyy')` na propriedade.

**Recebo `LazyInitializationException` fora da action.**
A sessão de dados vale durante a action e a renderização da view. Em tarefas assíncronas, threads próprias e jobs, cada chamada do GORM abre a sua sessão: carregue os dados dentro do mesmo bloco (`withTransaction { }`) em vez de levar entidades carregadas em outro lugar.

**Posso usar `dbCreate: update` num banco existente?**
Com cuidado. O padrão `grox.schema.update: safe` só acrescenta, nunca altera colunas. Antes, gere o DDL com `grox.schema.updateScript` e revise.

**O nome da tabela ficou diferente do Grails.**
O Grox segue a regra do GORM: snake_case, sem separar dígitos e siglas (`valor10Meses` → `valor10meses_id`). Se ainda divergir, use `mapping { table 'x'; prop column: 'y' }` e confira o `orm.xml` gerado (`grox.data.ormXmlFile`).

## Multi-tenancy

**Recebo 403 em páginas que acessam o banco.**
Uma classe `MultiTenant` foi acessada sem tenant resolvido, e a `TenantNotFoundException` vira `403`. Causas comuns: usuário anônimo numa página que lista dados multi-tenant, ou usuário sem `tenantId`. No BootStrap e em jobs, use `Tenants.withId(...) { }`.

**Preciso anotar os services com `@CurrentTenant`?**
Não. No modo DISCRIMINATOR, o tenant é resolvido ao abrir a sessão Hibernate. As anotações servem só para exceções (`@WithoutTenant`, `@Tenant`).

**Minha consulta SQL nativa vê dados de outros tenants.**
SQL nativo não é filtrado. Inclua `tenant_id = :tenant` com `Tenants.currentId()`.

## Segurança

**Formulários migrados do Grails dão 403 no POST.**
O CSRF é ligado por padrão no Grox (o spring-security-core não tem). Use `<g:form>`, que inclui o `_csrf`, envie `X-CSRF-TOKEN` no AJAX, ou desligue com `grails.plugin.springsecurity.csrf.enabled: false`.

**APIs com token precisam de CSRF?**
Não. Bearer, caminhos `stateless`, Basic de clientes de API e POST anônimo sem cookie de sessão (que não venha de outro site) dispensam o token.

**Depois de reiniciar, os JWT emitidos param de valer.**
Defina `rest.token.storage.jwt.secret` (ao menos 32 bytes). Sem ele, o segredo é aleatório por execução.

**Posso ler o mesmo banco de usuários do Grails?**
Sim. As senhas usam o formato do `DelegatingPasswordEncoder` (`{bcrypt}...`, `{SHA-256}...`), e os tokens GORM são gravados como no plugin.

## Concorrência

**Os campos de instância do meu controller sumiram entre requisições.**
No modo `pool`, cada ator tem sua instância do controller, e requisições diferentes podem cair em atores diferentes. Não guarde estado em campos de controller; use a sessão, o banco ou um service.

**Meu service com `static actor = true` travou.**
Provavelmente há um ciclo: o service-ator A chama B, que chama A de volta. Remova o ciclo ou deixe um dos dois como service comum.

**Uma action lenta segura as outras do mesmo controller.**
Com `poolSize` atores, só `poolSize` requisições daquele controller andam ao mesmo tempo. Devolva uma promise (`task { }`) para liberar o ator, ou aumente o `poolSize`.

## Desenvolvimento

**Salvei um controller e nada mudou.**
Confira se o `run` foi pelo plugin `org.grox.app` (com `grox { reload = true }`) e se a compilação passou: uma compilação com erro mantém a versão anterior no ar. O log avisa quando um artefato ficou fixado na camada estável.

**A recarga de uma domain class demora.**
Mudar uma domain class faz a recarga completa (Hibernate e BootStrap), e o Gradle recompila tudo que depende dela. Controllers e services fazem a recarga leve.

**O IntelliJ não completa `Livro.findByTitulo` nem o `params` do controller.**
Os descritores GDSL vêm nos jars do framework: confira se o projeto foi importado pelo Gradle (o IDEA precisa ver os jars no classpath) e se a classe está em `grails-app/` ou `grox-app/`. Com outro nome de diretório, declare a trait (`implements GormEntity<Livro>`, `implements ControllerApi`). Ver [Autocomplete no IntelliJ IDEA](../recursos/estrutura.md#autocomplete-no-intellij-idea).

**Como inspeciono a aplicação rodando?**
Pelo console web em `/console` (development), com `ctx` (todos os beans), as domain classes importadas e rollback opcional.

## Veja também

- [Solução de problemas](problemas.md)
- [Armadilhas da migração](../migracao/armadilhas.md)
- [Armadilhas do Groovy 6](../apendices/groovy6.md)
