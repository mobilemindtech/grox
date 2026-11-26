package grails.plugins.orm.auditable.resolvers

/** Como no plugin: o {@code toString()} do {@code springSecurityService.currentUser}. */
class SpringSecurityRequestResolver extends DefaultAuditRequestResolver {

    def springSecurityService

    @Override
    String getCurrentActor() {
        springSecurityService?.currentUser?.toString() ?: super.getCurrentActor()
    }
}
