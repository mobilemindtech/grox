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
        request.sessionProvider = (Boolean create) -> getSession(create)   // lambda nativa: "as Function" criava um proxy do JDK por requisição
        if (request.getAttribute('grox.flashAdvanced') == null && requestSession.hasCookie()) {
            request.setAttribute('grox.flashAdvanced', true)
            ((FlashScope) requestSession.get(false)?.getAttribute(FLASH_KEY))?.advance()
        }
    }

    static GroxParams buildParams(GroxRequest r, Map<String, Object> route) {
        def p = new GroxParams()
        // laços, não each: a closure era chamada pelo runtime a cada parâmetro
        for (Map<String, List<String>> source : [r.queryParameters, r.formParameters]) {
            if (source == null) continue
            for (Map.Entry<String, List<String>> e : source.entrySet()) {
                List<String> vs = e.value
                p.put(e.key, vs.size() == 1 ? (Object) vs.get(0) : new ArrayList(vs))
            }
        }
        if (r.files != null) {
            for (Map.Entry<String, List<GroxMultipartFile>> e : r.files.entrySet()) {
                List<GroxMultipartFile> fs = e.value
                p.put(e.key, fs.size() == 1 ? (Object) fs.get(0) : new ArrayList(fs))
            }
        }
        if (route != null) p.putAll(route)
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

    /** Sem sessão, um flash que só a cria na primeira escrita ({@link FlashScope#of}). */
    FlashScope getFlash() {
        FlashScope.of(request, { Boolean create -> getSession(create) } as java.util.function.Function<Boolean, GroxSession>)
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
