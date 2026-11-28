package org.grox.i18n

import java.text.MessageFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Mensagens de {@code messages[_idioma[_PAIS]].properties} (UTF-8) no classpath,
 * como em {@code grails-app/i18n}. Ordem: locale pedido, locale padrão, base; as mensagens
 * padrão do framework ({@code org/grox/i18n/messages*}) vêm por último.
 */
@groovy.transform.CompileStatic
class MessageSource {

    final ClassLoader loader
    final List<String> basenames
    final Locale defaultLocale
    private final Map<String, Properties> bundles = new ConcurrentHashMap<>()
    /** Caches (uma página faz dezenas de g:message): sufixos por locale, padrão por código e MessageFormat analisado. */
    private final Map<Locale, List<String>> suffixCache = new ConcurrentHashMap<>()
    private final Map<Locale, Map<String, String>> patternCache = new ConcurrentHashMap<>()
    private final Map<Locale, Map<String, MessageFormat>> formatCache = new ConcurrentHashMap<>()
    /** Sufixo ({@code _pt_BR}) → existe arquivo de mensagens com ele. */
    private final Map<String, Boolean> available = new ConcurrentHashMap<>()
    private static final String MISSING = new String('<ausente>')
    static final int MAX_CACHED = 20_000
    /** Sufixos inexistentes guardados no cache (o resto é consultado no classpath a cada vez). */
    static final int MAX_MISSING_SUFFIXES = 1_000

    MessageSource(ClassLoader loader, String basename, Locale defaultLocale) {
        this.loader = loader
        this.basenames = [basename, FRAMEWORK_BASENAME]
        this.defaultLocale = defaultLocale
    }

    static final String FRAMEWORK_BASENAME = 'org/grox/i18n/messages'

    /** Mensagem de um erro de validação (ou outra {@link MessageResolvable}): tenta cada código, depois a mensagem padrão. */
    String getMessage(MessageResolvable error, Locale locale) {
        Locale l = effective(locale)
        for (String code : error.codes) {
            String pattern = find(code, l)
            if (pattern != null) return format(pattern, error.arguments?.toList(), l)
        }
        error.defaultMessage ? format(error.defaultMessage, error.arguments?.toList(), l) : error.codes.last()
    }

    private String format(String pattern, List args, Locale l) {
        if (!args) return pattern
        Map<String, MessageFormat> byPattern = formatCache.computeIfAbsent(l, (Locale k) -> new ConcurrentHashMap<String, MessageFormat>())
        MessageFormat parsed = byPattern.get(pattern)
        if (parsed == null) {
            parsed = new MessageFormat(pattern, l)
            if (byPattern.size() < MAX_CACHED) byPattern.put(pattern, parsed)
        }
        // MessageFormat não é thread-safe: cada uso formata numa cópia (clone não refaz o parse)
        Object[] valores = new Object[args.size()]
        for (int i = 0; i < valores.length; i++) {
            Object v = args.get(i)
            valores[i] = v instanceof Range || v instanceof Collection ? v.toString() : v
        }
        ((MessageFormat) parsed.clone()).format(valores)
    }

    String getMessage(String code, List args = null, String defaultMessage = null, Locale locale = null) {
        String pattern = code ? find(code, locale ?: defaultLocale) : null
        if (pattern == null) pattern = defaultMessage
        if (pattern == null) return code
        format(pattern, args, effective(locale))
    }

    /**
     * O locale que de fato tem mensagens: o pedido ({@code pt_BR}), só o idioma ({@code pt}) ou o padrão. Os caches são
     * por ele, e não pelo locale pedido, que vem do cliente ({@code ?lang=}, {@code Accept-Language}): com locales
     * inventados, eles cresceriam sem limite.
     */
    Locale effective(Locale requested) {
        if (requested == null || requested == defaultLocale) return defaultLocale
        String lang = requested.language
        if (!lang) return defaultLocale
        if (requested.country && hasBundle("_${lang}_${requested.country}".toString())) return Locale.of(lang, requested.country)
        hasBundle("_${lang}".toString()) ? Locale.of(lang) : defaultLocale
    }

    private boolean hasBundle(String suffix) {
        Boolean cached = available.get(suffix)
        if (cached != null) return cached
        boolean exists = basenames.any { String b -> loader.getResource("${b}${suffix}.properties") != null }
        if (exists || available.size() < MAX_MISSING_SUFFIXES) available.put(suffix, exists)
        exists
    }

    String find(String code, Locale requested) {
        Locale locale = effective(requested)
        Map<String, String> byCode = patternCache.computeIfAbsent(locale, (Locale k) -> new ConcurrentHashMap<String, String>())
        String cached = byCode.get(code)
        if (cached != null) return cached.is(MISSING) ? null : cached
        String found = lookup(code, locale)
        if (byCode.size() < MAX_CACHED) byCode.put(code, found == null ? MISSING : found)
        found
    }

    private String lookup(String code, Locale locale) {
        for (String basename : basenames) {
            for (String suffix : suffixes(locale)) {
                String v = bundle(basename + suffix).getProperty(code)
                if (v != null) return v
            }
        }
        null
    }

    private List<String> suffixes(Locale locale) {
        suffixCache.computeIfAbsent(locale) { Locale l -> computeSuffixes(l) }
    }

    private List<String> computeSuffixes(Locale locale) {
        List<String> s = []
        [locale, defaultLocale].each { Locale l ->
            if (l?.country) s << "_${l.language}_${l.country}".toString()
            if (l?.language) s << "_${l.language}".toString()
        }
        s << ''
        s.unique()
    }

    private Properties bundle(String name) {
        bundles.computeIfAbsent(name) {
            // todos os arquivos do classpath (app e bibliotecas org.grox.lib), como os plugins do Grails:
            // carregados do último para o primeiro, para a aplicação (primeira no classpath) prevalecer
            def props = new Properties()
            Collections.list(loader.getResources("${name}.properties")).reverse().each { URL url ->
                url.withReader('UTF-8') { props.load(it) }
            }
            props
        }
    }
}
