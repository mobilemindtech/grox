package org.grox.web

import grails.converters.JSON
import org.grox.core.ClassUtils
import org.grox.http.GroxResponse
import org.grox.web.json.JsonSupport

/** Implementação de render/redirect/respond/withFormat usada pelo trait dos controllers. */
class ControllerSupport {

    /** {@code status: 404}, {@code status: '404'} ou {@code status: HttpStatus.NOT_FOUND}. */
    static int statusCode(Object status) {
        if (status instanceof org.springframework.http.HttpStatus) return ((org.springframework.http.HttpStatus) status).value()
        if (status instanceof Number) return ((Number) status).intValue()
        Integer.parseInt(status.toString().trim())
    }


    static void render(WebRequest wr, Map args) {
        GroxResponse resp = wr.response
        if (args.status != null) resp.status = statusCode(args.status)
        if (args.contentType) resp.contentType = args.contentType.toString()
        if (args.encoding) resp.characterEncoding = args.encoding.toString()
        if (args.containsKey('text')) {
            // como no Grails (textArg.toString()): text: null envia "null", não corpo vazio
            writeText(wr, String.valueOf(args.text), 'text/html')
        } else if (args.view) {
            renderView(wr, resolveView(wr, args.view.toString()), (Map) (args.model ?: [:]))
        } else if (args.template) {
            wr.ctx.viewRenderer.renderTemplate(resolveTemplate(wr, args.template.toString()), args, wr)
            resp.committed = true
        } else if (args.file != null) {
            renderFile(wr, args)
        } else if (args.containsKey('json')) {
            renderJson(wr, args.json)
        } else {
            resp.committed = true
        }
    }

    static void writeText(WebRequest wr, CharSequence text, String defaultContentType) {
        GroxResponse resp = wr.response
        if (!resp.contentType) resp.contentType = defaultContentType
        resp.writer.write(text.toString())
        resp.committed = true
    }

    static void renderJson(WebRequest wr, Object value) {
        GroxResponse resp = wr.response
        resp.contentType = 'application/json'
        if (value instanceof JSON) {
            ((JSON) value).render(resp)
            return
        }
        resp.writer.write(JsonSupport.toJson(value))
        resp.committed = true
    }

    /**
     * {@code attachment} com o nome seguro: sem caracteres de controle (CRLF), aspas e barras
     * escapadas, e {@code filename*} em UTF-8 para nomes com acento (RFC 6266).
     */
    static String contentDisposition(String fileName) {
        String clean = fileName.replaceAll(/[\p{Cntrl}]/, '')
        String ascii = clean.replaceAll(/[^\x20-\x7e]/, '_')
        String value = "attachment;filename=\"${ascii.replace('\\', '\\\\').replace('"', '\\"')}\""
        if (ascii != clean) value += ";filename*=UTF-8''" + URLEncoder.encode(clean, 'UTF-8').replace('+', '%20')
        value
    }

    private static void renderFile(WebRequest wr, Map args) {
        GroxResponse resp = wr.response
        Object f = args.file
        if (!resp.contentType) resp.contentType = 'application/octet-stream'
        if (args.fileName) resp.setHeader('Content-Disposition', contentDisposition(args.fileName.toString()))
        if (f instanceof byte[]) {
            resp.body = (byte[]) f
        } else if (f instanceof File) {
            resp.body = ((File) f).bytes
        } else {
            // o stream é fechado aqui, como no Grails (antes ficava aberto até o GC)
            ((InputStream) f).withCloseable { InputStream input -> input.transferTo(resp.outputStream) }
        }
        resp.committed = true
    }

    static void redirect(WebRequest wr, Map args) {
        wr.response.sendRedirect(wr.ctx.links.link(args, wr.controllerName), args.permanent ? 301 : 302)
    }

    static String createLink(WebRequest wr, Map attrs) {
        wr.ctx.links.link(attrs, wr.controllerName)
    }

