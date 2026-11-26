package org.grox.session

/**
 * Serialização Java dos atributos, resolvendo classes pelo class loader da aplicação.
 *
 * <p>A desserialização só aceita classes permitidas ({@link ObjectInputFilter}): JDK comum
 * ({@code java.lang}, {@code java.util}, {@code java.time}, {@code java.math}), o framework
 * ({@code org.grox}, {@code grails}, {@code org.grails}, {@code org.springframework} emulado),
 * {@code GString} e os pacotes da aplicação ({@code allowedPackages}). Assim, quem conseguir
 * gravar no Redis não executa código com um "gadget" de biblioteca (o próprio runtime do Groovy
 * tem um). Classe recusada faz o atributo ser ignorado, com aviso no log.</p>
 */
class SessionSerializer {

    static final List<String> ALLOWED_PREFIXES = ['java.lang.', 'java.util.', 'java.time.', 'java.math.',
                                                  'org.grox.', 'grails.', 'org.grails.', 'org.springframework.']
    static final List<String> DENIED_PREFIXES = ['java.lang.reflect.', 'java.lang.invoke.', 'java.util.logging.']
    static final Set<String> ALLOWED_CLASSES = ['groovy.lang.GString', 'org.codehaus.groovy.runtime.GStringImpl', 'java.net.URI'] as Set
    static final int MAX_DEPTH = 64
    static final int MAX_ARRAY = 1_000_000

    /** Trocado a cada recarga em desenvolvimento (a nova geração da app). */
    volatile ClassLoader loader
    /** Prefixos de pacote da aplicação (ex.: {@code br.com.empresa.}). */
    final List<String> allowedPackages

    SessionSerializer(ClassLoader loader, List<String> allowedPackages = []) {
        this.loader = loader
        this.allowedPackages = allowedPackages.collect { it.endsWith('.') ? it : it + '.' }
    }

    boolean isAllowed(String className) {
        if (className in ALLOWED_CLASSES) return true
        if (DENIED_PREFIXES.any { className.startsWith(it) }) return false
        ALLOWED_PREFIXES.any { className.startsWith(it) } || allowedPackages.any { className.startsWith(it) }
    }

    ObjectInputFilter.Status check(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > MAX_DEPTH || info.arrayLength() > MAX_ARRAY) return ObjectInputFilter.Status.REJECTED
        Class<?> c = info.serialClass()
        if (c == null) return ObjectInputFilter.Status.UNDECIDED
        while (c.array) c = c.componentType
        c.primitive || isAllowed(c.name) ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED
    }

    byte[] serialize(Object value) {
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(value) }
        bytes.toByteArray()
    }

    Object deserialize(byte[] data) {
        def input = new ObjectInputStream(new ByteArrayInputStream(data)) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                try {
                    Class.forName(desc.name, false, loader)
                } catch (ClassNotFoundException ignored) {
                    super.resolveClass(desc)
                }
            }
        }
        input.objectInputFilter = { ObjectInputFilter.FilterInfo info -> check(info) } as ObjectInputFilter
        input.withCloseable { it.readObject() }
    }
}
