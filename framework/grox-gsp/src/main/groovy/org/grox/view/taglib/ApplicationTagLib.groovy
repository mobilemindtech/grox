package org.grox.view.taglib

import org.grox.i18n.MessageResolvable
import org.grox.http.Html

import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

/** Tags {@code g:} de links, recursos, mensagens e formatação. */
class ApplicationTagLib implements TagLibApi {

    private static final java.util.concurrent.ConcurrentHashMap<String, TimeZone> TIME_ZONES = new java.util.concurrent.ConcurrentHashMap<>()

    /** Limite do cache: o id pode vir de dado do usuário (ids inválidos viram GMT). */
    private static TimeZone timeZone(String id) {
        TimeZone tz = TIME_ZONES.get(id)
        if (tz == null) {
            tz = TimeZone.getTimeZone(id)
            if (TIME_ZONES.size() < 1000) TIME_ZONES.put(id, tz)
        }
        tz
    }


    static returnObjectForTags = ['createLink', 'resource', 'cookie']

    static final List<String> LINK_ATTRS = ['controller', 'action', 'id', 'params', 'url', 'uri', 'absolute', 'base', 'fragment', 'mapping']

    static Map<String, Object> extractLinkAttrs(Map attrs) {
        Map<String, Object> link = [:]
        LINK_ATTRS.each { if (attrs.containsKey(it)) link[it] = attrs.remove(it) }
        link
    }

    def createLink = { Map attrs ->
        webRequest.links.link(attrs, controllerName)
    }

    def link = { Map attrs, body ->
        String href = webRequest.links.link(extractLinkAttrs(attrs), controllerName)
        out << '<a href="' << Html.escape(href) << '"' << Html.attributes(attrs) << '>' << body() << '</a>'
    }

    /** {@code <g:resource dir="css" file="app.css"/>} → {@code /static/css/app.css} */
    def resource = { Map attrs ->
        String path = [attrs.dir, attrs.file].findAll().join('/').replaceAll('/+', '/')
        String prefix = webRequest?.statics?.prefix ?: '/static'
        (attrs.absolute ? (webRequest.links.serverURL ?: '') : '') + prefix + (path.startsWith('/') ? path : '/' + path)
    }

    /** {@code <g:message code="x" args="${[1]}" default="..."/>} ou {@code <g:message error="${erro}"/>} */
    def message = { Map attrs ->
        def messages = webRequest.messages
        String text = attrs.error instanceof MessageResolvable ?
                messages.getMessage((MessageResolvable) attrs.error, webRequest.locale) :
                messages.getMessage(attrs.code?.toString(), attrs.args as List, attrs.default?.toString(), webRequest.locale)
        out << (attrs.encodeAs?.toString()?.toLowerCase() == 'raw' ? text : Html.escape(text))
    }

    /** {@code <g:formatDate date="${d}" format="dd/MM/yyyy"/>} (Date ou java.time). */
    /** {@code <g:formatBoolean boolean="${x}" true="Sim" false="Não"/>} */
    /** {@code <g:cookie name="x"/>} / {@code g.cookie(name: 'x')}: valor do cookie da requisição, ou null. */
    def cookie = { Map attrs ->
        webRequest?.request?.cookieValues?.get(attrs.name?.toString())
    }

    def formatBoolean = { Map attrs ->
        Object b = attrs.boolean
        if (b == null) return
        boolean v = b instanceof Boolean ? (Boolean) b : b.toString().toBoolean()
        String code = v ? 'boolean.true' : 'boolean.false'
        Object text = v ? attrs.true : attrs.false
        if (text == null) text = webRequest?.messages?.getMessage(code, null, v ? 'True' : 'False', webRequest.locale) ?: (v ? 'True' : 'False')
        out << Html.escape(text)
    }

    /** {@code <g:renderException exception="${exception}"/>}: mensagem e stack trace (para páginas de erro em desenvolvimento). */
    def renderException = { Map attrs ->
        Throwable e = (Throwable) attrs.exception
        if (e == null) return
        Throwable root = e
        while (root.cause != null && root.cause != root) root = root.cause
        def sw = new StringWriter()
        root.printStackTrace(new PrintWriter(sw))
        out << '<div class="error-details"><h2>' << Html.escape(root.getClass().name) << '</h2><p>' << Html.escape(root.message ?: '') <<
                '</p><pre class="stack">' << Html.escape(sw.toString()) << '</pre></div>'
    }

    def formatDate = { Map attrs ->
        Object date = attrs.date
        if (date == null) return
        String format = attrs.format?.toString() ?: 'yyyy-MM-dd HH:mm:ss'
        Locale locale = attrs.locale instanceof Locale ? (Locale) attrs.locale : (webRequest?.locale ?: Locale.default)
        // timeZone: TimeZone ou id ("UTC", "America/Sao_Paulo"), como no Grails
        // TimeZone.getTimeZone(String) é synchronized no JDK: cache por id
        TimeZone tz = attrs.timeZone instanceof TimeZone ? (TimeZone) attrs.timeZone :
                (attrs.timeZone ? timeZone(attrs.timeZone.toString()) : null)
        if (date instanceof TemporalAccessor) {
            def f = DateTimeFormatter.ofPattern(format, locale)
            out << Html.escape((tz ? f.withZone(tz.toZoneId()) : f).format((TemporalAccessor) date))
        } else {
            def f = new SimpleDateFormat(format, locale)
            if (tz) f.timeZone = tz
            out << Html.escape(f.format((Date) date))
        }
    }

    /** {@code <g:formatNumber number="${n}" type="currency|percent|number" format="#,##0.00"/>} */
    def formatNumber = { Map attrs ->
        Object number = attrs.number
        if (number == null) return
        Locale locale = attrs.locale instanceof Locale ? (Locale) attrs.locale : (webRequest?.locale ?: Locale.default)
        NumberFormat nf
        if (attrs.format) {
            nf = new DecimalFormat(attrs.format.toString(), java.text.DecimalFormatSymbols.getInstance(locale))
        } else {
            nf = switch (attrs.type?.toString()) {
                case 'currency' -> NumberFormat.getCurrencyInstance(locale)
                case 'percent' -> NumberFormat.getPercentInstance(locale)
                default -> NumberFormat.getNumberInstance(locale)
            }
            if (attrs.currencyCode) nf.currency = Currency.getInstance(attrs.currencyCode.toString())
        }
        if (attrs.maxFractionDigits != null) nf.maximumFractionDigits = attrs.maxFractionDigits as int
        if (attrs.minFractionDigits != null) nf.minimumFractionDigits = attrs.minFractionDigits as int
        out << Html.escape(nf.format(number instanceof Number ? number : new BigDecimal(number.toString())))
    }

    /** {@code <g:join in="${lista}" delimiter=", "/>} */
    def join = { Map attrs ->
        out << ((attrs.in ?: []) as Collection).collect { Html.escape(it) }.join(attrs.delimiter?.toString() ?: ', ')
    }
}
