package org.grox.core

import org.grox.http.RequestDispatcher

/**
 * Módulo do framework (GORM, GSP, segurança, compatibilidade Grails, plugins...). Descoberto por
 * {@link ServiceLoader} ({@code META-INF/services/org.grox.core.GroxModule}) e chamado pelo
 * {@link GroxRuntime} em cada fase da subida, na ordem de {@link #getOrder}, e na ordem inversa ao
 * parar. Cada fase tem implementação vazia: o módulo sobrescreve só as que usa.
 *
 * Ordem da subida: {@link #getArtefactTypes} (varredura), {@link #prepare}, {@link #initialize}, {@link #contributeBeans}, (services da aplicação),
 * {@link #contributeApplicationBeans}, (injeção), {@link #beansReady}, ({@code @PostConstruct}),
 * {@link #contextReady}, {@link #configureWeb}, {@link #awaitReady}, {@link #bootStrap},
 * {@link #started} e {@link #createDispatcher}.
 */
abstract class GroxModule {

    /** Preenchido pelo runtime antes da primeira fase. */
    GroxRuntime runtime

    /** Menor primeiro. Faixas: http 50, dados 100, segurança 200, views 300, assets 350, Grails 500, plugins 600+. */
    abstract int getOrder()

    String getName() {
        getClass().simpleName.replaceAll(/Module$/, '').toLowerCase()
    }

    /** O mesmo módulo na geração anterior (recarga em desenvolvimento), ou null. */
    protected <T extends GroxModule> T previous(Class<T> type) {
        runtime.handoff?.previous?.module(type)
    }

    protected Config getConfig() {
        runtime.config
    }

    /** Tipos de artefato que o módulo reconhece nas classes da aplicação, antes da primeira fase. */
    List<ArtefactType> getArtefactTypes() { [] }

    /** Depois da varredura dos artefatos, antes do {@link #initialize} de todos os módulos: registros globais. */
    void prepare() {}

    /** Antes dos beans: recursos que sobem cedo (Hibernate em paralelo, configuração de segurança). */
    void initialize() {}

    /** Beans do módulo, antes dos services da aplicação (um service com o mesmo nome prevalece). */
    void contributeBeans(Map<String, Object> beans) {}

    /** Beans que vêm depois dos services (Data Services, {@code resources.groovy}). */
    void contributeApplicationBeans(Map<String, Object> beans) {}

    /** Depois da injeção nos services e beans gerenciados, antes do {@code @PostConstruct}. */
    void beansReady(Map<String, Object> beans) {}

    /** Depois do {@code @PostConstruct}: listeners, adaptadores que precisam dos beans prontos. */
    void contextReady() {}

    /** Rotas, views, contexto web, controllers. */
    void configureWeb() {}

    /** Espera o que subiu em paralelo (Hibernate). */
    void awaitReady() {}

    /** Dados iniciais (BootStrap). */
    void bootStrap() {}

    /** Depois do BootStrap, antes do servidor (jobs). */
    void started() {}

    /** Quem atende as requisições: o primeiro módulo que devolver um dispatcher. */
    RequestDispatcher createDispatcher() { null }

    /**
     * Verificações do {@code /health} (nome → teste; {@code false} ou exceção é DOWN), lidas depois
     * do {@link #configureWeb}.
     */
    Map<String, java.util.concurrent.Callable<Boolean>> healthChecks() { [:] }

    /** Exceções que viram um status próprio na resposta (ex.: tenant ausente → 403), sem stack no log. */
    Map<Class<? extends Throwable>, Integer> exceptionStatuses() { [:] }

    /** Resumo para o log de subida ("12 controllers"), ou null. */
    String summary() { null }

    /** O desligamento começou (health OUT_OF_SERVICE); o servidor ainda atende. */
    void shuttingDown() {}

    /**
     * Para de aceitar trabalho (jobs, BootStrap.destroy, atores de controller), antes de fechar os
     * services. Só na geração que ainda não foi fechada por uma recarga.
     */
    void stop(Shutdown shutdown) {}

    /** Libera recursos (Hibernate, sessões, conexões), depois dos services. */
    void release(Shutdown shutdown) {}

    /** Como a geração está sendo fechada. */
    static class Shutdown {
        /** Recarga em desenvolvimento: servidor e sessões continuam. */
        final boolean reload
        /** Recarga leve: Hibernate, segurança e dados continuam (são da próxima geração). */
        final boolean keepData
        /** false no desligamento de uma geração já fechada por uma recarga que falhou. */
        final boolean generation

        Shutdown(boolean reload, boolean keepData, boolean generation) {
            this.reload = reload
            this.keepData = keepData
            this.generation = generation
        }
    }
}
