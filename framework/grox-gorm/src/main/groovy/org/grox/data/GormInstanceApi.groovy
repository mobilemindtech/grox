package org.grox.data

import grails.validation.ValidationException
import org.grox.core.ClassUtils
import org.grox.data.tenant.TenantSupport
import org.grails.datastore.mapping.engine.event.PostDeleteEvent
import org.grails.datastore.mapping.engine.event.PostInsertEvent
import org.grails.datastore.mapping.engine.event.PostUpdateEvent
import org.grails.datastore.mapping.engine.event.PreDeleteEvent
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.hibernate.Session
import org.hibernate.engine.spi.EntityEntry
import org.hibernate.engine.spi.SessionImplementor
import org.springframework.dao.DataIntegrityViolationException

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** Métodos de instância do GORM ({@code save}, {@code delete}, {@code addTo*}...). */
class GormInstanceApi {

    static Object save(Object entity, Map opts) {
        DomainClass d = Gorm.domain(entity.getClass())
        Boolean isNew = d.composite ? null : d.identity.get(entity) == null
        Persistence.withTransaction {
            Session s = Persistence.currentSession()
            if (d.multiTenant) {
                TenantSupport.requireTenant(s)
                // tenantId primitivo (long/int) começa em 0: como no GORM, é preenchido com o tenant atual
                Object tv = d.tenantProperty.get(entity)
                boolean unset = tv == null || (d.tenantProperty.type.primitive && tv instanceof Number && ((Number) tv).longValue() == 0L)
                if (unset && d.tenantProperty.type.primitive && !TenantSupport.isRootSession(s)) {
                    d.tenantProperty.set(entity, org.grox.data.binding.DataBinder.convert(s.tenantIdentifierValue, d.tenantProperty.type))
                    unset = false
                }
                if (TenantSupport.isRootSession(s) && unset) {
                    throw new TenantNotFoundException("${d.name}: dentro de withoutId o ${d.tenantProperty.name} deve ser informado")
                }
            }
            boolean managed = s.contains(entity)
            if (d.composite && isNew == null) {
                // chave composta: nova se não está na sessão e não existe no banco (ou save(insert: true))
                isNew = !managed && (opts?.insert == true || s.find(d.type, entity) == null)
            }
            event(entity, isNew ? 'beforeInsert' : 'beforeUpdate')
            if (!GormEvents.fire(isNew ? new PreInsertEvent(entity) : new PreUpdateEvent(entity))) return null
            timestamps(d, entity, isNew)
            if (opts?.validate != false && !entity.validate()) {
                // como o GORM: a entidade inválida não pode ser gravada pelo flush/commit
                if (managed) s.setReadOnly(entity, true)
                if (opts?.failOnError) throw new ValidationException("Validação falhou para ${d.name}", entity.errors)
                return null
            }
            if (managed && s.isReadOnly(entity)) {
                // foi marcada somente leitura por uma validação que falhou: ao voltar a modificável o
                // Hibernate tira um novo snapshot e não veria as alterações; destaca e faz merge
                s.setReadOnly(entity, false)
                s.detach(entity)
                managed = false
            }
            Object stored = entity
            if (!managed) {
                try {
                    if (isNew) {
                        s.persist(entity)   // com id IDENTITY, o INSERT já sai aqui
                    } else {
                        Object merged = s.merge(entity)
                        stored = merged
                        if (!d.composite) d.identity.set(entity, d.identity.get(merged))
                        if (d.version) d.version.set(entity, d.version.get(merged))
                    }
                } catch (RuntimeException e) {
                    throw Persistence.translate(e)
                }
            }
            if (d.tenantHasOne) HasOneCascade.afterSave(s, d, entity, stored)
            if (opts?.flush) flush(s)
            GormEvents.fire(isNew ? new PostInsertEvent(entity) : new PostUpdateEvent(entity))
            entity
        }
    }

    /** Como o GORM/Spring: violação de restrição no banco vira {@link DataIntegrityViolationException}. */
    static void flush(Session s) {
        try {
            s.flush()
        } catch (RuntimeException e) {
            throw Persistence.translate(e)
        }
    }

    static void delete(Object entity, Map opts) {
        Persistence.withTransaction {
            Session s = Persistence.currentSession()
            if (Gorm.domain(entity.getClass()).multiTenant) TenantSupport.requireTenant(s)
            event(entity, 'beforeDelete')
            if (!GormEvents.fire(new PreDeleteEvent(entity))) return
            try {
                s.remove(s.contains(entity) ? entity : s.merge(entity))
            } catch (RuntimeException e) {
                throw Persistence.translate(e)
            }
            if (opts?.flush) flush(s)
            GormEvents.fire(new PostDeleteEvent(entity))
        }
    }

    /** {@code merge()}: copia o estado para a instância gerenciada e a devolve. */
    static Object merge(Object entity, Map opts) {
        Persistence.withTransaction {
            Session s = Persistence.currentSession()
            Object merged = s.merge(entity)
            if (opts?.flush) flush(s)
            merged
        }
    }

