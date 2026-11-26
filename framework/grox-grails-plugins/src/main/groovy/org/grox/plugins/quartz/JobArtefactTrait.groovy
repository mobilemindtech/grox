package org.grox.plugins.quartz

import groovy.transform.CompileStatic
import org.grox.ast.ArtefactTrait

/** {@code grails-app/jobs/*Job}: trait {@link JobApi}. */
@CompileStatic
class JobArtefactTrait implements ArtefactTrait {
    String getDirectory() { 'jobs' }

    String getSuffix() { 'Job' }

    Class<?> getTrait() { JobApi }
}
