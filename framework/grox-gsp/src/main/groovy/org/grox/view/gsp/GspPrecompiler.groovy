package org.grox.view.gsp

import groovy.json.JsonOutput
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grox.core.ClassUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Pré-compila as GSPs no build (tarefa {@code compileGsp} do plugin {@code org.grox.app}):
 * gera as classes e o índice {@value #INDEX} (textos, contentType e código gerado para
 * mapear erros). Qualquer erro de GSP falha o build com arquivo e linha.
 *
 * Argumentos: diretório das views, diretório de saída, diretórios das classes da aplicação
 * (para achar os namespaces das TagLibs).
 */
class GspPrecompiler {

    static final String INDEX = 'META-INF/grox/gsp-index.json'

    static void main(String[] args) {
        if (args.length < 2) {
            System.err.println('uso: GspPrecompiler <viewsDir> <outputDir> [classesDir...]')
            System.exit(2)
        }
        List<String> errors = run(new File(args[0]), new File(args[1]), args.drop(2).collect { new File(it) })
        if (errors) {
            errors.each { System.err.println("erro GSP: ${it}") }
            System.exit(1)
        }
    }

    /** @return erros (vazio se tudo compilou) */
    static List<String> run(File viewsDir, File outputDir, List<File> classesDirs) {
        Set<String> namespaces = namespaces(classesDirs)
        Map<String, Map> index = [:]
        Map<String, String> classNames = [:]
        List<String> errors = []
        outputDir.mkdirs()
        if (!viewsDir.directory) return errors
        Files.walk(viewsDir.toPath()).withCloseable { stream ->
            stream.filter { Path p -> p.toString().endsWith('.gsp') }.sorted().forEach { Path file ->
                String rel = viewsDir.toPath().relativize(file).toString().replace(File.separator, '/')
                String path = '/' + rel.substring(0, rel.length() - 4)
                String view = path + '.gsp'
                String className = GspTemplateEngine.className(path)
                if (classNames.containsKey(className)) {
                    errors << "${view}: nome de classe ${className} colide com ${classNames[className]}".toString()
                    return
                }
                classNames[className] = view
                try {
                    def result = GspCompiler.compile(GspParser.parse(file.toFile().getText('UTF-8'), namespaces, view), view)
                    compileClass(className, result.source, outputDir)
                    index[path] = [className: className, texts: result.texts, contentType: result.contentType, source: result.source]
                } catch (GspException e) {
                    errors << e.message
                } catch (MultipleCompilationErrorsException e) {
                    def m = e.message =~ /@ line (\d+)/
                    int line = 0
                    if (m.find()) {
                        try {
                            def result = GspCompiler.compile(GspParser.parse(file.toFile().getText('UTF-8'), namespaces, view), view)
                            line = GspTemplateEngine.gspLineOf(result.source, m.group(1) as int)
                        } catch (Exception ignored) {
                        }
                    }
                    errors << "${view}:${line}: erro de compilação: ${GspTemplateEngine.compileMessage(e)}".toString()
                }
            }
        }
        File indexFile = new File(outputDir, INDEX)
        indexFile.parentFile.mkdirs()
        indexFile.setText(JsonOutput.toJson(index), 'UTF-8')
        errors
    }

    /** Os módulos do classpath, só para saber as TagLibs e os imports que contribuem (sem subir a aplicação). */
    static final List<org.grox.core.GroxModule> MODULES = org.grox.core.GroxRuntime.loadModules(GspPrecompiler.classLoader)

    private static void compileClass(String className, String source, File outputDir) {
        GspTemplateEngine.imports = org.grox.view.GspModule.providedImports(MODULES)
        def config = GspTemplateEngine.compilerConfiguration()
        config.targetDirectory = outputDir
        def unit = new CompilationUnit(config, null, new GroovyClassLoader(GspPrecompiler.classLoader, config))
        unit.addSource(className + '.groovy', source)
        unit.compile()
    }

    /** Namespaces das TagLibs nativas e das classes *TagLib da aplicação ({@code static namespace}). */
    static Set<String> namespaces(List<File> classesDirs) {
        List<Class<?>> builtIn = org.grox.view.GspModule.BUILT_IN + org.grox.view.GspModule.providedTagLibs(MODULES)
        Set<String> ns = builtIn.collect {
            (ClassUtils.staticProperty(it, 'namespace') ?: 'g').toString()
        } as Set
        ClassLoader loader = GspPrecompiler.classLoader
        classesDirs.findAll { it.directory }.each { File dir ->
            Files.walk(dir.toPath()).withCloseable { stream ->
                stream.filter { Path p -> p.fileName.toString() ==~ /[^$]+TagLib\.class/ }.forEach { Path p ->
                    String name = dir.toPath().relativize(p).toString().replace(File.separator, '.').replaceAll(/\.class$/, '')
                    Class c = Class.forName(name, false, loader)
                    ns << (ClassUtils.staticProperty(c, 'namespace') ?: 'g').toString()
                }
            }
        }
        ns
    }
}
