package org.grox.ast

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ConstructorNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.grox.data.GormEntity
import org.grox.web.interceptor.InterceptorApi

import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grox.web.Action
import org.grox.web.ControllerArtefact
import org.grox.web.api.ControllerApi
import org.grox.web.api.ServiceApi
import org.grox.web.taglib.GrailsTagLibApi

/**
 * Transformação global: como o Grails, aplica a API de artefato conforme o
 * diretório em {@code grails-app/}.
 * <ul>
 *   <li>{@code controllers/*Controller}: trait {@link ControllerApi} e {@link Action} nas actions; com
 *       {@code static scaffold = Dominio}, a classe estende {@link grails.rest.RestfulController} (scaffold dinâmico)</li>
 *   <li>{@code controllers/*Interceptor}: trait {@link InterceptorApi}</li>
 *   <li>{@code services/*Service}: trait {@link ServiceApi}</li>
 *   <li>{@code taglib/*TagLib}: trait {@link GrailsTagLibApi}</li>
 *   <li>{@code init/*} (BootStrap...): trait {@link org.grox.core.LogApi} ({@code log})</li>
 *   <li>os dos plugins ({@link ArtefactTrait}), como {@code jobs/*Job}: trait {@code JobApi}</li>
 *   <li>{@code domain/*}: trait {@link GormEntity}, id/version, associações e construtores</li>
 * </ul>
 * As classes de {@link #RELOADABLE_DIRS} recebem {@link org.grox.core.reload.Reloadable}.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class ArtefactTransformation implements ASTTransformation {

    private static final ClassNode CONTROLLER_API = ClassHelper.make(ControllerApi)
    private static final ClassNode SERVICE_API = ClassHelper.make(ServiceApi)
    private static final ClassNode TAGLIB_API = ClassHelper.make(GrailsTagLibApi)
    private static final ClassNode ACTION = ClassHelper.make(Action)
    private static final ClassNode INTERCEPTOR_API = ClassHelper.make(InterceptorApi)
    private static final ClassNode LOG_API = ClassHelper.make(org.grox.core.LogApi)
    private static final ClassNode RELOADABLE = ClassHelper.make(org.grox.core.reload.Reloadable)
    /** Diretórios cujas classes a recarga em desenvolvimento troca sem recriar o Hibernate. */
    /** Traits dos plugins, por diretório ({@code META-INF/services/org.grox.ast.ArtefactTrait}). */
    static final Map<String, ArtefactTrait> PLUGIN_TRAITS = ServiceLoader.load(ArtefactTrait, ArtefactTransformation.classLoader)
            .collectEntries { ArtefactTrait t -> [t.directory, t] }

    static final Set<String> RELOADABLE_DIRS = (['controllers', 'services', 'taglib', 'init'] + PLUGIN_TRAITS.keySet()) as Set

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        String dir = artefactDir(source?.name)
        if (!dir || source.AST == null) return
        for (ClassNode cn : source.AST.classes) {
            if (dir in RELOADABLE_DIRS && cn.outerClass == null && !cn.getAnnotations(RELOADABLE)) cn.addAnnotation(new AnnotationNode(RELOADABLE))
            if (cn.interface || cn.enum || cn.abstract || cn.outerClass != null) continue
            String n = cn.nameWithoutPackage
            if (dir == 'controllers' && n.endsWith('Interceptor')) {
                addTrait(cn, INTERCEPTOR_API)
            } else if (dir == 'controllers' && n.endsWith('Controller')) {
                scaffold(cn, source)
                addTrait(cn, CONTROLLER_API)
                markActions(cn)
            } else if (dir == 'services' && n.endsWith('Service')) {
                addTrait(cn, SERVICE_API)
            } else if (dir == 'taglib' && n.endsWith('TagLib')) {
                addTrait(cn, TAGLIB_API)
            } else if (dir == 'init' && !cn.getMethods('getLog') && !cn.getField('log')) {
                addTrait(cn, LOG_API)
            } else if (PLUGIN_TRAITS[dir] && n.endsWith(PLUGIN_TRAITS[dir].suffix)) {
                addTrait(cn, ClassHelper.make(PLUGIN_TRAITS[dir].trait))
            } else if (dir == 'domain') {
                org.grox.data.ast.DomainClassSupport.apply(cn)
            }
        }
    }


    /** Subdiretório do layout ({@code grails-app}, {@code grox-app} ou o de {@code grox.appDir}) do fonte. */
    static String artefactDir(String path) {
        org.grox.core.AppLayout.artefactDir(path)
    }

    /**
     * {@code static scaffold = Livro}: como o plugin scaffolding do Grails 3+, o controller estende o
     * {@code RestfulController} da domain (actions index, show, create, save, edit, update, delete) e, sem
     * construtor próprio, ganha um que chama {@code super(Livro)}. As views que faltam vêm dos templates de scaffold.
     */
    private static void scaffold(ClassNode cn, SourceUnit source) {
        FieldNode f = cn.getDeclaredField('scaffold')
        if (f == null || !f.static || f.initialValueExpression == null) return
        if (!(f.initialValueExpression instanceof ClassExpression)) {
            // o scaffold = true do Grails 2 (domain pelo nome) não é suportado
            source.addError(new SyntaxException("${cn.name}: use static scaffold = <DomainClass> (scaffold = true do Grails 2 não é suportado)",
                    f.lineNumber, f.columnNumber))
            return
        }
        if (cn.superClass != ClassHelper.OBJECT_TYPE) return
        ClassNode domain = ((ClassExpression) f.initialValueExpression).type
        cn.superClass = org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics(grails.rest.RestfulController, domain)
        if (!cn.declaredConstructors) {
            def body = new ExpressionStatement(new ConstructorCallExpression(ClassNode.SUPER, new ArgumentListExpression(new ClassExpression(domain))))
            cn.addConstructor(new ConstructorNode(java.lang.reflect.Modifier.PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, body))
        }
    }

    private static void addTrait(ClassNode cn, ClassNode trait) {
        if (!cn.implementsInterface(trait)) cn.addInterface(trait)
    }

    private static void markActions(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if (!isAction(mn) || mn.getAnnotations(ACTION)) continue
            def an = new AnnotationNode(ACTION)
            an.addMember('parameterNames', new ListExpression(mn.parameters.collect { new ConstantExpression(it.name) as Expression }))
            mn.addAnnotation(an)
        }
    }

    private static boolean isAction(MethodNode mn) {
        mn.public && !mn.static && !mn.abstract && !mn.synthetic && !mn.name.contains('$') &&
                !mn.name.startsWith('<') && !(mn.name in ControllerArtefact.NON_ACTIONS)
    }
}
