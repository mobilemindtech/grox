package grails.plugins.quartz

import grails.core.GrailsClass
import org.grox.core.ClassUtils
import org.grox.plugins.quartz.TriggerCollector

/** {@link GrailsJobClass} do Grox, com os padrões do plugin quartz. */
class DefaultGrailsJobClass extends GrailsClass implements GrailsJobClass {

    private Map triggers

    DefaultGrailsJobClass(Class<?> clazz) {
        super(clazz, JobArtefactHandler.TYPE)
    }

    void execute() {
        newInstance().invokeMethod('execute', null)
    }

    String getGroup() { (staticValue('group') ?: 'GRAILS_JOBS').toString() }

    boolean isConcurrent() { flag('concurrent', true) }

    boolean isSessionRequired() { flag('sessionRequired', true) }

    boolean isDurability() { flag('durability', true) }

    boolean isRequestsRecovery() { flag('requestsRecovery', false) }

    boolean isEnabled() { flag('jobEnabled', true) }

    String getDescription() { (staticValue('description') ?: 'Grails Job').toString() }

    /** Avaliado uma vez, como no plugin: os sem {@code name} recebem o nome completo do job seguido de um contador. */
    synchronized Map getTriggers() {
        if (triggers == null) {
            Map result = [:]
            Object dsl = staticValue('triggers')
            if (dsl instanceof Closure) {
                def builder = new TriggerCollector()
                Closure c = (Closure) ((Closure) dsl).clone()
                c.resolveStrategy = Closure.DELEGATE_FIRST
                c.delegate = builder
                c.call()
                int unnamed = 0
                builder.created.each { Expando t ->
                    Map attrs = (Map) t.getProperty('triggerAttributes')
                    String name = attrs.name ?: "${fullName}${unnamed++}"
                    attrs.name = name
                    result[name] = t
                }
            }
            triggers = result
        }
        triggers
    }

    private Object staticValue(String name) { ClassUtils.staticProperty(clazz, name) }

    private boolean flag(String name, boolean defaultValue) {
        Object v = staticValue(name)
        v == null ? defaultValue : v as boolean
    }
}
