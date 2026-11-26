package org.grox.core.reload

import groovy.transform.CompileStatic

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Classloader de uma camada da aplicação: define ele mesmo (child-first) as classes que possui, a partir
 * do bytecode do retrato ({@link AppClasses#bytecode}), e delega o resto ao pai. As classes da app também
 * estão no classpath do sistema (o {@code gradle run} as põe lá), então a camada estável nunca delega uma
 * classe da app ao sistema: isso criaria uma segunda cópia. Recursos (yml, properties, views) seguem a
 * delegação normal, porque são os mesmos arquivos.
 */
@CompileStatic
class AppClassLoader extends URLClassLoader {

    static {
        registerAsParallelCapable()
    }

    /** Ainda não definidas (cada entrada sai ao ser definida, liberando a memória). */
    private final Map<String, byte[]> pending
    private final Set<String> owned
    private final Set<String> appClasses
    private final boolean base

    /**
     * @param owned classes de nível superior desta camada
     * @param base camada estável (pai é o sistema): classe da app que não é dela vira ClassNotFoundException
     */
    AppClassLoader(String name, List<Path> dirs, AppClasses snapshot, Set<String> owned, boolean base, ClassLoader parent) {
        super(name, dirs.collect { it.toUri().toURL() } as URL[], parent)
        this.pending = new ConcurrentHashMap<>(snapshot.bytecodeOf(owned))
        this.owned = owned
        this.appClasses = snapshot.checksums.keySet()
        this.base = base
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!owned.contains(AppClasses.topLevel(name))) {
            if (base && (appClasses.contains(name) || findResource(name.replace('.', '/') + '.class') != null)) {
                throw new ClassNotFoundException("${name} não pertence à camada estável (domain, src/main/groovy): é um artefato " +
                        'recarregável carregado por nome a partir dela, ou uma classe nova ainda não recarregada')
            }
            return super.loadClass(name, resolve)
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name) ?: findClass(name)
            if (resolve) resolveClass(c)
            c
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = pending.remove(name)
        if (bytes == null) throw new ClassNotFoundException(name)
        defineClass(name, bytes, 0, bytes.length)
    }
}
