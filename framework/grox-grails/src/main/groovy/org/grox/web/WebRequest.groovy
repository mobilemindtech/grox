package org.grox.web

import org.grox.http.FlashScope
import org.grox.http.GroxMultipartFile
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession
import org.grox.session.RequestSession

/** Estado de uma requisição sendo processada por um controller. Estático: criado em toda requisição. */
@groovy.transform.CompileStatic
class WebRequest implements org.grox.view.ViewRequest {

    static final String FLASH_KEY = FlashScope.KEY
    static final String LOCALE_KEY = 'grox.locale'

    final GroxRequest request
    final GroxResponse response = new GroxResponse()
    final GroxParams params
    final WebContext ctx
    final ControllerArtefact controller
    final String controllerName
    final String actionName
    final RequestSession requestSession
    /** Dentro de uma action a view é renderizada só depois dos interceptors (after). */
    boolean deferViews
    String pendingView
    Map pendingModel
    private String format
    private Locale locale

    /** @param logicalName nome do controller quando não há artefato (o LoginController embutido, por exemplo) */
    WebRequest(GroxRequest request, WebContext ctx, ControllerArtefact controller, String actionName, Map<String, Object> routeParams,
               String logicalName = null) {
        this.request = request
        this.ctx = ctx
        this.controller = controller
        this.controllerName = controller?.logicalName ?: logicalName
        this.actionName = actionName
        this.params = buildParams(request, routeParams)
        if (controllerName) params.put('controller', controllerName)
        if (actionName) params.put('action', actionName)
        this.requestSession = RequestSession.of(request, ctx.sessions)
        request.sessionProvider = { Boolean create -> getSession(create) } as java.util.function.Function<Boolean, GroxSession>
        if (request.getAttribute('grox.flashAdvanced') == null && requestSession.hasCookie()) {
            request.setAttribute('grox.flashAdvanced', true)
            ((FlashScope) requestSession.get(false)?.getAttribute(FLASH_KEY))?.advance()
        }
    }

    static GroxParams buildParams(GroxRequest r, Map<String, Object> route) {
        def p = new GroxParams()
        for (Map<String, List<String>> source : [r.queryParameters, r.formParameters]) {
            source?.each { String k, List<String> vs -> p.put(k, vs.size() == 1 ? vs[0] : new ArrayList(vs)) }
        }
        r.files?.each { String k, List<GroxMultipartFile> fs -> p.put(k, fs.size() == 1 ? fs[0] : new ArrayList(fs)) }
        route?.each { k, v -> p.put(k, v) }
        p
    }

    Class<?> getControllerClass() { controller?.type }

    org.grox.routing.LinkGenerator getLinks() { ctx?.links }

    org.grox.i18n.MessageSource getMessages() { ctx?.messages }

    org.grox.http.StaticResources getStatics() { ctx?.statics }

    org.grox.view.GspViews getViews() { ctx?.views }

    /** O {@code grailsApplication}. */
    Object getApplication() { ctx?.application }

    GroxSession getSession() {
        getSession(true)
    }

    GroxSession getSession(boolean create) {
        requestSession.get(create)
    }

    FlashScope getFlash() {
        GroxSession s = getSession(true)
        FlashScope f = (FlashScope) s.getAttribute(FLASH_KEY)
        if (f == null) {
            f = new FlashScope()
            s.setAttribute(FLASH_KEY, f)
        }
        f
    }

    String getFormat() {
        if (format == null) format = Formats.resolve(this)
        format
    }

    /** {@code ?lang=pt_BR} (guardado na sessão), sessão, Accept-Language ou padrão. */
    Locale getLocale() {
        if (locale != null) return locale
        Object lang = params.get('lang')
        if (lang) {
            locale = Locale.forLanguageTag(lang.toString().replace('_', '-'))
            getSession(true).setAttribute(LOCALE_KEY, locale)
            return locale
        }
        Locale stored = (Locale) getSession(false)?.getAttribute(LOCALE_KEY)
        if (stored != null) return locale = stored
        String accept = request.getHeader('Accept-Language')
        if (accept) {
            try {
                List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(accept)
                if (ranges && ranges[0].range != '*') return locale = Locale.forLanguageTag(ranges[0].range)
            } catch (IllegalArgumentException ignored) {
            }
        }
        locale = ctx.messages?.defaultLocale ?: Locale.default
    }

    /** A sessão é gravada pelo WebDispatcher ao fim da requisição (inclusive redespachos de erro). */
    void finish() {
    }
}
