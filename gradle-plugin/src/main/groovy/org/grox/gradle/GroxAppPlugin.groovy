package org.grox.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.api.tasks.SourceSet

/**
 * Plugin {@code org.grox.app}: aplica groovy + application e configura o layout do Grails.
 * <ul>
 *   <li>fontes: grails-app/{controllers,services,domain,init,jobs,taglib,utils} e src/main/groovy; o diretório
 *       pode ser {@code grox-app} (detectado) ou outro ({@code grox { appDir = '...' }})</li>
 *   <li>resources: grails-app/conf e grails-app/i18n; views em {@code views/} no classpath</li>
 *   <li>índice de pacotes dos artefatos ({@code META-INF/grox/packages}, ver {@link GroxLayout})</li>
 *   <li>{@code run} a partir do diretório do projeto (reload de views em desenvolvimento)</li>
 *   <li>{@code compileGsp}: pré-compila as views (incluídas no classpath e no jar)</li>
 *   <li>{@code assetCompile}: processa grails-app/assets (require, digest, manifest)</li>
 *   <li>{@code run} com JIT só C1 ({@code grox.fastDevStartup}), para subir mais rápido</li>
 *   <li>{@code run} com recarga de classes ({@code grox.reload}): diretórios de saída e o Gradle contínuo
 *       que os atualiza, passados à app por propriedades {@code grox.reload.*}</li>
 *   <li>raízes de código externas e patches ({@code grox { source; patches }}, ver {@link GroxSources}): o build
 *       paralelo dentro do repositório de uma app Grails</li>
 *   <li>{@code aotCache}: execução de treino sobre o {@code installDist} que gera o AOT cache do JDK
 *       ({@code lib/grox.aot}); o script de start o usa quando existe</li>
 * </ul>
 * A dependência do framework continua no build da aplicação.
 */
class GroxAppPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.pluginManager.apply('groovy')
        project.pluginManager.apply('application')
        GroxExtension ext = project.extensions.create('grox', GroxExtension)
        ext.precompileGsp.convention(true)
        ext.fastDevStartup.convention(true)
        ext.reload.convention(true)

        SourceSet main = GroxLayout.apply(project, ext)
        def fastStartup = ext.fastDevStartup
        String prefix = project.path == ':' ? '' : project.path
        File gradlew = new File(project.rootDir, 'gradlew')
        // carimbo gravado ao fim de cada compilação bem-sucedida do Gradle contínuo: o sinal para recarregar
        def stamp = project.layout.buildDirectory.file('grox-reload/stamp')
        project.tasks.register('groxReloadStamp') { t ->
            t.description = 'Compila as classes e grava o carimbo que dispara a recarga (usada pelo run em development)'
            t.dependsOn('compileGroovy', 'processResources')
            t.outputs.file(stamp)
            t.outputs.upToDateWhen { false }
            t.doLast(new WriteStamp(stamp))
        }
        // bibliotecas org.grox.lib do mesmo build (diretas ou transitivas): também recarregam, e o run usa os
        // diretórios de saída delas no lugar dos jars (que o Gradle contínuo reescreve; ver ReloadLibs)
        def libs = ReloadLibs.register(project)
        project.tasks.named('groxReloadStamp').configure { it.dependsOn(libs.outputs) }
        def reloadArgs = new ReloadArgs(ext.reload, project.files(main.output.classesDirs, libs.classes),
                project.files(main.output.resourcesDir, libs.resources),
                gradlew.file ? gradlew : null, "${prefix}:groxReloadStamp".toString(), stamp,
                project.files(main.allSource.sourceDirectories, libs.sources), project.rootDir)
        project.tasks.named('run', JavaExec).configure { JavaExec t ->
            t.classpath = project.files(main.output, libs.outputs, libs.runtimeWithoutLibs(project))
            t.workingDir = project.projectDir
            // sem GRAILS_ENV o padrão do framework é production (artefato); o run é development, como o bootRun
            t.systemProperty('grox.env.default', 'development')
            t.jvmArgumentProviders.add(new FastStartupArgs(fastStartup))
            t.jvmArgumentProviders.add(reloadArgs)
            t.jvmArgumentProviders.add(new GroxLayout.AppDirArgs(ext.appDir))
        }
        // testes sem GRAILS_ENV rodam em test, como no Grails
        project.tasks.withType(Test).configureEach { Test t -> t.systemProperty('grox.env.default', 'test') }

        // AOT cache (JDK 25+): o script de start usa lib/grox.aot quando existe (e não durante o treino)
        project.tasks.named('startScripts', CreateStartScripts).configure { CreateStartScripts t ->
            t.doLast {
                File unix = t.unixScript
                String marker = unix.readLines().find { it.startsWith('DEFAULT_JVM_OPTS=') }
                if (marker) {
                    unix.text = unix.text.replace(marker, marker + '''

# Grox: AOT cache gerado por "gradle aotCache" (classes já carregadas e linkadas: subida mais rápida)
if [ -f "$APP_HOME/lib/grox.aot" ] && [ -z "$GROX_AOT_TRAINING" ]; then
    DEFAULT_JVM_OPTS="$DEFAULT_JVM_OPTS \\"-XX:AOTCache=$APP_HOME/lib/grox.aot\\""
fi''')
                }
            }
        }
        def installDir = project.tasks.named('installDist', Sync).map { it.destinationDir }
        String appName = project.name
        project.tasks.register('aotCache', Exec) { Exec t ->
            t.group = 'distribution'
            t.description = 'Gera o AOT cache (lib/grox.aot) com uma execução de treino da aplicação instalada'
            t.dependsOn('installDist')
            // a app sobe (com a configuração do ambiente atual, inclusive banco) e encerra ao ficar pronta
            t.argumentProviders.add(new AotArgs(installDir, appName))
            t.executable('sh')
            t.environment('GROX_AOT_TRAINING', '1')
            t.doFirst(new AotEnv(installDir))
        }

        def gspOutput = project.layout.buildDirectory.dir('gsp-classes')
        // com raízes externas (grox { source }), as views vêm de várias raízes: compila as já reunidas pelo processResources
        Provider<Directory> projectViews = GroxLayout.dir(project, ext, 'views')
        File mergedViews = new File(main.output.resourcesDir, 'views')
        Provider<Directory> viewsDir = project.layout.dir(project.provider {
            GroxSources.external(ext) ? mergedViews : projectViews.get().asFile })
        def compileGsp = project.tasks.register('compileGsp', JavaExec) { JavaExec t ->
            t.group = 'build'
            t.description = 'Pré-compila as GSPs de grails-app/views (ou do appDir)'
            t.onlyIf { ext.precompileGsp.get() && viewsDir.get().asFile.directory }
            // não 'classes': a saída de compileGsp faz parte de classes (main.output.dir)
            t.dependsOn('compileGroovy', 'processResources')
            // fileTree: apps sem views (jobs, API) não têm o diretório
            t.inputs.files(project.provider { GroxSources.external(ext) ? project.fileTree(mergedViews) : GroxLayout.tree(project, ext, 'views') })
                    .withPropertyName('views')
            t.inputs.files(main.output.classesDirs).withPropertyName('classes')
            t.outputs.dir(gspOutput)
            t.mainClass.set('org.grox.view.gsp.GspPrecompiler')
            // o classpath não inclui a própria saída (evita dependência circular)
            t.classpath = project.files(main.output.classesDirs, main.output.resourcesDir) + project.configurations.getByName('runtimeClasspath')
            // compatível com o configuration cache: nada de Project/SourceSet capturados
            t.argumentProviders.add(new GspArgs(viewsDir, gspOutput, main.output.classesDirs))
        }
        main.output.dir([builtBy: compileGsp], gspOutput)

        def assetOutput = project.layout.buildDirectory.dir('assets')
        // assets não se mesclam: vêm do projeto ou da última raiz externa que os tem
        Provider<Directory> projectAssets = GroxLayout.dir(project, ext, 'assets')
        Provider<Directory> assetsDir = project.layout.dir(project.provider {
            File own = projectAssets.get().asFile
            if (own.directory || !ext.sources) return own
            ext.sources.collect { new File(project.file(it.dir), "${ext.appDir.get()}/assets") }.reverse().find { it.directory } ?: own
        })
        def assetCompile = project.tasks.register('assetCompile', JavaExec) { JavaExec t ->
            t.group = 'build'
            t.description = 'Compila grails-app/assets (ou do appDir): diretivas require, digest e manifest'
            t.onlyIf { assetsDir.get().asFile.directory }
            t.dependsOn('compileGroovy', 'processResources')
            t.inputs.files(project.provider { project.fileTree(assetsDir) }).withPropertyName('assets')
            t.outputs.dir(assetOutput)
            t.mainClass.set('org.grox.assets.AssetCompiler')
            t.classpath = project.files(main.output.classesDirs, main.output.resourcesDir) + project.configurations.getByName('runtimeClasspath')
            t.argumentProviders.add(new AssetArgs(assetsDir, assetOutput))
        }
        main.output.dir([builtBy: assetCompile], assetOutput)
        // em development os assets são servidos do diretório (relativo ao run); de uma raiz externa, pelo caminho
        def devAssets = project.provider { GroxSources.external(ext) ? assetsDir.get().asFile.absolutePath : '' }
        project.tasks.named('run', JavaExec).configure { JavaExec t -> t.jvmArgumentProviders.add(new DevAssetsArgs(devAssets)) }
    }

    static class DevAssetsArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<String> dir

        DevAssetsArgs(Provider<String> dir) { this.dir = dir }

        @Override
        Iterable<String> asArguments() {
            dir.get() ? ["-Dgrox.assets.dir=${dir.get()}".toString()] : []
        }
    }

    static class FastStartupArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<Boolean> enabled

        FastStartupArgs(Provider<Boolean> enabled) { this.enabled = enabled }

        @Override
        Iterable<String> asArguments() {
            enabled.get() ? ['-XX:TieredStopAtLevel=1'] : []
        }
    }

    /** Propriedades lidas pelo {@code DevReloader} do framework (só age em development). */
    static class ReloadArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<Boolean> enabled
        private final FileCollection classes
        private final FileCollection resources
        private final File gradlew
        private final String tasks
        private final Provider<RegularFile> stamp
        private final FileCollection sources
        private final File rootDir

        ReloadArgs(Provider<Boolean> enabled, FileCollection classes, FileCollection resources, File gradlew, String tasks,
                   Provider<RegularFile> stamp, FileCollection sources, File rootDir) {
            this.enabled = enabled
            this.classes = classes
            this.resources = resources
            this.gradlew = gradlew
            this.tasks = tasks
            this.stamp = stamp
            this.sources = sources
            this.rootDir = rootDir
        }

        @Override
        Iterable<String> asArguments() {
            if (!enabled.get()) return []
            List<String> args = ["-Dgrox.reload.classes=${classes.files*.absolutePath.join(File.pathSeparator)}".toString(),
                                 "-Dgrox.reload.resources=${resources.files*.absolutePath.join(File.pathSeparator)}".toString()]
            if (gradlew) args += ["-Dgrox.reload.gradlew=${gradlew.absolutePath}".toString(), "-Dgrox.reload.tasks=${tasks}".toString(),
                                  "-Dgrox.reload.stamp=${stamp.get().asFile.absolutePath}".toString()]
            // o Gradle contínuo não vê mudanças fora da raiz do build (raízes externas do build paralelo): nesse caso a
            // app observa os fontes e roda o Gradle a cada mudança
            List<File> dirs = sources.files.findAll { it.directory }.toList()
            String root = rootDir.canonicalPath + File.separator
            if (gradlew && dirs.any { !(it.canonicalPath + File.separator).startsWith(root) }) {
                args << "-Dgrox.reload.sources=${dirs*.absolutePath.join(File.pathSeparator)}".toString()
            }
            args
        }
    }

    static class WriteStamp implements org.gradle.api.Action<org.gradle.api.Task>, Serializable {
        private final Provider<RegularFile> stamp

        WriteStamp(Provider<RegularFile> stamp) { this.stamp = stamp }

        @Override
        void execute(org.gradle.api.Task task) {
            File f = stamp.get().asFile
            f.parentFile.mkdirs()
            f.text = System.currentTimeMillis().toString()
        }
    }

    /** Ambiente do treino: grava lib/grox.aot, porta aleatória e encerra ao ficar pronta. */
    static class AotEnv implements org.gradle.api.Action<org.gradle.api.Task>, Serializable {
        private final Provider<File> home

        AotEnv(Provider<File> home) { this.home = home }

        @Override
        void execute(org.gradle.api.Task task) {
            File cache = new File(home.get(), 'lib/grox.aot')
            cache.delete()
            String opts = (System.getenv('JAVA_OPTS') ?: '') + " -XX:AOTCacheOutput=${cache.absolutePath} -Dgrox.aot.training=true -Dserver.port=0"
            ((Exec) task).environment('JAVA_OPTS', opts.trim())
        }
    }

    static class AotArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<File> home
        private final String appName

        AotArgs(Provider<File> home, String appName) {
            this.home = home
            this.appName = appName
        }

        @Override
        Iterable<String> asArguments() {
            [new File(home.get(), "bin/${appName}").absolutePath]
        }
    }

    static class AssetArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<Directory> assets
        private final Provider<Directory> output

        AssetArgs(Provider<Directory> assets, Provider<Directory> output) {
            this.assets = assets
            this.output = output
        }

        @Override
        Iterable<String> asArguments() {
            [assets.get().asFile.absolutePath, output.get().asFile.absolutePath]
        }
    }

    static class GspArgs implements CommandLineArgumentProvider, Serializable {
        private final Provider<Directory> views
        private final Provider<Directory> output
        private final FileCollection classes

        GspArgs(Provider<Directory> views, Provider<Directory> output, FileCollection classes) {
            this.views = views
            this.output = output
            this.classes = classes
        }

        @Override
        Iterable<String> asArguments() {
            [views.get().asFile.absolutePath, output.get().asFile.absolutePath] + classes.files*.absolutePath
        }
    }
}
