package org.grox

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

/**
 * Autocomplete no IDE: os descritores GDSL do IntelliJ (rodados aqui com um {@code psiClass} simulado, já que o
 * IDEA não roda no build) e a {@code GormEntity<D>} genérica, conferida por código {@code @CompileStatic}.
 */
class AutocompleteTest {

    @TempDir
    Path tmp

    /** O que um script GDSL registra para uma classe: métodos, propriedades e classes delegadas. */
    static class Contribution {
        List<Map> methods = []
        List<Map> properties = []
        List<String> delegates = []

        List<String> names(boolean statics) { methods.findAll { (it.isStatic ?: false) == statics }*.name.unique() }

        Map method(String name, int params = -1) { methods.find { it.name == name && (params < 0 || it.params.size() == params) } }
    }

    /** Roda o script com {@code context}/{@code contributor} simulados e aplica os contribuidores a {@code psiClass}. */
    static Contribution contribute(String resource, Map psiClass) {
        List<Closure> contributors = []
        def binding = new Binding()
        binding.setVariable('context', { Map args -> args })
        binding.setVariable('contributor', { Object ctx, Closure body -> contributors << body })
        String text = AutocompleteTest.classLoader.getResource(resource).getText('UTF-8')
        new GroovyShell(binding).evaluate(text)
        def result = new Contribution()
        def delegate = new Object() {
            Object getPsiClass() { psiClass }
            void method(Map m) { result.methods << m }
            void property(Map p) { result.properties << p }
            Object findClass(String name) { [qualifiedName: name] }
            void delegatesTo(Object c) { result.delegates << c.qualifiedName }
        }
        contributors.each { Closure c ->
            Closure body = (Closure) c.clone()
            body.delegate = delegate
            body.resolveStrategy = Closure.DELEGATE_FIRST
            body.call()
        }
        result
    }

    /** Uma classe como o IDEA a vê (PsiClass): só o que os descritores consultam. */
    static Map psiClass(String qualifiedName, String path, Map extra = [:]) {
        [name                : qualifiedName.tokenize('.').last(), qualifiedName: qualifiedName,
         containingFile      : [originalFile: [virtualFile: [path: path]]],
         modifierList        : [findAnnotation: { String a -> extra.annotations?.contains(a) ? [:] : null }],
         interfaces          : [], implementsListTypes: (extra.implementsList ?: []).collect { [canonicalText: it] },
         fields              : (extra.fields ?: [:]).collect { String n, String t -> [name: n, type: [canonicalText: t], hasModifierProperty: { String m -> false }] },
         findFieldByName     : { String n, boolean deep -> extra.statics?.containsKey(n) ? [text: extra.statics[n]] : null }]
    }

    @Test
    void 'gdsl das domains: API do GORM com o tipo, id, hasMany e dynamic finders'() {
        def c = contribute('org/grox/data/gorm.gdsl', psiClass('app.Livro', '/proj/grails-app/domain/app/Livro.groovy',
                [fields: [titulo: 'java.lang.String', ano: 'java.lang.Integer'], statics: [hasMany: 'static hasMany = [capitulos: Capitulo]']]))
        assert c.method('get').type == 'app.Livro' && c.method('get').isStatic
        assert c.method('list', 0).type == 'java.util.List<app.Livro>'
        assert c.method('where').type == 'grails.gorm.DetachedCriteria<app.Livro>'
        assert c.method('save', 0).type == 'app.Livro' && !c.method('save', 0).isStatic
        assert c.properties*.name.containsAll(['id', 'version', 'errors', 'capitulos'])
        assert c.properties.find { it.name == 'capitulos' }.type == 'java.util.Set<app.Capitulo>'
        assert c.method('addToCapitulos').params == [item: 'app.Capitulo']
        assert c.method('findByTitulo').type == 'app.Livro' && c.method('findByTitulo').params == [titulo: 'java.lang.String']
        assert c.method('findAllByTituloIlike', 1).type == 'java.util.List<app.Livro>'
        assert c.method('countByAnoBetween').params.size() == 2 && c.method('findByAnoGreaterThan')
        assert !c.method('findByTituloGreaterThan') && !c.method('findByAnoLike')   // operador pelo tipo
        assert c.method('findByTituloIsNull').params == [:] && c.method('listOrderByAno', 0)
    }

