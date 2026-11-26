package grails.plugins.quartz

import groovy.transform.CompileStatic

/** Compatibilidade com o plugin quartz: o tipo de artefato dos jobs ({@code grailsApplication.getArtefacts(JobArtefactHandler.TYPE)}). */
@CompileStatic
class JobArtefactHandler {
    static final String TYPE = 'Job'
}
