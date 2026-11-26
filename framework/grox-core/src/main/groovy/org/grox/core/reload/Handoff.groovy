package org.grox.core.reload

import groovy.transform.CompileStatic
import org.grox.core.GroxRuntime

/**
 * O que uma geração da aplicação recebe do {@link DevReloader}: a entrada do Netty e, a partir da
 * segunda, a geração anterior, de onde reaproveita o servidor e as sessões e, na recarga leve, também
 * o Hibernate, a segurança e os dados do BootStrap.
 */
@CompileStatic
class Handoff {
    final SwitchableDispatcher entry
    /** Classloader da camada estável: o do Hibernate, que precisa sobreviver às recargas leves. */
    final ClassLoader dataClassLoader
    final GroxRuntime previous
    /** Recarga leve: mantém datastore, segurança e dados (o BootStrap não roda de novo). */
    final boolean keepData

    Handoff(SwitchableDispatcher entry, ClassLoader dataClassLoader, GroxRuntime previous, boolean keepData) {
        this.entry = entry
        this.dataClassLoader = dataClassLoader
        this.previous = previous
        this.keepData = keepData && previous != null
    }
}
