package org.grox.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Recarga com bibliotecas {@code org.grox.lib} do mesmo build (o app-c-core do app-c): os diretórios delas entram
 * em {@code grox.reload.classes}/{@code resources}, no classpath do {@code run} no lugar dos jars e nas dependências
 * do {@code groxReloadStamp} que o Gradle contínuo roda. Projetos sem o plugin ficam de fora.
 */
class ReloadLibsTest {

    @TempDir
    File dir

    @Test
    void 'bibliotecas grox diretas e transitivas entram na recarga e no classpath do run'() {
        file('settings.gradle', "rootProject.name = 'multi'\ninclude 'app', 'core', 'base', 'comum'")
        // app -> core (grox) -> base (grox) e comum (java comum, como o framework no build do Grox)
        file('app/build.gradle', """
plugins { id 'org.grox.app' }
dependencies {
    implementation localGroovy()
    implementation project(':core')
}
application { mainClass = 'demo.Application' }
def run = tasks.named('run', JavaExec)
tasks.register('mostra') {
    doLast {
        println 'ARGS ' + run.get().allJvmArgs.join(' ')
        println 'CP ' + run.get().classpath.files*.path.join(File.pathSeparator)
    }
}
""")
        file('core/build.gradle', """
plugins { id 'org.grox.lib' }
dependencies {
    implementation localGroovy()
    api project(':base')
    api project(':comum')
}
""")
        file('base/build.gradle', "plugins { id 'org.grox.lib' }\ndependencies { implementation localGroovy() }")
        file('comum/build.gradle', "plugins { id 'java-library' }")
        file('gradlew', '#!/bin/sh')

        def result = runner('mostra').build()
        String args = result.output.readLines().find { it.startsWith('ARGS ') }
        String classes = (args =~ /-Dgrox\.reload\.classes=(\S+)/)[0][1]
        String resources = (args =~ /-Dgrox\.reload\.resources=(\S+)/)[0][1]
        ['app', 'core', 'base'].each { String p ->
            assert classes.contains(path("${p}/build/classes/groovy/main"))
            assert resources.contains(path("${p}/build/resources/main"))
        }
        assert !classes.contains(path('comum/'))
        assert !args.contains('grox.reload.sources')   // tudo dentro da raiz: o Gradle contínuo basta

        List<String> cp = result.output.readLines().find { it.startsWith('CP ') }.substring(3).split(File.pathSeparator).toList()
        assert cp.contains(path('core/build/classes/groovy/main'))
        assert cp.contains(path('base/build/resources/main'))
        // sem os jars das bibliotecas, que o Gradle contínuo reescreve com a JVM rodando; os de outros projetos ficam
        assert !cp.any { it.endsWith('core.jar') || it.endsWith('base.jar') }
        assert cp.any { it.endsWith('comum.jar') }

        // o Gradle contínuo compila também as bibliotecas, com os resources delas
        def dry = runner(':app:groxReloadStamp', '--dry-run').build().output
        [':core:compileGroovy', ':core:processResources', ':base:compileGroovy', ':base:processResources'].each {
            assert dry.contains(it)
        }
    }

    @Test
    void 'raiz externa numa biblioteca liga o Gradle sob demanda com os fontes de todos'() {
        // build paralelo: o build fica em b/, e o core compila os fontes de fora/ (fora da raiz do build)
        file('fora/grails-app/services/demo/XService.groovy', 'package demo\nclass XService {}')
        file('b/settings.gradle', "rootProject.name = 'multi'\ninclude 'app', 'core'")
        file('b/gradlew', '#!/bin/sh')
        file('b/app/build.gradle', """
plugins { id 'org.grox.app' }
dependencies {
    implementation localGroovy()
    implementation project(':core')
}
application { mainClass = 'demo.Application' }
def run = tasks.named('run', JavaExec)
tasks.register('mostra') { doLast { println 'ARGS ' + run.get().allJvmArgs.join(' ') } }
""")
        file('b/app/grails-app/controllers/demo/AController.groovy', 'package demo\nclass AController {}')
        file('b/core/build.gradle', "plugins { id 'org.grox.lib' }\ndependencies { implementation localGroovy() }\ngrox { source('../../fora') }")

        def result = GradleRunner.create().withProjectDir(new File(dir, 'b')).withPluginClasspath()
                .withArguments('mostra', '--stacktrace', '--no-configuration-cache').build()
        String args = result.output.readLines().find { it.startsWith('ARGS ') }
        List<String> sources = (args =~ /-Dgrox\.reload\.sources=(\S+)/)[0][1].split(File.pathSeparator).toList()
        assert sources.contains(path('fora/grails-app/services'))
        assert sources.contains(path('b/app/grails-app/controllers'))   // os de dentro também: o contínuo não roda
    }

    private String path(String rel) {
        new File(dir, rel).canonicalFile.path
    }

    private GradleRunner runner(String... tasks) {
        // sem configuration cache: a tarefa de teste lê o run em tempo de execução
        GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments((tasks as List) + ['--stacktrace', '--no-configuration-cache'])
    }

    private File file(String path, String text) {
        File f = new File(dir, path)
        f.parentFile.mkdirs()
        f.text = text
        f
    }
}
