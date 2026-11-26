package org.grox

import org.grox.core.GroxRuntime
import org.grox.core.reload.DevReloader

/** Ponto de entrada, equivalente ao {@code GrailsApp.run(Application, args)}. */
class GroxApp {

    static GroxRuntime run(Class<?> applicationClass, String... args) {
        // recarga em desenvolvimento: a app sobe em classloaders próprios (a classe recebida é só o ponto de partida)
        if (DevReloader.requested(applicationClass)) return DevReloader.create(applicationClass, args).start()
        GroxRuntime runtime = new GroxRuntime(applicationClass, args).start()
        if (Boolean.getBoolean('grox.aot.training')) {
            // treino do AOT cache (tarefa aotCache): a subida já carregou as classes; ao sair, a JVM grava o cache
            org.slf4j.LoggerFactory.getLogger(GroxApp).info('Treino do AOT cache concluído; encerrando')
            runtime.close()
            System.exit(0)
        }
        runtime
    }
}