    /**
     * {@code respond obj, [status:, view:, model:, formats:]}: JSON quando o formato
     * negociado é json; senão renderiza a view com o modelo
     * ({@code usuario} ou {@code usuarioList}/{@code usuarioCount}).
     */
    static void respond(WebRequest wr, Object value, Map args) {
        if (value == null && args.status == null) {
            wr.response.sendError(404)
            return
        }
        List<String> formats = (args.formats ?: wr.controller?.responseFormats) as List<String>
        String fmt = wr.format
        if (fmt == 'all') fmt = formats ? formats[0] : 'html'
        if (formats && !(fmt in formats)) {
            wr.response.sendError(406)
            return
        }
        if (args.status != null) wr.response.status = statusCode(args.status)
        if (fmt == 'json') {
            if (value == null) {
                wr.response.committed = true
            } else {
                renderJson(wr, value)
            }
        } else {
            Map model = value == null ? [:] : new LinkedHashMap(modelFor(wr, value))
            if (args.model instanceof Map) model.putAll((Map) args.model)
            renderView(wr, resolveView(wr, (args.view ?: wr.actionName).toString()), model)
        }
    }

    /**
     * Modelo do {@code respond}: {@code usuario}, ou {@code usuarioList}/{@code usuarioCount}. Como no
     * Grails, {@code grails.scaffolding.templates.domainSuffix} (ex.: {@code Instance}) entra no nome:
     * {@code usuarioInstance}, {@code usuarioInstanceList}.
     */
    static Map modelFor(WebRequest wr, Object value) {
        if (value instanceof Map) return (Map) value
        // respond x.errors, view: 'create', model: [x: x]: os erros já estão no objeto do modelo
        if (value instanceof org.springframework.validation.Errors) return [:]
        String suffix = wr.ctx?.application?.config?.getProperty('grails.scaffolding.templates.domainSuffix', String, '') ?: ''
        if (value instanceof Collection) {
            Collection c = (Collection) value
            String n = (c ? ClassUtils.propertyName(c.iterator().next().getClass()) : wr.controllerName) + suffix
            return [("${n}List".toString()): c, ("${n}Count".toString()): c.size()]
        }
        [(ClassUtils.propertyName(value.getClass()) + suffix): value]
    }

    /**
     * {@code withFormat} do controller: bloco pelo formato da resposta (Accept, extensão, {@code format}).
     * Com {@code requestFormat} (o {@code request.withFormat}), pelo Content-Type da requisição, como no Grails.
     */
    static Object withFormat(WebRequest wr, Closure blocks, String requestFormat = null) {
        Map<String, Closure> collected = [:]
        def collector = new Object() {
            def methodMissing(String name, Object args) {
                Object[] a = (Object[]) args
                // outras chamadas (render, respond...) seguem para o controller
                if (a.length != 1 || !(a[0] instanceof Closure)) throw new MissingMethodException(name, getClass(), a)
                collected[name] = (Closure) a[0]
                null
            }
        }
        Closure c = (Closure) blocks.clone()
        c.delegate = collector
        c.resolveStrategy = Closure.DELEGATE_FIRST
        c.call()
        c.resolveStrategy = Closure.OWNER_ONLY
        String fmt = requestFormat ?: wr.format
        Closure chosen = fmt == 'all' ? (collected.values() ? collected.values().first() : null) : (collected[fmt] ?: collected['*'])
        if (chosen == null) {
            wr.response.sendError(406)
            return null
        }
        if (requestFormat == null && fmt != 'all' && collected.containsKey(fmt) && !wr.response.contentType) {
            wr.response.contentType = Formats.mimeType(fmt)
        }
        Closure body = (Closure) chosen.clone()
        body.resolveStrategy = Closure.OWNER_FIRST
        body.call()
    }

    static String resolveView(WebRequest wr, String view) {
        view.startsWith('/') ? view : "/${wr.controllerName}/${view}"
    }

    static String resolveTemplate(WebRequest wr, String template) {
        int i = template.lastIndexOf('/')
        String t = i >= 0 ? template.substring(0, i + 1) + '_' + template.substring(i + 1) : '_' + template
        t.startsWith('/') ? t : "/${wr.controllerName}/${t}"
    }

    static void renderView(WebRequest wr, String path, Map model) {
        // static scaffold = X: a view que falta vem do template de scaffold
        path = org.grox.web.scaffolding.ScaffoldViews.resolve(wr, path)
        if (wr.deferViews) {
            wr.pendingView = path
            wr.pendingModel = model
        } else {
            wr.ctx.viewRenderer.render(path, model, wr)
        }
        wr.response.committed = true
    }

    /** Renderiza a view adiada (depois dos interceptors). */
    static void renderPendingView(WebRequest wr) {
        wr.deferViews = false
        if (wr.pendingView != null) {
            String path = wr.pendingView
            wr.pendingView = null
            wr.ctx.viewRenderer.render(path, wr.pendingModel ?: [:], wr)
        }
    }
}
