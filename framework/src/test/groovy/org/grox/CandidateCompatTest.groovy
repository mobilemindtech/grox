package org.grox

import grails.plugin.springsecurity.userdetails.GrailsUser
import grails.plugin.springsecurity.userdetails.GrailsUserDetailsService
import grails.plugin.springsecurity.userdetails.NoStackUsernameNotFoundException
import org.grox.core.Config
import org.grox.data.Datastore
import org.grox.data.Gorm
import org.grox.data.GormEvents
import org.grox.plugins.springsecurity.SpringUserDetailsAdapter
import org.grox.testdomain.Turma
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.EventType
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.web.SessionTenantResolver
import org.springframework.context.ApplicationEvent
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.grox.data.GroxTransactionManager
import org.grox.data.Persistence
import org.grox.service.ServiceProxies
import org.springframework.transaction.NoTransactionException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.interceptor.TransactionAspectSupport
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertThrows

/** Compatibilidades levantadas nos candidatos à migração (app B). */
class CandidateCompatTest {

    /** Como o {@code UserPasswordEncoderListener} do s2-quickstart: só PreInsert e PreUpdate. */
    static class NomeListener extends AbstractPersistenceEventListener {
        List<EventType> recebidos = []

        NomeListener(org.grails.datastore.mapping.core.Datastore datastore) { super(datastore) }

        @Override
        protected void onPersistenceEvent(AbstractPersistenceEvent event) {
            recebidos << event.eventType
            if (event.entityObject instanceof Turma) {
                event.entityAccess.setProperty('nome', ((Turma) event.entityObject).nome.toUpperCase())
            }
        }

