package grails.util

/** Compatibilidade com o {@code grails.util.Holder}: um valor global com nome (sem separação por classloader). */
class Holder<T> {
    private volatile T value
    final String name

    Holder(String name) { this.name = name }

    T get() { value }

    T get(boolean mappedOnly) { value }

    void set(T t) { value = t }
}
