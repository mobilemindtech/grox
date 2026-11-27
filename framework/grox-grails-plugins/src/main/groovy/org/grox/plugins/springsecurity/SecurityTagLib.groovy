package org.grox.plugins.springsecurity

import org.grox.view.taglib.ApplicationTagLib
import org.grox.view.taglib.TagLibApi
import grails.plugin.springsecurity.SpringSecurityUtils
import org.grox.security.AccessExpression
import org.grox.security.Authentication
import org.grox.security.SecurityContextHolder
import org.grox.security.SecurityFilter
import org.grox.http.Html

/** Tags {@code sec:} do spring-security-core. */
class SecurityTagLib implements TagLibApi {

    static namespace = 'sec'
    static returnObjectForTags = ['csrfToken']

    @groovy.transform.CompileStatic
    void ifLoggedIn(Map attrs, Closure body) { if (auth()) ApplicationTagLib.writeBody(out, body) }

    @groovy.transform.CompileStatic
    void ifNotLoggedIn(Map attrs, Closure body) { if (!auth()) ApplicationTagLib.writeBody(out, body) }

    def ifAllGranted = { attrs, body -> if (SpringSecurityUtils.ifAllGranted(attrs.roles.toString())) out << body() }

    def ifAnyGranted = { attrs, body -> if (SpringSecurityUtils.ifAnyGranted(attrs.roles.toString())) out << body() }

    def ifNotGranted = { attrs, body -> if (SpringSecurityUtils.ifNotGranted(attrs.roles.toString())) out << body() }

    def username = { attrs -> out << Html.escape(auth()?.name ?: '') }

    /** {@code <sec:loggedInUserInfo field="tenantId"/>} */
    def loggedInUserInfo = { attrs -> out << Html.escape(auth()?.principal?."${attrs.field}" ?: '') }

    /** {@code <sec:access expression="hasRole('ROLE_ADMIN')">} ou {@code <sec:access url="/admin">} */
    def access = { attrs, body -> if (granted(attrs)) out << body() }

    def noAccess = { attrs, body -> if (!granted(attrs)) out << body() }

    def csrfToken = { attrs -> security()?.cfg?.csrfEnabled ? security().csrfToken(request) : '' }

    def csrfInput = { attrs ->
        if (security()?.cfg?.csrfEnabled) {
            out << '<input type="hidden" name="' << SecurityFilter.CSRF_PARAM << '" value="' << Html.escape(security().csrfToken(request)) << '"/>'
        }
    }

    private boolean granted(Map attrs) {
        if (attrs.expression) return AccessExpression.evaluate(attrs.expression.toString(), auth())
        if (attrs.url) {
            // sem segurança ativa tudo é permitido (cuidado: 'false ?: true' seria true)
            return security() == null || security().isGranted(attrs.url.toString(), (attrs.method ?: 'GET').toString().toUpperCase(), auth())
        }
        throw new IllegalArgumentException('<sec:access> exige expression ou url')
    }

    private static Authentication auth() {
        SecurityContextHolder.authentication
    }

    /** O filtro de segurança da geração corrente, definido pelo {@link SpringSecurityModule} (vale também nas rotas do grox-dsl). */
    static volatile SecurityFilter filter

    private SecurityFilter security() {
        filter
    }
}
