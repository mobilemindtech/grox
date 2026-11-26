package org.grox.gradle

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.util.PatternSet
import org.gradle.process.CommandLineArgumentProvider

/**
 * Layout {@code grails-app/} comum aos plugins {@code org.grox.app} e {@code org.grox.lib}: fontes,
 * resources ({@code conf}, {@code i18n}, {@code views/} no classpath), compilação incremental e o
 * índice de pacotes ({@code META-INF/grox/packages}) que a aplicação usa para achar os artefatos.
 * O nome do diretório vem de {@code grox { appDir }} ({@code grox-app} se existir, senão {@code grails-app}).
 */
class GroxLayout {

    static final List<String> SOURCE_DIRS = ['controllers', 'services', 'domain', 'init', 'jobs', 'taglib', 'utils']
    static final String GRAILS_APP = 'grails-app'
    static final String GROX_APP = 'grox-app'

    static SourceSet apply(Project project, GroxLayoutExtension ext) {
        File projectDir = project.projectDir
        ext.appDir.convention(project.provider { new File(projectDir, GROX_APP).directory ? GROX_APP : GRAILS_APP })
        SourceSetContainer sourceSets = project.extensions.getByType(SourceSetContainer)
        SourceSet main = sourceSets.getByName('main')
        SOURCE_DIRS.each { String d -> main.groovy.srcDir(dir(project, ext, d)) }
        main.resources.srcDir(dir(project, ext, 'conf'))
        main.resources.srcDir(dir(project, ext, 'i18n'))

        // compilação incremental do Groovy: sem ela, cada mudança recompila a app inteira (a recarga espera por isso)
        def appDirArgs = new AppDirArgs(ext.appDir)
        project.tasks.withType(GroovyCompile).configureEach { GroovyCompile t ->
            t.options.incremental = true
            t.options.incrementalAfterFailure = true
            // a AST reconhece grails-app e grox-app; outro nome vai por -Dgrox.appDir ao compilador
            t.groovyOptions.forkOptions.jvmArgumentProviders.add(appDirArgs)
        }
        project.tasks.withType(Test).configureEach { Test t -> t.jvmArgumentProviders.add(appDirArgs) }
        project.tasks.named('processResources').configure { task ->
            task.from(dir(project, ext, 'views')) { it.into('views') }
            // templates de scaffold no lugar do Grails (src/main/templates/scaffolding/*.gsp)
            task.from(project.layout.projectDirectory.dir('src/main/templates')) { it.into('templates') }
            task.duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }

        def indexOutput = project.layout.buildDirectory.dir('grox-index')
        def index = project.tasks.register('groxPackageIndex', PackageIndexTask) { PackageIndexTask t ->
            t.description = 'Lista os pacotes dos fontes do layout (grails-app/) em META-INF/grox/packages'
            t.sources.from(SOURCE_DIRS.collect { String d -> tree(project, ext, d, '**/*.groovy', '**/*.java') })
            t.outputDir.set(indexOutput)
        }
        main.output.dir([builtBy: index], indexOutput)
        // raízes externas e patches (build paralelo dentro do repositório de uma app Grails)
        GroxSources.apply(project, ext, main)
        main
    }

    /** Subdiretório do layout ({@code grails-app/views}), resolvido só quando usado. */
    static Provider<Directory> dir(Project project, GroxLayoutExtension ext, String sub) {
        project.layout.projectDirectory.dir(ext.appDir.map { String a -> "${a}/${sub}".toString() })
    }

    /** Arquivos de um subdiretório do layout (vazio se ele não existir). */
    static FileTree tree(Project project, GroxLayoutExtension ext, String sub, String... includes) {
        FileTree t = project.files(dir(project, ext, sub)).asFileTree
        includes ? t.matching(new PatternSet().include(includes)) : t
    }

    /** {@code -Dgrox.appDir}, só quando o nome não é um dos reconhecidos sem configuração. */
    static class AppDirArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<String> appDir

        AppDirArgs(Provider<String> appDir) { this.appDir = appDir }

        @Override
        Iterable<String> asArguments() {
            String a = appDir.get()
            a in [GRAILS_APP, GROX_APP] ? [] : ["-Dgrox.appDir=${a}".toString()]
        }
    }
}
