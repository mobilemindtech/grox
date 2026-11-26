package org.grox.plugins.quartz

import groovy.util.logging.Slf4j
import org.grox.core.ArtefactType
import org.grox.core.GroxModule
import grails.core.GrailsClass
import grails.plugins.quartz.DefaultGrailsJobClass
import grails.plugins.quartz.JobArtefactHandler
import org.grox.web.GrailsModule

/** Plugin de jobs (substituto do Quartz): classes {@code *Job} de {@code grails-app/jobs}. */
@Slf4j
class JobsModule extends GroxModule {

    JobScheduler jobs
    /** Instâncias dos jobs (beans com o nome completo da classe, como no plugin quartz), agendadas ou não. */
    private List<Object> instances = []

    int getOrder() { 650 }

    @Override
    List<ArtefactType> getArtefactTypes() { [ArtefactType.suffix('Job')] }

    /** {@code grailsApplication.getArtefacts('Job')} como {@link DefaultGrailsJobClass}. */
    @Override
    void prepare() {
        runtime.module(GrailsModule)?.grailsApplication?.grailsClassFactories?.put(JobArtefactHandler.TYPE,
                { Class<?> c -> new DefaultGrailsJobClass(c) } as java.util.function.Function<Class<?>, GrailsClass>)
    }

    /** Os jobs viram beans injetados (inclusive com o agendador desligado), para telas que executam um job à mão. */
    @Override
    void contributeApplicationBeans(Map<String, Object> beans) {
        runtime.application.getArtefacts('Job').findAll { JobApi.isAssignableFrom(it) }.each { Class<?> type ->
            Object job = type.getDeclaredConstructor().newInstance()
            beans[type.name] = job
            runtime.managedBeans << job
            instances << job
        }
    }

    @Override
    void started() {
        if (!instances) return
        boolean enabled = config.getProperty('grox.jobs.enabled', Boolean,
                config.getProperty('quartz.autoStartup', Boolean, config.getProperty('grails.plugin.quartz.autoStartup', Boolean, true)))
        if (!enabled) {
            log.info("Jobs desativados (${instances.size()} encontrados)")
            return
        }
        jobs = new JobScheduler()
        instances.each { jobs.register(it) }
        jobs.start()
    }

    @Override
    void stop(Shutdown shutdown) {
        jobs?.close()
    }
}