    @Test
    void 'gdsl das domains: grox-app e Entity sim, outras classes e GormEntity explicito nao'() {
        assert contribute('org/grox/data/gorm.gdsl', psiClass('a.X', '/p/grox-app/domain/a/X.groovy')).method('get')
        assert contribute('org/grox/data/gorm.gdsl', psiClass('a.X', '/p/src/main/groovy/a/X.groovy', [annotations: ['grails.gorm.annotation.Entity']])).method('get')
        assert !contribute('org/grox/data/gorm.gdsl', psiClass('a.X', '/p/src/main/groovy/a/X.groovy')).methods
        assert !contribute('org/grox/data/gorm.gdsl', psiClass('a.X', '/p/grails-app/domain/a/X.groovy',
                [implementsList: ['org.grox.data.GormEntity<a.X>']])).methods
    }

    @Test
    void 'gdsl das domains lista todos os metodos da GormEntity'() {
        Set<String> ignored = ['getMetaClass', 'setMetaClass', 'methodMissing', 'constraintsForValidation', 'toString',
                               'setProperties', 'getErrors', 'addBindingError'] as Set
        Class<?> helper = Class.forName('org.grox.data.GormEntity$Trait$Helper')
        Set<String> trait = helper.declaredMethods.findAll { Modifier.isPublic(it.modifiers) && !it.synthetic && !it.name.startsWith('$') }*.name
        trait.addAll(['validate', 'hasErrors', 'clearErrors'])   // de ValidationSupport
        def c = contribute('org/grox/data/gorm.gdsl', psiClass('a.X', '/p/grails-app/domain/a/X.groovy'))
        Set<String> listed = (c.names(true) + c.names(false)) as Set
        assert (trait - ignored - listed).empty : "faltam no gorm.gdsl: ${trait - ignored - listed}"
    }

    @Test
    void 'gdsl dos artefatos: trait pelo diretorio, scaffold e Handler'() {
        def ctrl = contribute('org/grox/web/grails.gdsl', psiClass('app.LivroController', '/p/grails-app/controllers/app/LivroController.groovy',
                [statics: [scaffold: 'static scaffold = Livro']]))
        assert ctrl.delegates == ['org.grox.web.api.ControllerApi', 'grails.rest.RestfulController']
        assert contribute('org/grox/web/grails.gdsl', psiClass('app.LivroService', '/p/grox-app/services/app/LivroService.groovy')).delegates == ['org.grox.web.api.ServiceApi']
        assert contribute('org/grox/web/grails.gdsl', psiClass('app.BootStrap', '/p/grails-app/init/app/BootStrap.groovy')).delegates == ['org.grox.core.LogApi']
        assert !contribute('org/grox/web/grails.gdsl', psiClass('app.Util', '/p/src/main/groovy/app/Util.groovy')).delegates
        assert contribute('org/grox/dsl/dsl.gdsl', psiClass('app.Api', '/p/src/main/groovy/app/Api.groovy', [annotations: ['org.grox.dsl.Handler']])).delegates == ['org.grox.dsl.HandlerApi']
        // as traits citadas existem
        String text = getClass().classLoader.getResource('org/grox/web/grails.gdsl').getText('UTF-8')
        (text =~ /'(org\.grox\.[\w.]+Api)'/).collect { it[1] }.each { Class.forName(it) }
    }

    @Test
    void 'GormEntity generica tipa a API em codigo estatico'() {
        Path target = tmp.resolve('classes')
        def config = new CompilerConfiguration(targetDirectory: target.toFile())
        def unit = new CompilationUnit(config, null, new GroovyClassLoader(getClass().classLoader, config))
        [
                'grails-app/domain/app/Livro.groovy': '''package app
                    import org.grox.data.GormEntity
                    class Livro implements GormEntity<Livro> { String titulo }''',
                'src/main/groovy/app/Uso.groovy'    : '''package app
                    @groovy.transform.CompileStatic
                    class Uso {
                        String titulo() { Livro.get(1L).titulo }
                        List<Livro> todos() { Livro.list() }
                        Livro salvar(Livro l) { l.save(flush: true) }
                        Livro primeiro() { Livro.where { titulo == 'x' }.find() }
                    }'''
        ].each { String rel, String code ->
            Path file = tmp.resolve('src').resolve(rel)
            Files.createDirectories(file.parent)
            file.toFile().text = code
            unit.addSource(file.toFile())
        }
        unit.compile()   // sem o tipo D, o código estático não compilaria (Object não tem titulo)
        assert Files.exists(target.resolve('app/Uso.class'))
    }
}