    /** {@code attach()}: reassocia uma instância destacada à sessão corrente. */
    static Object attach(Object entity) {
        Persistence.withSession { Session s ->
            if (!s.contains(entity)) {
                Object managed = s.merge(entity)
                DomainClass dc = Gorm.domain(entity.getClass())
                if (!dc.composite) dc.identity.set(entity, dc.identity.get(managed))
            }
        }
        entity
    }

    private static EntityEntry entry(Object entity) {
        Session s = Persistence.currentHolder()?.session
        if (s == null || !s.contains(entity)) return null
        s.unwrap(SessionImplementor).persistenceContextInternal.getEntry(entity)
    }

    /** Valor carregado do banco (antes das alterações em memória), ou null se não gerenciada. */
    static Object getPersistentValue(Object entity, String property) {
        EntityEntry e = entry(entity)
        if (e?.loadedState == null) return null
        int i = e.persister.propertyNames.toList().indexOf(property)
        i < 0 ? null : e.loadedState[i]
    }

    static List<String> getDirtyPropertyNames(Object entity) {
        EntityEntry e = entry(entity)
        if (e?.loadedState == null) return []
        String[] names = e.persister.propertyNames
        List<String> dirty = []
        for (int i = 0; i < names.length; i++) {
            Object current = entity[names[i]]
            if (!Objects.equals(current, e.loadedState[i])) dirty << names[i]
        }
        dirty
    }

    /** Instância nova ou destacada conta como alterada (não há como comparar). */
    static boolean isDirty(Object entity, String property = null) {
        EntityEntry e = entry(entity)
        if (e?.loadedState == null) return true
        property == null ? !getDirtyPropertyNames(entity).isEmpty() : property in getDirtyPropertyNames(entity)
    }

    static Object refresh(Object entity) {
        Persistence.withSession { Session s -> s.refresh(entity) }
        entity
    }

    static Object discard(Object entity) {
        Persistence.withSession { Session s -> s.detach(entity) }
        entity
    }

    static boolean isAttached(Object entity) {
        Persistence.currentHolder()?.session?.contains(entity) ?: false
    }

    /** {@code addToLivros(livro)} / {@code removeFromLivros(livro)} (também aceita Map). */
    static Object relation(Object entity, String method, Object[] args) {
        boolean add = method.startsWith('addTo')
        if (!(add || method.startsWith('removeFrom')) || args.length != 1) {
            throw new MissingMethodException(method, entity.getClass(), args)
        }
        DomainClass d = Gorm.domain(entity.getClass())
        String prop = method.substring(add ? 5 : 10).uncapitalize()
        DomainProperty p = d[prop]
        if (!p?.collection) throw new MissingMethodException(method, entity.getClass(), args)
        Object item = args[0] instanceof Map ? p.referencedType.newInstance((Map) args[0]) : args[0]
        Collection coll = (Collection) p.get(entity)
        if (coll == null) {
            coll = List.isAssignableFrom(p.type) ? new ArrayList() : new LinkedHashSet()
            p.set(entity, coll)
        }
        if (p.kind == DomainProperty.Kind.MANY_TO_MANY) {
            // mantém os dois lados em memória (como o GORM)
            DomainProperty other = Gorm.domain(item.getClass()).persistentProperties.values()
                    .find { it.kind == DomainProperty.Kind.MANY_TO_MANY && (it.mappedBy == p.name || p.mappedBy == it.name) }
            Collection back = other ? (Collection) other.get(item) : null
            if (other && back == null && add) {
                back = new LinkedHashSet()
                other.set(item, back)
            }
            if (add) {
                coll.add(item)
                back?.add(entity)
            } else {
                coll.remove(item)
                back?.remove(entity)
            }
            return entity
        }
        if (add) {
            coll.add(item)
            if (p.mappedBy) item[p.mappedBy] = entity
        } else {
            coll.remove(item)
            if (p.mappedBy && item[p.mappedBy].is(entity)) item[p.mappedBy] = null
        }
        entity
    }

    private static void timestamps(DomainClass d, Object entity, boolean isNew) {
        if (!d.autoTimestamp) return
        DomainProperty created = d['dateCreated']
        DomainProperty updated = d['lastUpdated']
        if (isNew && created && created.get(entity) == null) created.set(entity, now(created.type))
        if (updated) updated.set(entity, now(updated.type))
    }

    private static Object now(Class t) {
        // comparar classes com ==: 'case Date' testaria instância
        if (t == Date) return new Date()
        if (t == Instant) return Instant.now()
        if (t == LocalDateTime) return LocalDateTime.now()
        if (t == LocalDate) return LocalDate.now()
        if (t == OffsetDateTime) return OffsetDateTime.now()
        if (t == Long || t == long) return System.currentTimeMillis()
        null
    }

    private static void event(Object entity, String name) {
        if (entity.respondsTo(name)) entity.invokeMethod(name, null)
    }

    static String toString(Object entity) {
        DomainClass d = Gorm.domain(entity.getClass())
        String id = d.composite ? d.compositeProperties.collect { p -> def v = p.get(entity); p.association && v != null ? Gorm.domain(v.getClass()).identity?.get(v) : v }.join(', ') : String.valueOf(d.identity.get(entity))
        "${ClassUtils.userClass(entity.getClass()).name} : ${id}"
    }
}
