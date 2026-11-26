package org.grox.data

import org.grails.datastore.mapping.engine.event.PreInsertEvent as GormPreInsert
import org.grails.datastore.mapping.engine.event.PreUpdateEvent as GormPreUpdate
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.AutoFlushEvent
import org.hibernate.event.spi.AutoFlushEventListener
import org.hibernate.event.spi.EventType
import org.hibernate.event.spi.FlushEvent
import org.hibernate.event.spi.FlushEventListener
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostLoadEvent
import org.hibernate.event.spi.PostLoadEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import org.hibernate.persister.entity.EntityPersister

/**
 * Eventos de domain no flush do Hibernate, como no GORM:
 * <ul>
 *   <li>{@code beforeInsert}/{@code beforeUpdate}, os listeners {@code Pre*} ({@code @Listener}, audit log) e os
 *       timestamps também para o que não passou pelo {@code save()}: alteração por dirty checking e filho salvo por
 *       cascata. As alterações feitas nesses métodos vão para o SQL. O que já passou pelo {@code save()} (que dispara
 *       os mesmos eventos antes da validação) não dispara de novo;</li>
 *   <li>{@code afterInsert}, {@code afterUpdate}, {@code afterDelete} e {@code afterLoad}, depois do SQL.</li>
 * </ul>
 */
class DomainEventBridge implements PreInsertEventListener, PreUpdateEventListener, PostInsertEventListener,
        PostUpdateEventListener, PostDeleteEventListener, PostLoadEventListener, FlushEventListener, AutoFlushEventListener {

    /** Entidades cujos eventos {@code before*} o {@code save()} já disparou, por sessão (até o fim do flush). */
    private static final Map<Object, Set<Object>> HANDLED = Collections.synchronizedMap(new WeakHashMap<Object, Set<Object>>())

    static void register(SessionFactory sf) {
        def bridge = new DomainEventBridge()
        def registry = ((SessionFactoryImplementor) sf).serviceRegistry.requireService(EventListenerRegistry)
        registry.appendListeners(EventType.PRE_INSERT, (PreInsertEventListener) bridge)
        registry.appendListeners(EventType.PRE_UPDATE, (PreUpdateEventListener) bridge)
        registry.appendListeners(EventType.POST_INSERT, (PostInsertEventListener) bridge)
        registry.appendListeners(EventType.POST_UPDATE, (PostUpdateEventListener) bridge)
        registry.appendListeners(EventType.POST_DELETE, (PostDeleteEventListener) bridge)
        registry.appendListeners(EventType.POST_LOAD, (PostLoadEventListener) bridge)
        registry.appendListeners(EventType.FLUSH, (FlushEventListener) bridge)
        registry.appendListeners(EventType.AUTO_FLUSH, (AutoFlushEventListener) bridge)
    }

    /** O {@code save()} já disparou os eventos {@code before*} desta entidade nesta sessão. */
    static void handled(Object session, Object entity) {
        if (session == null || entity == null) return
        Set<Object> set = HANDLED.computeIfAbsent(session) { Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()) }
        synchronized (set) { set.add(entity) }
    }

    private static boolean consume(Object session, Object entity) {
        Set<Object> set = HANDLED.get(session)
        if (set == null) return false
        synchronized (set) { set.remove(entity) }
    }

    @Override
    boolean onPreInsert(PreInsertEvent event) {
        before(event.entity, event.session, event.persister, event.state, true)
    }

    @Override
    boolean onPreUpdate(PreUpdateEvent event) {
        before(event.entity, event.session, event.persister, event.state, false)
    }

    /** @return true veta a operação (um listener {@code Pre*} devolveu false) */
    private static boolean before(Object entity, Object session, EntityPersister persister, Object[] state, boolean insert) {
        if (consume(session, entity) || !Gorm.isDomain(entity.getClass())) return false
        DomainClass d = Gorm.domain(entity.getClass())
        Object[] before = persister.getValues(entity)
        call(entity, insert ? 'beforeInsert' : 'beforeUpdate')
        if (!GormEvents.fire(insert ? new GormPreInsert(entity) : new GormPreUpdate(entity))) return true
        GormInstanceApi.timestamps(d, entity, insert)
        // o Hibernate já tirou o estado: o que os eventos alteraram na entidade vai para o SQL (só isso: a versão,
        // por exemplo, já foi incrementada no estado e não pode voltar ao valor da entidade)
        Object[] after = persister.getValues(entity)
        for (int i = 0; i < Math.min(after.length, state.length); i++) {
            if (!after[i].is(before[i]) && after[i] != before[i]) state[i] = after[i]
        }
        false
    }

    @Override
    void onPostInsert(PostInsertEvent event) { call(event.entity, 'afterInsert') }

    @Override
    void onPostUpdate(PostUpdateEvent event) { call(event.entity, 'afterUpdate') }

    @Override
    void onPostDelete(PostDeleteEvent event) { call(event.entity, 'afterDelete') }

    @Override
    void onPostLoad(PostLoadEvent event) { call(event.entity, 'afterLoad') }

    @Override
    boolean requiresPostCommitHandling(EntityPersister persister) { false }

    @Override
    void onFlush(FlushEvent event) { HANDLED.remove(event.session) }

    @Override
    void onAutoFlush(AutoFlushEvent event) {
        if (event.flushRequired) HANDLED.remove(event.session)
    }

    private static final List<String> EVENT_METHODS = ['beforeInsert', 'beforeUpdate', 'afterInsert', 'afterUpdate', 'afterDelete', 'afterLoad']

    /** Os métodos de evento que cada classe tem (o {@code afterLoad} roda a cada entidade carregada). */
    private static final ClassValue<Set<String>> METHODS = new ClassValue<Set<String>>() {
        @Override
        protected Set<String> computeValue(Class<?> type) {
            EVENT_METHODS.findAll { String n -> !type.metaClass.respondsTo(type, n).isEmpty() } as Set<String>
        }
    }

    private static void call(Object entity, String name) {
        if (entity == null) return
        Class<?> type = org.grox.core.ClassUtils.userClass(entity.getClass())
        if (METHODS.get(type).contains(name) && Gorm.isDomain(type)) entity.invokeMethod(name, null)
    }
}
