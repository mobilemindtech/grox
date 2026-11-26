package org.grox.data.tenant

import grails.gorm.multitenancy.Tenants
import org.grox.data.Persistence
import org.grox.data.binding.DataBinder
import org.grails.datastore.mapping.multitenancy.TenantResolver
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.hibernate.Session
import org.hibernate.context.spi.CurrentTenantIdentifierResolver

/**
 * Ponte entre a API de tenants do GORM e o multi-tenancy nativo do Hibernate (@TenantId).
 * O Hibernate exige um identificador em toda sessão: sem tenant usa-se um valor sentinela
 * que não casa com nenhum registro, e as APIs do GORM lançam TenantNotFoundException.
 */
class TenantSupport {

    static final Object ROOT = new Object() {
        String toString() { 'ROOT' }
    }

    private static final ThreadLocal<Boolean> RESOLVING = ThreadLocal.withInitial { false }

    static volatile TenantResolver resolver
    static volatile Class<?> tenantType = String

    static void configure(TenantResolver r, Class<?> type) {
        resolver = r
        tenantType = type ?: String
    }

    static void reset() {
        resolver = null
        tenantType = String
    }

    static Serializable resolve() {
        if (resolver == null) throw new TenantNotFoundException('Multi-tenancy não configurado (grails.gorm.multiTenancy)')
        convert(resolver.resolveTenantIdentifier())
    }

    static Serializable convert(Object id) {
        if (id == null || tenantType.isInstance(id)) return (Serializable) id
        Object v = DataBinder.convert(id.toString(), tenantType)
        if (v == null) throw new TenantNotFoundException("tenant inválido para ${tenantType.simpleName}: ${id}")
        (Serializable) v
    }

    static Object rootValue() {
        tenantType == String ? '__grox_root__' : DataBinder.convert('-1', tenantType)
    }

    static Object noTenantValue() {
        tenantType == String ? '__grox_no_tenant__' : DataBinder.convert('-2', tenantType)
    }

    /** Valor que o Hibernate recebe para o contexto atual. */
    static Object hibernateTenant() {
        if (Tenants.CURRENT.bound) {
            Object v = Tenants.CURRENT.get()
            return v.is(ROOT) ? rootValue() : v
        }
        if (resolver == null) return noTenantValue()
        // caminho comum sem exceções (abertura de sessão acontece a cada requisição)
        if (resolver instanceof OptionalTenantResolver) {
            Serializable t = ((OptionalTenantResolver) resolver).resolveTenantIdentifierOrNull()
            return t == null ? noTenantValue() : convert(t)
        }
        // o resolver da app pode consultar o banco (ex.: springSecurityService.currentUser): roda numa
        // sessão isolada, e a sessão aberta lá dentro (reentrância) não tem tenant
        if (RESOLVING.get()) return noTenantValue()
        RESOLVING.set(true)
        try {
            // resolvers da app podem devolver null em vez de lançar (ex.: sem usuário logado)
            Persistence.withIsolatedSession { resolve() } ?: noTenantValue()
        } catch (TenantNotFoundException ignored) {
            noTenantValue()
        } finally {
            RESOLVING.remove()
        }
    }

    static boolean sessionMatches(Session session, Object tenant) {
        Object current = session.tenantIdentifierValue
        Object wanted = tenant.is(ROOT) ? rootValue() : tenant
        current == wanted
    }

    /** Chamado pelas APIs do GORM de classes multi-tenant. */
    static void requireTenant(Session session) {
        Object t = session.tenantIdentifierValue
        if (t == noTenantValue()) {
            // relança com a causa (o resolver explica por que não há tenant)
            if (!Tenants.CURRENT.bound && resolve() == null) throw new TenantNotFoundException('O tenant resolver não identificou o tenant (null)')
            throw new TenantNotFoundException('Nenhum tenant resolvido para a sessão atual')
        }
    }

    static boolean isRootSession(Session session) {
        session.tenantIdentifierValue == rootValue()
    }

    static class HibernateResolver implements CurrentTenantIdentifierResolver<Object> {
        // qualificado: numa classe aninhada estática, a chamada sem classe passa por
        // MissingMethodException antes de achar o método da classe externa
        @Override
        Object resolveCurrentTenantIdentifier() {
            TenantSupport.hibernateTenant()
        }

        @Override
        boolean validateExistingCurrentSessions() {
            false
        }

        @Override
        boolean isRoot(Object tenantId) {
            tenantId == TenantSupport.rootValue()
        }
    }
}
