package org.grox.view

import org.grox.http.Html
import org.grox.http.SafeHtml

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Extension module: {@code encodeAsHTML()}, {@code encodeAsURL()}, {@code encodeAsJSON()}...
 * <p>Como o {@code EncodingStateRegistry} do Grails, as strings devolvidas por {@code encodeAsHTML},
 * {@code encodeAsJavaScript} e {@code encodeAsURL} ficam registradas na requisição (por identidade), e o
 * {@code ${}} da GSP não as codifica de novo. A saída desses três nunca tem {@code < > " '} crus.
 */
class CodecExtensions {

    static String encodeAsHTML(Object self) {
        Html.markEncoded(Html.escape(self))
    }

    static String encodeAsURL(Object self) {
        Html.markEncoded(self == null ? '' : URLEncoder.encode(self.toString(), StandardCharsets.UTF_8))
    }

    /** Conversão do {@link #encodeAsJSON}; o módulo Grails troca pelo JSON dele (entidades, erros). */
    static volatile java.util.function.Function<Object, String> json = { Object v -> groovy.json.JsonOutput.toJson(v) } as java.util.function.Function<Object, String>

    static String encodeAsJSON(Object self) {
        json.apply(self)
    }

    /** Como o {@code JavaScriptCodec} do Grails: aspas, {@code &}, {@code <} e {@code >} como {@code \\uXXXX}. */
    static String encodeAsJavaScript(Object self) {
        if (self == null) return ''
        Html.markEncoded(self.toString()
                .replace('\\', '\\\\').replace('"', '\\u0022').replace('\'', '\\u0027').replace('&', '\\u0026')
                .replace('\n', '\\n').replace('\r', '\\r').replace('<', '\\u003C').replace('>', '\\u003E'))
    }

    static SafeHtml encodeAsRaw(Object self) {
        new SafeHtml(self?.toString())
    }

    // codecs de digest do Grails: texto em UTF-8, byte[] como está; resultado em hex minúsculo (ou bytes)

    static String encodeAsMD5(Object self) { digest(self, 'MD5')?.encodeHex()?.toString() }

    static byte[] encodeAsMD5Bytes(Object self) { digest(self, 'MD5') }

    static String encodeAsSHA1(Object self) { digest(self, 'SHA-1')?.encodeHex()?.toString() }

    static byte[] encodeAsSHA1Bytes(Object self) { digest(self, 'SHA-1') }

    static String encodeAsSHA256(Object self) { digest(self, 'SHA-256')?.encodeHex()?.toString() }

    static byte[] encodeAsSHA256Bytes(Object self) { digest(self, 'SHA-256') }

    static String encodeAsBase64(Object self) { self == null ? null : bytes(self).encodeBase64().toString() }

    static String encodeAsHex(Object self) { self == null ? null : bytes(self).encodeHex().toString() }

    private static byte[] digest(Object self, String algorithm) {
        self == null ? null : MessageDigest.getInstance(algorithm).digest(bytes(self))
    }

    private static byte[] bytes(Object self) {
        if (self instanceof byte[]) return (byte[]) self
        if (self instanceof Collection) return ((Collection) self).collect { it as byte } as byte[]
        self.toString().getBytes(StandardCharsets.UTF_8)
    }
}
