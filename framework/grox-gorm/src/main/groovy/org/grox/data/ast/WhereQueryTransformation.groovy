package org.grox.data.ast

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.DynamicVariable
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.Variable
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.BooleanExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.NotExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.grox.data.GormEntity
import org.grox.data.query.DetachedCriteria
import org.grox.data.query.Where

/**
 * Consultas {@code where} do GORM. O corpo do closure passado a {@code where}/{@code whereAny}
 * (e a {@code find}/{@code findAll} com closure) de uma domain class é reescrito para
 * devolver uma árvore de critérios:
 * <pre>
 * Livro.where { titulo ==~ 'G%' && (preco > min || autor.nome == 'X') && !esgotado }
 *   → { -> Where.and(Where.like('titulo', 'G%'), Where.or(Where.gt('preco', min), Where.eq('autor.nome', 'X')), Where.not(Where.isTrue('esgotado'))) }
 * </pre>
 * Operadores: == != > >= < <= ==~ (like) =~ (ilike) in !in && || !. O lado esquerdo é uma
 * propriedade (ou caminho de associação); o direito é um valor, ou uma propriedade da domain
 * class (comparação entre propriedades). Várias linhas no closure = AND (OR em whereAny).
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
class WhereQueryTransformation implements ASTTransformation {

    static final Set<String> CHAINABLE = ['where', 'whereAny'] as Set
    static final Set<String> STATIC_ONLY = ['find', 'findAll'] as Set
    private static final ClassNode WHERE = ClassHelper.make(Where)
    private static final ClassNode WHERE_ANY = ClassHelper.make(DetachedCriteria.WhereAny)
    private static final ClassNode GORM_ENTITY = ClassHelper.make(GormEntity)
    /** Domínio desconhecido (where encadeado sobre expressão não resolvida). */
    private static final ClassNode UNKNOWN = ClassHelper.OBJECT_TYPE

    static final Map<Integer, String> OPERATORS = [
            (Types.COMPARE_EQUAL)             : 'eq',
            (Types.COMPARE_IDENTICAL)         : 'eq',
            (Types.COMPARE_NOT_EQUAL)         : 'ne',
            (Types.COMPARE_NOT_IDENTICAL)     : 'ne',
            (Types.COMPARE_GREATER_THAN)      : 'gt',
            (Types.COMPARE_GREATER_THAN_EQUAL): 'ge',
            (Types.COMPARE_LESS_THAN)         : 'lt',
            (Types.COMPARE_LESS_THAN_EQUAL)   : 'le',
            (Types.MATCH_REGEX)               : 'like',
            (Types.FIND_REGEX)                : 'ilike',
            (Types.KEYWORD_IN)                : 'inList',
            (Types.COMPARE_NOT_IN)            : 'notInList',
    ]

    @Override
    void visit(ASTNode[] nodes, SourceUnit source) {
        if (source?.AST == null) return
        for (ClassNode cn : source.AST.classes) new Visitor(source, cn).visitClass(cn)
    }

    static class Visitor extends ClassCodeVisitorSupport {
        final SourceUnit sourceUnit
        final ClassNode current

        Visitor(SourceUnit sourceUnit, ClassNode current) {
            this.sourceUnit = sourceUnit
            this.current = current
        }

        @Override
        protected SourceUnit getSourceUnit() {
            sourceUnit
        }

        @Override
        void visitMethodCallExpression(MethodCallExpression call) {
            super.visitMethodCallExpression(call)
            String name = call.methodAsString
            if (!(name in CHAINABLE) && !(name in STATIC_ONLY)) return
            ClosureExpression closure = singleClosure(call.arguments)
            if (closure == null || closure.parameterSpecified) return
            ClassNode domain = domainOf(call)
            if (domain == null || (name in STATIC_ONLY && domain == UNKNOWN)) return
            rewrite(closure, domain == UNKNOWN ? null : domain, name == 'whereAny', domain != UNKNOWN)
        }

        @Override
        void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
            super.visitStaticMethodCallExpression(call)
            // where { } dentro de método estático da própria domain class
            if (call.method in CHAINABLE && isDomain(call.ownerType)) {
                ClosureExpression closure = singleClosure(call.arguments)
                if (closure != null && !closure.parameterSpecified) rewrite(closure, call.ownerType, call.method == 'whereAny', true)
            }
        }

        private ClassNode domainOf(MethodCallExpression call) {
            Expression obj = call.objectExpression
            if (obj instanceof ClassExpression) return isDomain(obj.type) ? obj.type : null
            if (call.implicitThis) return isDomain(current) ? current : null
            if (call.methodAsString in CHAINABLE) {
                // where encadeado (Classe.where{}.where{}) ou sobre uma variável DetachedCriteria
                if (obj instanceof MethodCallExpression && ((MethodCallExpression) obj).methodAsString in CHAINABLE + ['max', 'offset', 'sort']) {
                    return domainOf((MethodCallExpression) obj) ?: UNKNOWN
                }
                return UNKNOWN
            }
            null
        }

        /** Erros coletados; com {@code strict} (domain conhecida) viram erro de compilação, senão o closure fica intacto. */
        private List<List> pending

        private void rewrite(ClosureExpression closure, ClassNode domain, boolean any, boolean strict) {
            pending = []
            try {
                Expression result = build(closure, domain, any)
                if (pending) {
                    if (strict) pending.each { List e -> sourceUnit.addError(new SyntaxException((String) e[1], ((ASTNode) e[0]).lineNumber, ((ASTNode) e[0]).columnNumber)) }
                    return
                }
                Statement code = closure.code
                def block = new BlockStatement()
                block.addStatement(GeneralUtils.returnS(result))
                block.variableScope = code instanceof BlockStatement ? ((BlockStatement) code).variableScope : closure.variableScope
                closure.code = block
            } finally {
                pending = null
            }
        }

        private Expression build(ClosureExpression closure, ClassNode domain, boolean any) {
            Statement code = closure.code
            List<Statement> statements = code instanceof BlockStatement ? ((BlockStatement) code).statements : [code]
            if (!statements) {
                error(closure, 'where { } vazio')
                return null
            }
            List<Expression> criteria = []
            for (Statement st : statements) {
                Expression e = st instanceof ExpressionStatement ? ((ExpressionStatement) st).expression :
                        st instanceof ReturnStatement ? ((ReturnStatement) st).expression : null
                if (e == null) {
                    error(st, 'where { } aceita apenas expressões de critério (ex.: nome == x && idade > y)')
                    return null
                }
                Expression c = criterion(e, domain)
                if (c == null) return null
                criteria << c
            }
            criteria.size() == 1 && !any ? criteria[0] : GeneralUtils.ctorX(WHERE_ANY, new ArgumentListExpression(criteria))
        }

        private Expression criterion(Expression e, ClassNode domain) {
            // NotExpression é subclasse de BooleanExpression: testar antes
            if (e instanceof NotExpression) {
                Expression inner = criterion(((NotExpression) e).expression, domain)
                return inner == null ? null : where('not', inner)
            }
            if (e instanceof BooleanExpression) return criterion(((BooleanExpression) e).expression, domain)
            if (e instanceof BinaryExpression) {
                BinaryExpression b = (BinaryExpression) e
                int op = b.operation.type
                if (op == Types.LOGICAL_AND || op == Types.LOGICAL_OR) {
                    Expression l = criterion(b.leftExpression, domain)
                    Expression r = criterion(b.rightExpression, domain)
                    return l == null || r == null ? null : where(op == Types.LOGICAL_AND ? 'and' : 'or', l, r)
                }
                String name = OPERATORS[op]
                if (name == null) {
                    error(e, "operador não suportado em where { }: ${b.operation.text}")
                    return null
                }
                String left = path(b.leftExpression, domain, true)
                if (left == null) {
                    error(b.leftExpression, "o lado esquerdo em where { } deve ser uma propriedade${domain ? ' de ' + domain.nameWithoutPackage : ''}: ${b.leftExpression.text}")
                    return null
                }
                String right = name in ['eq', 'ne', 'gt', 'ge', 'lt', 'le'] ? path(b.rightExpression, domain, false) : null
                if (right != null) return where(name + 'Property', GeneralUtils.constX(left), GeneralUtils.constX(right))
                return where(name, GeneralUtils.constX(left), b.rightExpression)
            }
            String p = path(e, domain, true)
            if (p != null) return where('isTrue', GeneralUtils.constX(p))
            error(e, "expressão não suportada em where { }: ${e.text}")
            null
        }

        private static Expression where(String method, Expression... args) {
            GeneralUtils.callX(GeneralUtils.classX(WHERE), method, new ArgumentListExpression(args.toList()))
        }

        /** Caminho de propriedade ({@code nome}, {@code autor.nome}) ou null se for um valor. */
        private String path(Expression e, ClassNode domain, boolean left) {
            if (e instanceof VariableExpression) {
                VariableExpression v = (VariableExpression) e
                if (v.thisExpression || v.superExpression) return null
                Variable av = v.accessedVariable
                boolean dynamic = av == null || av instanceof DynamicVariable || av instanceof PropertyNode || av instanceof FieldNode
                if (!dynamic) return null
                if (domain != null) return domainProperties(domain).contains(v.name) ? v.name : null
                return left ? v.name : null
            }
            if (e instanceof PropertyExpression) {
                PropertyExpression pe = (PropertyExpression) e
                if (pe.safe || pe.spreadSafe) return null
                String base = path(pe.objectExpression, domain, left)
                String name = pe.propertyAsString
                if (base == null || name == null) return null
                // cada nível do caminho (livro.autor.nome) é conferido quando o tipo da associação é uma domain class
                ClassNode owner = domain == null ? null : associationType(domain, base)
                if (owner != null && !domainProperties(owner).contains(name)) {
                    error(pe, "propriedade inexistente em where { }: ${owner.nameWithoutPackage} não tem '${name}' (${base}.${name})")
                }
                return base + '.' + name
            }
            null
        }

        /** A domain class ao fim do caminho {@code autor} ou {@code livro.autor}; null se algum nível não for domain class. */
        private static ClassNode associationType(ClassNode domain, String path) {
            ClassNode type = domain
            for (String segment : path.split('\\.')) {
                type = propertyType(type, segment)
                if (type == null || !isDomain(type)) return null
            }
            type
        }

        private void error(ASTNode node, String message) {
            pending << [node, message]
        }
    }

    /** O closure como último argumento ({@code findAll([max: 10]) { ... }}); os demais não podem ser closures. */
    static ClosureExpression singleClosure(Expression args) {
        if (!(args instanceof ArgumentListExpression)) return null
        List<Expression> list = ((ArgumentListExpression) args).expressions
        if (!list || !(list.last() instanceof ClosureExpression) || list.count { it instanceof ClosureExpression } != 1) return null
        (ClosureExpression) list.last()
    }

    static boolean isDomain(ClassNode type) {
        if (type == null || type == UNKNOWN) return false
        if (type.implementsInterface(GORM_ENTITY)) return true
        if (type.annotations.any { it.classNode.name == 'grails.gorm.annotation.Entity' }) return true
        String src = type.module?.context?.name
        // fonte em grails-app/domain (ou grox-app...): a AST do layout Grails ainda pode não ter aplicado o GormEntity
        org.grox.core.AppLayout.artefactDir(src) == 'domain'
    }

    /**
     * Tipo da propriedade (o elemento, numa coleção): o declarado, o do {@code hasMany}/{@code belongsTo}
     * ou o genérico da coleção; null se não der para saber.
     */
    static ClassNode propertyType(ClassNode type, String name) {
        for (ClassNode k = type; k != null && k != ClassHelper.OBJECT_TYPE; k = k.superClass) {
            for (String s : ['hasMany', 'belongsTo']) {
                FieldNode f = k.getDeclaredField(s)
                if (f?.initialValueExpression instanceof MapExpression) {
                    MapEntryExpression me = ((MapExpression) f.initialValueExpression).mapEntryExpressions.find { it.keyExpression.text == name }
                    if (me?.valueExpression instanceof ClassExpression) return me.valueExpression.type
                }
            }
            PropertyNode p = k.getProperty(name)
            ClassNode t = p != null ? p.type : k.getDeclaredField(name)?.type
            if (t != null) {
                if (t.isDerivedFrom(ClassHelper.make(Collection)) || t.implementsInterface(ClassHelper.make(Collection))) {
                    return t.genericsTypes?.length == 1 ? t.genericsTypes[0].type : null
                }
                return t
            }
        }
        null
    }

    /** Propriedades declaradas, as adicionadas pela AST (id, version, tenantId) e as de hasMany/belongsTo. */
    static Set<String> domainProperties(ClassNode type) {
        Set<String> names = ['id', 'version', 'tenantId'] as Set
        for (ClassNode k = type; k != null && k != ClassHelper.OBJECT_TYPE; k = k.superClass) {
            k.fields.each { if (!it.static) names << it.name }
            k.properties.each { if (!it.static) names << it.name }
            ['hasMany', 'belongsTo'].each { String s ->
                FieldNode f = k.getDeclaredField(s)
                if (f?.initialValueExpression instanceof MapExpression) {
                    ((MapExpression) f.initialValueExpression).mapEntryExpressions.each { MapEntryExpression me -> names << me.keyExpression.text }
                }
            }
        }
        names
    }
}
