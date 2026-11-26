package org.grox.dsl

import org.grox.core.ClassUtils
import org.grox.data.json.GormJson
import org.grox.http.FlashScope
import org.grox.http.GroxMultipartFile
import org.grox.http.GroxParams
import org.grox.http.GroxRequest
import org.grox.http.GroxResponse
import org.grox.http.GroxSession
import org.grox.http.MimeTypes
import org.grox.http.StaticResources
import org.grox.i18n.MessageSource
import org.grox.routing.LinkGenerator
import org.grox.session.RequestSession
import org.grox.view.GspViews
import org.grox.view.ViewRequest

/**
 * A requisição de uma rota do {@code grox-dsl}: o delegate dos handlers por closure e o que o
 * {@link HandlerApi} expõe nas classes anotadas. Respostas no estilo dos controllers do Grails:
 * {@code render view:/model:}, {@code render json:}, {@code render text:}, {@code respond},
 * {@code redirect}. Também é a {@link ViewRequest} das GSPs que ela renderiza.
 */
class RouteContext implements ViewRequest {

    static final String LOCALE_KEY = 'grox.locale'

    final GroxRequest request
    final GroxResponse response = new GroxResponse()
    final GroxParams params
    final Route route
    final DslEnvironment env
    private final RequestSession requestSession
    private Locale locale
    /** Algum {@code render}/{@code respond}/{@code redirect} já escreveu a resposta. */
    boolean rendered

    RouteContext(GroxRequest request, Route route, Map<String, Object> routeParams, DslEnvironment env) {
        this.request = request
        this.route = route
        this.env = env
        this.params = buildParams(request, routeParams)
        this.requestSession = RequestSession.of(request, env.sessions)
        request.sessionProvider = { Boolean create -> getSession(create) } as java.util.function.Function<Boolean, GroxSession>
        if (request.getAttribute('grox.flashAdvanced') == null && requestSession.hasCookie()) {
            request.setAttribute('grox.flashAdvanced', true)
            ((FlashScope) requestSession.get(false)?.getAttribute(FlashScope.KEY))?.advance()
        }
    }

    /** Query string, formulário, arquivos e as variáveis do caminho, nessa ordem de precedência crescente. */
    static GroxParams buildParams(GroxRequest r, Map<String, Object> route) {
        def p = new GroxParams()
        for (Map<String, List<String>> source : [r.queryParameters, r.formParameters]) {
            source?.each { String k, List<String> vs -> p.put(k, vs.size() == 1 ? vs[0] : new ArrayList(vs)) }
        }
        r.files?.each { String k, List<GroxMultipartFile> fs -> p.put(k, fs.size() == 1 ? fs[0] : new ArrayList(fs)) }
        route?.each { k, v -> p.put(k, v) }
        p
    }

    // --- respostas no estilo do Grails ---

    /**
     * {@code render view: 'livros/index', model: [...]}, {@code render json: obj}, {@code render text: 'x',
     * contentType: 'text/csv'}, com {@code status:} opcional.
     */
    void render(Map args) {
        if (args.status != null) response.status = args.status as int
        if (args.view) {
            String view = args.view.toString()
            env.views.render(view.startsWith('/') ? view : '/' + view, (Map) args.model ?: [:], this)
        } else if (args.containsKey('json')) {
            response.contentType = 'application/json'
            response.writer.write(GormJson.toJson(args.json))
        } else if (args.containsKey('text')) {
            response.contentType = args.contentType?.toString() ?: 'text/plain'
            response.writer.write(args.text?.toString() ?: '')
        } else if (args.status == null) {
            throw new IllegalArgumentException("render: informe view, json, text ou status (${args.keySet()})")
        }
        rendered = true
    }

    /**
     * DSL de HTML ({@link org.grox.view.html.HtmlRender}): {@code render(layout: 'main') { h1 'Livros' }}. Com
     * {@code contentType} JSON, o closure monta o JSON ({@code JsonBuilder}); com XML, o {@code MarkupBuilder} puro.
     */
    void render(Map args, Closure body) {
        String type = args.contentType?.toString()
        if (type?.contains('json')) {
            render(text: new groovy.json.JsonBuilder().call(body).toString(), contentType: type, status: args.status)
        } else if (type?.contains('xml')) {
            render(text: org.grox.view.html.HtmlRender.xml(body), contentType: type, status: args.status)
        } else {
            render(text: org.grox.view.html.HtmlRender.render(body, this, env.views, (Map) args.model ?: [:], args.layout?.toString()),
                    contentType: type ?: 'text/html', status: args.status)
        }
    }