        @Override
        boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
            eventType == PreInsertEvent || eventType == PreUpdateEvent
        }
    }

    /** {@code userDetailsService} no estilo do spring-security-core (bean do resources.groovy). */
    static class UsuariosService implements GrailsUserDetailsService {
        UserDetails loadUserByUsername(String username, boolean loadRoles) {
            loadUserByUsername(username)
        }

        UserDetails loadUserByUsername(String username) {
            if (username == 'sumido') throw new NoStackUsernameNotFoundException()
            if (username != 'ana') throw new UsernameNotFoundException("não existe: ${username}")
            new GrailsUser('ana', '{noop}x', true, true, false, true,
                    [new SimpleGrantedAuthority('ROLE_ADMIN'), new SimpleGrantedAuthority('ROLE_USER')], 42L)
        }
    }

    /** Service com @Transactional do Spring: REQUIRES_NEW grava mesmo se a transação de fora voltar atrás. */
    static class TurmaService {
        @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
        void registrarIsolado(String nome) {
            new Turma(nome: nome).save(failOnError: true, flush: true)
        }

        @org.springframework.transaction.annotation.Transactional
        boolean participa() {
            TransactionAspectSupport.currentTransactionStatus() != null
        }

        /** Injetado no alvo (o autowiring é feito nele, não no proxy). */
        Object outroService

        String semAnotacao() {
            outroService?.toString()
        }
    }

    static Datastore datastore
    static NomeListener listener

    @BeforeAll
    static void start() {
        def config = new Config([dataSource: [url: 'jdbc:h2:mem:candidateCompat;DB_CLOSE_DELAY=-1', dbCreate: 'create-drop']])
        datastore = new Datastore(config, [Turma, org.grox.testdomain.Aluno, org.grox.testdomain.Order, org.grox.testdomain.Inscricao],
                CandidateCompatTest.classLoader, true)
        Gorm.init(datastore)
        listener = new NomeListener(datastore)
        assert GormEvents.register(listener) == 1
    }

    @AfterAll
    static void stop() {
        GormEvents.reset()
        Gorm.reset()
    }

    @Test
    void 'AbstractPersistenceEventListener recebe so os eventos que suporta e altera a entidade'() {
        Turma.withTransaction { new Turma(nome: 'noturna').save(failOnError: true, flush: true) }
        Turma.withTransaction {
            def t = Turma.findByNome('NOTURNA')
            assert t && listener.datastore.is(datastore)
            t.nome = 'diurna'
            t.save(flush: true)
        }
        assert Turma.withTransaction { Turma.findByNome('DIURNA') }
        Turma.withTransaction { Turma.findByNome('DIURNA').delete(flush: true) }
        // PostInsert, PreDelete etc. não chegam: supportsEventType recusa
        assert listener.recebidos.unique(false) == [EventType.PreInsert, EventType.PreUpdate]
    }

    @Test
    void 'userDetailsService no estilo Spring vira fonte de usuarios do Grox'() {
        def adapter = new SpringUserDetailsAdapter(new UsuariosService())
        def u = adapter.loadUserByUsername('ana')
        assert u.username == 'ana' && u.id == 42L && u.enabled
        assert u.roles as Set == ['ROLE_ADMIN', 'ROLE_USER'] as Set
        assert u.passwordExpired && !u.accountLocked && !u.accountExpired   // credentialsNonExpired = false
        assert adapter.loadUserByUsername('outro') == null
        assert adapter.loadUserByUsername('sumido') == null
        assert new NoStackUsernameNotFoundException().stackTrace.length == 0
    }

    @Test
    void 'transactionManager programatico no estilo Spring'() {
        def tm = new GroxTransactionManager()
        def definicao = new DefaultTransactionDefinition(name: 'manualTx', propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRED)
        Persistence.withSession {
            assertThrows(NoTransactionException) { TransactionAspectSupport.currentTransactionStatus() }
            def status = tm.getTransaction(definicao)
            assert status.newTransaction && TransactionAspectSupport.currentTransactionStatus().is(status)
            new Turma(nome: 'tm-commit').save(failOnError: true)
            def interna = tm.getTransaction(definicao)        // participa da de fora
            assert !interna.newTransaction
            tm.commit(interna)
            tm.commit(status)
            assert status.completed
        }
        Persistence.withSession {
            def status = tm.getTransaction(definicao)
            new Turma(nome: 'tm-rollback').save(failOnError: true, flush: true)
            tm.rollback(status)
        }
        // o NomeListener (registrado nesta classe) grava os nomes em maiúsculas
        assert Turma.withTransaction { Turma.findByNome('TM-COMMIT') && !Turma.findByNome('TM-ROLLBACK') }
        assertThrows(UnsupportedOperationException) {
            Persistence.withSession { tm.getTransaction(new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW)) }
        }
    }

    @Test
    void 'Transactional do Spring em service: REQUIRES_NEW e REQUIRED'() {
        def target = new TurmaService(outroService: 'injetado')
        def proxy = (TurmaService) ServiceProxies.create(TurmaService, target, false, org.grox.service.ServiceRegistry.interceptors(TurmaService, target), [])
        // método sem anotação também vai ao alvo, que tem as dependências injetadas (como o ApiAgendamentoService do app-c-api)
        assert proxy.semAnotacao() == 'injetado' && proxy.outroService == 'injetado'
        assert org.grox.data.GormServiceInterceptor.transactionalMethods(TurmaService, ServiceProxies.proxiedMethods(TurmaService)) ==
                [registrarIsolado: Propagation.REQUIRES_NEW, participa: Propagation.REQUIRED]
        assert proxy.participa()
        Turma.withTransaction { status ->
            proxy.registrarIsolado('isolada')
            status.setRollbackOnly()
        }
        assert Turma.withTransaction { Turma.findByNome('ISOLADA') }
    }

    @Test
    void 'GPars minimo: withPool com collectParallel e DefaultPGroup task'() {
        def ativos = java.util.concurrent.atomic.AtomicInteger.newInstance(), pico = java.util.concurrent.atomic.AtomicInteger.newInstance()
        List r = groovyx.gpars.GParsPool.withPool(2) {
            (1..6).toList().collectParallel { int n ->
                pico.accumulateAndGet(ativos.incrementAndGet()) { a, b -> Math.max(a, b) }
                Thread.sleep(20)
                ativos.decrementAndGet()
                n * 10
            }
        }
        assert r == [10, 20, 30, 40, 50, 60] && pico.get() <= 2
        assert groovyx.gpars.GParsPool.withPool { [1, 2, 3, 4].findAllParallel { it % 2 == 0 } } == [2, 4]
        assert new groovyx.gpars.group.DefaultPGroup().task { 'feito' }.get() == 'feito'
    }

    @Test
    void 'job triggers() como no plugin quartz (telas de status dos jobs)'() {
        def t = new org.grox.testjobs.RelatorioJob().triggers()
        assert t.type == 'cron' && t.triggerAttributes.cronExpression == '0 30 5 ? * *' && t.triggerAttributes.name == 'relatorio'
        assert new org.grox.testjobs.SemTriggerJob().triggers() == null
        assert new org.grox.testjobs.RelatorioJob() instanceof org.grox.plugins.quartz.JobApi
    }

    @Test
    void 'messageSource do Spring devolve null para mensagem inexistente sem padrao'() {
        def ms = new org.grox.i18n.SpringMessageSource(new org.grox.i18n.MessageSource(getClass().classLoader, 'messages', Locale.forLanguageTag('pt-BR')))
        assert ms.getMessage('nao.existe.de.jeito.nenhum', null, null, Locale.forLanguageTag('pt-BR')) == null
        assert ms.getMessage('nao.existe.de.jeito.nenhum', null, 'padrão', Locale.forLanguageTag('pt-BR')) == 'padrão'
        assert ms.getMessage('default.blank.message', ['nome', 'X'] as Object[], null, Locale.forLanguageTag('pt-BR'))
    }

    @Test
    void 'excel-export: fillHeader e add com propriedades, caminhos e closures'() {
        def x = new pl.touk.excel.export.XlsxExporter()
        x.fillHeader(['Nome', 'Cidade', 'Dobro'])
        x.add([[nome: 'Ana', end: [cidade: 'POA'], n: 2], [nome: 'Bia', end: [cidade: 'SP'], n: 5]], ['nome', 'end.cidade', { it.n * 2 }])
        assert x.getCellAt(0, 0) == 'Nome' && x.getCellAt(1, 1) == 'POA' && x.getCellAt(2, 0) == 'Bia' && x.getCellAt(2, 2) == 10
        x.close()
    }

    static class EventoA extends ApplicationEvent { EventoA() { super('a') } }
    static class EventoB extends ApplicationEvent { EventoB() { super('b') } }
    static class OuveA implements org.springframework.context.ApplicationListener<EventoA> {
        List recebidos = []
        void onApplicationEvent(EventoA e) { recebidos << e }
    }

    @Test
    void 'ApplicationListener recebe so o tipo de evento declarado'() {
        def ouve = new OuveA()
        def ctx = new grails.core.ApplicationContext([ouve: ouve, outro: 'x'])
        ctx.publishEvent(new EventoA())
        ctx.publishEvent(new EventoB())
        assert ouve.recebidos*.source == ['a']
        assert org.grox.core.ApplicationEvents.eventType(OuveA) == EventoA
    }

    static class MeuPluginGrailsPlugin extends grails.plugins.Plugin {}
    static class ApiController { static namespace = 'v1' }

    @Test
    void 'pluginManager e namespace de artefato'() {
        def pm = new grails.plugins.GrailsPluginManager([new MeuPluginGrailsPlugin()])
        assert pm.allPlugins*.name == ['meuPlugin'] && pm.hasGrailsPlugin('meuPlugin')
        assert new grails.core.GrailsClass(org.grox.testjobs.RelatorioJob, 'Job').namespace == null
        assert new grails.core.GrailsClass(ApiController, 'Controller').namespace == 'v1'
    }

    @Test
    void 'SecurityHeaders do Spring Security: bearerToken'() {
        Map<String, Object> headers = [:]
        org.springframework.security.web.http.SecurityHeaders.bearerToken('abc').accept(headers)
        assert headers.Authorization == 'Bearer abc'
    }

    @Test
    void 'SessionTenantResolver sem requisicao'() {
        assertThrows(TenantNotFoundException) { new SessionTenantResolver().resolveTenantIdentifier() }
    }
}
