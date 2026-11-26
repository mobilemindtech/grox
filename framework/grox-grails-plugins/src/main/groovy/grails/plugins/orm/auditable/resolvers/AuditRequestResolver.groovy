package grails.plugins.orm.auditable.resolvers

import groovy.transform.CompileStatic

/** Quem fez e em que URI: bean {@code auditRequestResolver} (pode ser redefinido no resources.groovy). */
@CompileStatic
interface AuditRequestResolver {
    String getCurrentActor()
    String getCurrentURI()
}