    void render(Closure body) {
        render([:], body)
    }

    /** {@code render 'texto'}. */
    void render(CharSequence text) {
        render(text: text)
    }

    /**
     * Como o {@code respond} do Grails: HTML com {@code view:} (modelo {@code [livro: x]} ou {@code [livroList: [...]]},
     * ou o {@code model:} dado) quando o cliente pede HTML; senão JSON. {@code null} é 404.
     */
    void respond(Object value, Map args = [:]) {
        if (value == null && args.status == null) {
            response.sendError(404)
            rendered = true
            return
        }
        if (args.view && wantsHtml()) {
            render(view: args.view, model: args.model ?: [(modelName(value)): value], status: args.status)
        } else {
            render(json: value, status: args.status)
        }
    }

    /** {@code respond x, status: 201}: em Groovy os argumentos nomeados vêm primeiro. */
    void respond(Map args, Object value) {
        respond(value, args)
    }

    /** {@code redirect url: '/x'}, {@code redirect uri: '/x'} ou {@code redirect controller:, action:, id:}; {@code permanent: true} dá 301. */
    void redirect(Map args) {
        String url = args.url?.toString() ?: args.uri?.toString() ?: env.links.link(args.findAll { k, v -> k != 'permanent' })
        response.sendRedirect(url, args.permanent ? 301 : 302)
        rendered = true
    }

    void redirect(CharSequence url) {
        redirect(url: url)
    }

    /** O cliente prefere HTML (sem {@code .json} no caminho e com {@code text/html} no Accept, ou sem Accept). */
    boolean wantsHtml() {
        if (request.path.endsWith('.json')) return false
        String accept = request.getHeader('Accept')
        !accept || MimeTypes.fromAccept(accept) == 'html' || accept.contains('text/html')
    }

    private static String modelName(Object value) {
        if (value instanceof Collection) {
            Object first = ((Collection) value) ? ((Collection) value).iterator().next() : null
            return first == null ? 'list' : ClassUtils.propertyName(first.getClass()) + 'List'
        }
        ClassUtils.propertyName(value.getClass())
    }

    /** Resposta ao fim do handler: sem {@code render}, um Map devolvido vira o modelo da {@code view:} da rota. */
    GroxResponse complete(Object result) {
        if (!rendered && route.view && result instanceof Map) render(view: route.view, model: (Map) result)
        response
    }

    /** A configuração da aplicação (yml, groovy e os padrões da DSL de projeto). */
    org.grox.core.Config getConfig() { env.config }

    /** Services e beans pelo nome ({@code livroService.todos()}), como nos controllers do Grails. */
    Object propertyMissing(String name) {
        Object bean = env.beans[name]
        if (bean != null) return bean
        throw new MissingPropertyException(name, RouteContext)
    }

    // --- ViewRequest ---

    String getControllerName() { route.group.name }

    String getActionName() { route.action }

    Class<?> getControllerClass() { route.group.type }

    LinkGenerator getLinks() { env.links }

    MessageSource getMessages() { env.messages }

    StaticResources getStatics() { env.statics }

    GspViews getViews() { env.views }

    Object getApplication() { env.application }

    GroxSession getSession() { getSession(true) }

    GroxSession getSession(boolean create) { requestSession.get(create) }

    FlashScope getFlash() {
        GroxSession s = getSession(true)
        FlashScope f = (FlashScope) s.getAttribute(FlashScope.KEY)
        if (f == null) {
            f = new FlashScope()
            s.setAttribute(FlashScope.KEY, f)
        }
        f
    }

    /** {@code ?lang=pt_BR} (guardado na sessão), sessão, Accept-Language ou o padrão das mensagens. */
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
        locale = env.messages?.defaultLocale ?: Locale.default
    }
}
