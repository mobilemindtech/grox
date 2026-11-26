package org.grox

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.grox.web.scaffolding.FieldsTagLib
import org.grox.web.scaffolding.ScaffoldViews
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path

/** Scaffold dinâmico: a AST de {@code static scaffold = X} e a expansão dos templates (o CRUD está no SampleAppTest). */
class ScaffoldTest {

    @TempDir
    Path tmp

    /** Compila como o build da app: caminhos em grails-app/ ativam a ArtefactTransformation. */
    GroovyClassLoader compile(Map<String, String> sources) {
        Path target = tmp.resolve('classes')
        def config = new CompilerConfiguration(targetDirectory: target.toFile())
        def loader = new GroovyClassLoader(getClass().classLoader, config)
        def unit = new CompilationUnit(config, null, loader)
        sources.each { String rel, String code ->
            Path file = tmp.resolve('src').resolve(rel)
            Files.createDirectories(file.parent)
            file.toFile().text = code
            unit.addSource(file.toFile())
        }
        unit.compile()
        loader.addClasspath(target.toString())
        loader
    }

    @Test
    void 'scaffold com a domain vira RestfulController da domain'() {
        def loader = compile([
                'grails-app/domain/app/Livro.groovy'               : 'package app\nclass Livro { String titulo }',
                'grails-app/controllers/app/LivroController.groovy': 'package app\nclass LivroController { static scaffold = Livro }'])
        Class<?> c = loader.loadClass('app.LivroController')
        assert c.superclass == grails.rest.RestfulController
        assert c.getDeclaredConstructor().newInstance().resource.name == 'app.Livro'
        assert ScaffoldViews.domainOf(c).name == 'app.Livro'
    }

    @Test
    void 'scaffold true do Grails 2 e um erro de compilacao com a orientacao'() {
        def e = org.junit.jupiter.api.Assertions.assertThrows(MultipleCompilationErrorsException) {
            compile(['grails-app/controllers/app/XController.groovy': 'package app\nclass XController { static scaffold = true }'])
        }
        assert e.message.contains('use static scaffold = <DomainClass>')
    }

    @Test
    void 'templates: variaveis da domain e expressoes da GSP'() {
        String t = ScaffoldViews.expand('<f:table collection="\\${${propertyName}List}"/> ${className} ${outra}',
                [propertyName: 'livro', className: 'Livro'])
        assert t == '<f:table collection="${livroList}"/> Livro ${outra}'
        String index = ScaffoldViews.source('index', ScaffoldTest, getClass().classLoader)
        assert index.contains('collection="${scaffoldTestList}"') && index.contains('domainClass="${org.grox.ScaffoldTest}"')
    }

    @Test
    void 'rotulos pelo nome natural da propriedade'() {
        assert FieldsTagLib.naturalName('dataNascimento') == 'Data Nascimento'
        assert FieldsTagLib.naturalName('cpf') == 'Cpf'
    }
}
