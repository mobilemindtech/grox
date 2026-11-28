package org.grox.security

import groovy.util.logging.Slf4j
import org.grox.core.GroxModule

/**
 * Segurança (filtro, regras, form login, Basic, JWT, CSRF): ativa se houver
 * {@code grails.plugin.springsecurity} no application.yml. As APIs do spring-security
 * ({@code springSecurityService}...) ficam no módulo do plugin.
 */
@Slf4j
class SecurityModule extends GroxModule {

    SecurityFilter security
    /** O filtro no pipeline HTTP do core (null sem segurança). */
    SecurityRequestFilter requestFilter

    /**
     * Adapta um bean {@code userDetailsService} de outra API (o do spring-security, no módulo do
     * plugin) ao {@link UserDetailsService} do Grox; devolve null se não reconhecer.
     */
    Closure<UserDetailsService> userDetailsAdapter

    /** Fontes de usuários de outros módulos (GORM), depois das do yml; preenchidas no {@code initialize}. */
    final List<UserDetailsService> userSources = []

    int getOrder() { 200 }

    @Override
    void initialize() {
        if (runtime.handoff?.keepData) {
            // recarga leve: mesma configuração e mesmo segredo de JWT (tokens emitidos continuam válidos)
            security = previous(SecurityModule).security
        } else {
            createSecurity()
        }
    }

    private void createSecurity() {
        def cfg = SecurityConfig.from(config)
        if (!cfg.active) return
        security = new SecurityFilter(cfg)
        Passwords.configure(cfg.passwordAlgorithm, cfg.passwordIterations, cfg.passwordBase64, cfg.bcryptLogrounds)
        if (config.getProperty('grox.security.loginThrottle.enabled', Boolean, true)) {
            security.throttle = new LoginThrottle(config.getProperty('grox.security.loginThrottle.maxAttempts', Integer, 5),
                    config.getProperty('grox.security.loginThrottle.maxAttemptsPerUser', Integer, 20),
                    config.getProperty('grox.security.loginThrottle.windowSeconds', Integer, 300))
        }
        // revogação de JWT compartilhada quando as sessões estão no Redis
        String revocations = config.getProperty('grox.security.tokenRevocation.store', String, config.getProperty('grox.session.store', String, 'memory'))
        if (security.jwt != null && revocations == 'redis') {
            security.jwt.revocations = new TokenRevocations.Redis(config.getProperty('grox.session.redis.url', String, 'redis://localhost:6379'))
        }
        String rest = !cfg.restActive ? 'não' : (cfg.gormTokenStorage ? "gorm (${cfg.tokenDomainClassName})" : 'jwt') +
                (cfg.useBearerToken ? ', Authorization: Bearer' : ", cabeçalho ${cfg.tokenHeaderName}")
        log.info("Segurança ativa: ${cfg.rules.size()} regras, basic=${cfg.basicAuth}, rest=${rest}, csrf=${cfg.csrfEnabled}")
    }

    /** No pipeline do core: endpoints, sessões, mensagens e os destinos do roteador. */
    @Override
    void configureWeb() {
        def pipeline = runtime.module(org.grox.http.HttpModule)?.pipeline
        if (security == null || pipeline == null) return
        security.endpoints = pipeline.endpoints   // a mesma lista: o console entra depois
        security.sessions = pipeline.sessions
        security.messages = runtime.messages
        // classe, não closure com "as Predicate" (um proxy do JDK chamado em toda requisição)
        security.hasTarget = new java.util.function.Predicate<String>() {
            @Override
            boolean test(String name) { pipeline.router != null && pipeline.router.hasTarget(name) }
        }
        requestFilter = new SecurityRequestFilter(security)
        pipeline.filter = requestFilter
    }

    /**
     * O {@code springSecurityService} vem do módulo do plugin ({@code grox-grails-plugins}); sem ele, uma propriedade
     * com esse nome fica null sem erro, como qualquer bean ausente. Avisa na subida quem a declara.
     */
    @Override
    void beansReady(Map<String, Object> beans) {
        if (security == null) return
        List<String> who = declaringSpringSecurityService(runtime.application.allArtefacts + beans.values()*.getClass(), beans)
        if (who) {
            log.warn("${who.join(', ')} declara springSecurityService, mas esse bean só existe com o módulo grox-grails-plugins " +
                    '(plugin spring-security): a propriedade fica null. Inclua o módulo ou use request.userPrincipal e SecurityContextHolder')
        }
    }

    /** Nomes das classes que declaram a propriedade {@code springSecurityService} quando o bean não existe. */
    static List<String> declaringSpringSecurityService(Collection<Class<?>> classes, Map<String, Object> beans) {
        if (beans.containsKey('springSecurityService')) return []
        classes.findAll { Class<?> c -> c.metaClass.getMetaProperty('springSecurityService') != null }*.simpleName.unique().sort()
    }

    @Override
    void contextReady() {
        if (security) security.users = userDetailsService()
    }

    /** Service {@code userDetailsService} da aplicação, usuários do yml e GORM, nessa ordem. */
    private UserDetailsService userDetailsService() {
        def cfg = security.cfg
        List<UserDetailsService> chain = []
        // service ou bean do resources.groovy; no estilo Grox ou no do Spring Security (UserDetails/GrailsUser)
        Object custom = runtime.autowirer?.beans?.get('userDetailsService') ?: runtime.services.beans['userDetailsService']
        if (custom instanceof UserDetailsService) chain << (UserDetailsService) custom
        else if (custom != null && userDetailsAdapter != null) {
            UserDetailsService adapted = userDetailsAdapter.call(custom)
            if (adapted != null) chain << adapted
        }
        def memory = new InMemoryUserDetailsService(cfg.users)
        if (!memory.empty) chain << memory
        chain.addAll(userSources)
        if (!chain) log.warn('Segurança ativa sem fonte de usuários (userLookup.userDomainClassName, users ou um UserDetailsService)')
        new CompositeUserDetailsService(chain)
    }

    @Override
    void release(Shutdown shutdown) {
        if (shutdown.reload && shutdown.keepData) return
        security?.jwt?.revocations?.close()
        if (shutdown.reload) return
        Passwords.custom = null
        Passwords.passwordService = null
    }
}
