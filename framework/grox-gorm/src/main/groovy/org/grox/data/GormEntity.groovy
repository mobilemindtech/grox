package org.grox.data

import org.grox.data.query.CriteriaBuilder
import org.grox.data.query.DetachedCriteria
import org.grox.data.validation.ValidationSupport
import org.grox.data.binding.DataBinder

/**
 * API do GORM aplicada (AST) às classes de {@code grails-app/domain}. Nos métodos
 * estáticos de trait, {@code this} é a classe concreta. Genérica como a do GORM: com
 * {@code class Livro implements GormEntity<Livro>} explícito (opcional; a AST aplica a trait sem tipo), o IDE
 * sabe que {@code Livro.get(1)} é um {@code Livro} e {@code Livro.list()} um {@code List<Livro>}.
 */
trait GormEntity<D> implements ValidationSupport {

    // --- estáticos ---

    static D get(Object id) { Gorm.api(this).get(id) }

    static D read(Object id) { Gorm.api(this).get(id) }

    static D load(Object id) { Gorm.api(this).load(id) }

    static boolean exists(Object id) { Gorm.api(this).exists(id) }

    static List<D> getAll(Collection ids) { Gorm.api(this).getAll(ids) }

    static List<D> list() { Gorm.api(this).list([:]) }

    static List<D> list(Map params) { Gorm.api(this).list(params) }

    static long count() { Gorm.api(this).count() }

    static D first() { Gorm.api(this).first() }

    static D first(String sort) { Gorm.api(this).first(sort) }

    static D first(Map args) { Gorm.api(this).first(args) }

    static D last() { Gorm.api(this).last() }

    static D last(String sort) { Gorm.api(this).last(sort) }

    static D last(Map args) { Gorm.api(this).last(args) }

    static D findWhere(Map criteria) { Gorm.api(this).findWhere(criteria) }

    static List<D> findAllWhere(Map criteria) { Gorm.api(this).findAllWhere(criteria) }

    static List<D> findAllWhere(Map criteria, Map params) { Gorm.api(this).findAllWhere(criteria, params) }

    static D find(String hql) { Gorm.api(this).find(hql) }

    static D find(String hql, Object params) { Gorm.api(this).find(hql, params) }

    static List<D> findAll() { Gorm.api(this).list([:]) }

    static List<D> findAll(String hql) { Gorm.api(this).findAll(hql) }

    static List<D> findAll(String hql, Object params) { Gorm.api(this).findAll(hql, params) }

    static List<D> findAll(String hql, Object params, Map pagination) { Gorm.api(this).findAll(hql, params, pagination) }

    static List executeQuery(String hql) { Gorm.api(this).executeQuery(hql) }

    static List executeQuery(String hql, Object params) { Gorm.api(this).executeQuery(hql, params) }

    static List executeQuery(String hql, Object params, Map pagination) { Gorm.api(this).executeQuery(hql, params, pagination) }

    static int executeUpdate(String hql) { Gorm.api(this).executeUpdate(hql) }

    static int executeUpdate(String hql, Object params) { Gorm.api(this).executeUpdate(hql, params) }

    static Object withTransaction(Closure body) { Persistence.withTransaction(body) }

    static Object withNewTransaction(Closure body) { Persistence.withNewTransaction(body) }

    static Object withSession(Closure body) { Persistence.withSession(body) }

    /** Plugin hibernate-filter: desliga os filtros padrão no bloco. */
    static Object withoutHibernateFilters(Closure body) { HibernateFilters.without(Gorm.hibernateFilterDefaults(), body) }

    static Object withoutHibernateFilter(String name, Closure body) { HibernateFilters.without([name], body) }

    /** Liga os filtros padrão no bloco (se tiverem sido desligados). */
    static Object withHibernateFilters(Closure body) { HibernateFilters.with(Gorm.hibernateFilterDefaults(), body) }

    static void enableHibernateFilter(String name) { Persistence.withSession { s -> s.enableFilter(HibernateFilters.checked(name)) } }

    static void disableHibernateFilter(String name) { Persistence.withSession { s -> s.disableFilter(HibernateFilters.checked(name)) } }

    static Object withNewSession(Closure body) { Persistence.withNewSession(body) }

    /** Executa com outro tenant ({@code Tenants.withId}). */
    static Object withTenant(Serializable tenantId, Closure body) { grails.gorm.multitenancy.Tenants.withId(tenantId, body) }

    /** {@code Livro.where { titulo ==~ 'G%' && preco > 10 }} (closure reescrito pela AST). */
    static DetachedCriteria<D> where(Closure body) { new DetachedCriteria(this).where(body) }

    static DetachedCriteria<D> whereAny(Closure body) { new DetachedCriteria(this).whereAny(body) }

    static List<D> findAll(Closure body) { new DetachedCriteria(this).where(body).list() }

    static List<D> findAll(Map params, Closure body) { new DetachedCriteria(this).where(body).list(params) }

    static D find(Closure body) { new DetachedCriteria(this).where(body).find() }

    static CriteriaBuilder createCriteria() { new CriteriaBuilder(this) }

    static List<D> withCriteria(Closure body) { new CriteriaBuilder(this).list(body) }

    static List<D> withCriteria(Map params, Closure body) { new CriteriaBuilder(this).list(params, body) }

    /** Dynamic finders: {@code findByNome}, {@code findAllByIdadeGreaterThan}, {@code countBy...}. */
    static Object $static_methodMissing(String name, Object args) {
        Gorm.api(this).invokeDynamic(name, (Object[]) args)
    }

    // --- instância ---

    D save() { GormInstanceApi.save(this, [:]) }

    D save(Map opts) { GormInstanceApi.save(this, opts) }

    void delete() { GormInstanceApi.delete(this, [:]) }

    void delete(Map opts) { GormInstanceApi.delete(this, opts) }

    D refresh() { GormInstanceApi.refresh(this) }

    D merge() { GormInstanceApi.merge(this, [:]) }

    D merge(Map opts) { GormInstanceApi.merge(this, opts) }

    D attach() { GormInstanceApi.attach(this) }

    boolean isDirty() { GormInstanceApi.isDirty(this, null) }

    boolean isDirty(String property) { GormInstanceApi.isDirty(this, property) }

    Object getPersistentValue(String property) { GormInstanceApi.getPersistentValue(this, property) }

    List<String> getDirtyPropertyNames() { GormInstanceApi.getDirtyPropertyNames(this) }

    D discard() { GormInstanceApi.discard(this) }

    boolean isAttached() { GormInstanceApi.isAttached(this) }

    /** Com chave composta, como no GORM, a própria instância é o identificador. */
    Object ident() { Gorm.domain(this.getClass()).composite ? this : Gorm.domain(this.getClass()).identity.get(this) }

    /** {@code usuario.properties = params}: binding em massa. */
    void setProperties(Object source) { DataBinder.bind(this, source) }

    @Override
    Map<String, Map<String, Object>> constraintsForValidation() {
        Gorm.domain(this.getClass()).effectiveConstraints()
    }

    Object methodMissing(String name, Object args) {
        GormInstanceApi.relation(this, name, (Object[]) args)
    }

    String toString() { GormInstanceApi.toString(this) }
}
