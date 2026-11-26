package org.grox.http

/** Texto já seguro para HTML: não é codificado de novo ao ser impresso com {@code ${}}. */
final class SafeHtml implements CharSequence {

    final String value

    SafeHtml(CharSequence value) {
        this.value = value?.toString() ?: ''
    }

    @Override
    int length() { value.length() }

    @Override
    char charAt(int index) { value.charAt(index) }

    @Override
    CharSequence subSequence(int start, int end) { value.subSequence(start, end) }

    @Override
    String toString() { value }

    /** Como o StreamCharBuffer do Grails (retorno de tag chamada como método): métodos de String ({@code toLowerCase()}...). */
    def methodMissing(String name, Object args) {
        value.invokeMethod(name, args)
    }

    @Override
    boolean equals(Object o) { o instanceof CharSequence && o.toString() == value }

    @Override
    int hashCode() { value.hashCode() }
}
