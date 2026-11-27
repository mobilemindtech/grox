package org.grox.data.ast

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.grox.data.GormEntity
import org.grox.data.binding.DataBinder

import java.lang.reflect.Modifier

/**
 * O que a compilação acrescenta a uma domain class, como no GORM: o trait {@link GormEntity}, {@code Long id},
 * {@code Long version}, as coleções de {@code hasMany} ({@code Set}), as referências de {@code hasOne} e
 * {@code belongsTo = [x: Tipo]} e os construtores sem argumentos e com Map (binding com conversão, ignorando
 * chaves desconhecidas). Usado pela AST do layout Grails ({@code grails-app/domain}) e pela anotação
 * {@code @grails.gorm.annotation.Entity}.
 */
class DomainClassSupport {

    private static final ClassNode GORM_ENTITY = ClassHelper.make(GormEntity)
    private static final ClassNode LONG = ClassHelper.make(Long)
    private static final ClassNode SET = ClassHelper.make(Set)

    static void apply(ClassNode cn) {
        // GormEntity<Livro>: a trait é genérica (o tipo vale para o código estático e para o IDE)
        // também na subclasse de outra domain (herança): os estáticos do trait usam a classe que os declara, e sem
        // reaplicar o trait Cachorro.list() listaria os Animal
        if (!cn.interfaces.any { it.name == GORM_ENTITY.name }) cn.addInterface(org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics(GormEntity, cn))
        if (cn.abstract && cn.getAnnotations(ABSTRACT_ENTITY).isEmpty()) cn.addAnnotation(new org.codehaus.groovy.ast.AnnotationNode(ABSTRACT_ENTITY))
        injectDomainMembers(cn)
    }

    private static final ClassNode ABSTRACT_ENTITY = ClassHelper.make(org.grox.data.AbstractEntity)

    private static void injectDomainMembers(ClassNode cn) {
        if (!hasMember(cn, 'id')) cn.addProperty('id', Modifier.PUBLIC, LONG, null, null, null)
        if (!hasMember(cn, 'version')) cn.addProperty('version', Modifier.PUBLIC, LONG, null, null, null)
        boolean multiTenant = cn.interfaces.any { it.name == 'grails.gorm.MultiTenant' || it.nameWithoutPackage == 'MultiTenant' }
        if (multiTenant && !hasMember(cn, 'tenantId') && !staticClosure(cn, 'mapping')) {
            cn.addProperty('tenantId', Modifier.PUBLIC, ClassHelper.STRING_TYPE.plainNodeReference, null, null, null)
        }
        staticMap(cn, 'hasMany')?.mapEntryExpressions?.each { MapEntryExpression e ->
            String n = e.keyExpression.text
            if (hasMember(cn, n)) return
            // Set<Livro>: o genérico fica no bytecode (where { livros.titulo } confere o caminho numa classe já compilada)
            ClassNode type = e.valueExpression instanceof ClassExpression
                    ? org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics(Set, e.valueExpression.type)
                    : SET.plainNodeReference
            cn.addProperty(n, Modifier.PUBLIC, type, null, null, null)
        }
        // hasOne = [perfil: Perfil]: a referência ao filho (a chave estrangeira fica no filho)
        staticMap(cn, 'hasOne')?.mapEntryExpressions?.each { MapEntryExpression e ->
            String n = e.keyExpression.text
            if (e.valueExpression instanceof ClassExpression && !hasMember(cn, n)) {
                cn.addProperty(n, Modifier.PUBLIC, e.valueExpression.type.plainNodeReference, null, null, null)
            }
        }
        staticMap(cn, 'belongsTo')?.mapEntryExpressions?.each { MapEntryExpression e ->
            String n = e.keyExpression.text
            if (e.valueExpression instanceof ClassExpression && !hasMember(cn, n)) {
                cn.addProperty(n, Modifier.PUBLIC, e.valueExpression.type.plainNodeReference, null, null, null)
            }
        }
        boolean hasNoArg = cn.declaredConstructors.any { it.parameters.length == 0 }
        boolean hasMap = cn.declaredConstructors.any { it.parameters.length == 1 && it.parameters[0].type.name in ['java.util.Map', 'java.util.LinkedHashMap'] }
        if (cn.declaredConstructors.isEmpty()) {
            cn.addConstructor(new ConstructorNode(Modifier.PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, new BlockStatement()))
            hasNoArg = true
        }
        if (hasNoArg && !hasMap) {
            // new Dominio(params): chama o construtor sem argumentos (inicializações da app) e faz o binding
            def param = new Parameter(ClassHelper.MAP_TYPE.plainNodeReference, 'args')
            def bind = GeneralUtils.stmt(GeneralUtils.callX(GeneralUtils.classX(ClassHelper.make(DataBinder)), 'bind',
                    GeneralUtils.args(VariableExpression.THIS_EXPRESSION, GeneralUtils.varX(param))))
            def thisCall = GeneralUtils.stmt(GeneralUtils.ctorThisX(GeneralUtils.args()))
            cn.addConstructor(new ConstructorNode(Modifier.PUBLIC, [param] as Parameter[], ClassNode.EMPTY_ARRAY, GeneralUtils.block(thisCall, bind)))
        }
    }

    private static boolean hasMember(ClassNode cn, String name) {
        cn.getProperty(name) != null || cn.getField(name) != null
    }

    private static boolean staticClosure(ClassNode cn, String name) {
        FieldNode f = cn.getDeclaredField(name)
        f?.static && f.initialValueExpression?.text?.contains('tenantId')
    }

    private static MapExpression staticMap(ClassNode cn, String name) {
        FieldNode f = cn.getDeclaredField(name)
        f?.static && f.initialValueExpression instanceof MapExpression ? (MapExpression) f.initialValueExpression : null
    }
}
