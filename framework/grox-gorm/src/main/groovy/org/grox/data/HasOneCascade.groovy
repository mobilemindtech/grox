package org.grox.data

import groovy.transform.CompileStatic
import org.hibernate.Hibernate
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.DeleteContext
import org.hibernate.event.spi.DeleteEvent
import org.hibernate.event.spi.DeleteEventListener
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.PersistContext
import org.hibernate.event.spi.PersistEvent
import org.hibernate.event.spi.PersistEventListener

/**
 * Cascade dos hasOne com filho multi-tenant ({@link DomainProperty#tenantHasOne}), que o Hibernate não faz por
 * estarem mapeados como many-to-one só leitura. Como o one-to-one inverso do GORM: o filho é gravado depois do
 * pai (a chave estrangeira está no filho) e apagado antes dele.
 * <ul>
 *   <li>persist do pai (direto ou por cascade de outra entidade): listener do Hibernate;</li>
 *   <li>{@code save()} de um pai já gravado (gerenciado ou destacado): {@link #afterSave};</li>
 *   <li>delete do pai: listener que remove o filho antes.</li>
 * </ul>
 */
@CompileStatic
class HasOneCascade implements PersistEventListener, DeleteEventListener {

    private final Map<Class, List<DomainProperty>> byType

    private HasOneCascade(Map<Class, List<DomainProperty>> byType) {
        this.byType = byType
    }

    static Map<Class, List<DomainProperty>> props(Collection<DomainClass> domains) {
        Map<Class, List<DomainProperty>> out = [:]
        domains.each { DomainClass d ->
            List<DomainProperty> ps = d.persistentProperties.values().findAll { DomainProperty p -> p.tenantHasOne }.toList()
            if (ps) out[d.type] = ps
        }
        out
    }

    /** Registra os listeners se alguma domain class tiver hasOne com filho multi-tenant. */
    static void register(SessionFactory sf, Collection<DomainClass> domains) {
        Map<Class, List<DomainProperty>> byType = props(domains)
        if (!byType) return
        def listener = new HasOneCascade(byType)
        def registry = ((SessionFactoryImplementor) sf).serviceRegistry.requireService(EventListenerRegistry)
        registry.appendListeners(EventType.PERSIST, (PersistEventListener) listener)
        registry.prependListeners(EventType.DELETE, (DeleteEventListener) listener)
    }

    /** {@code save()} de um pai gerenciado ou destacado: grava o filho novo (ou destacado) e liga-o ao pai gerenciado. */
    static void afterSave(org.hibernate.Session s, DomainClass d, Object entity, Object managed) {
        for (DomainProperty p : d.persistentProperties.values()) {
            if (!p.tenantHasOne) continue
            Object child = p.get(entity)
            if (child == null || s.contains(child)) continue
            DomainClass cd = Gorm.domain(child.getClass())
            Object stored = cd.identity.get(child) == null ? persisted(s, child) : s.merge(child)
            if (managed != null && !managed.is(entity)) p.set(managed, stored)
        }
    }

    private static Object persisted(org.hibernate.Session s, Object child) {
        s.persist(child)
        child
    }

    private List<DomainProperty> propsOf(Object entity) {
        entity == null ? null : byType[Hibernate.getClass(entity)]
    }

    @Override
    void onPersist(PersistEvent event) {
        cascadePersist(event)
    }

    @Override
    void onPersist(PersistEvent event, PersistContext createdAlready) {
        cascadePersist(event)
    }

    private void cascadePersist(PersistEvent event) {
        List<DomainProperty> ps = propsOf(event.object)
        if (!ps) return
        def s = event.session
        for (DomainProperty p : ps) {
            Object child = p.get(event.object)
            if (child != null && !s.contains(child)) s.persist(child)
        }
    }

    @Override
    void onDelete(DeleteEvent event) {
        cascadeDelete(event)
    }

    @Override
    void onDelete(DeleteEvent event, DeleteContext transientEntities) {
        cascadeDelete(event)
    }

    private void cascadeDelete(DeleteEvent event) {
        Object entity = Hibernate.unproxy(event.object)
        List<DomainProperty> ps = propsOf(entity)
        if (!ps) return
        def s = event.session
        for (DomainProperty p : ps) {
            Object child = p.get(entity)
            if (child != null && s.contains(child)) s.remove(child)
        }
    }
}
