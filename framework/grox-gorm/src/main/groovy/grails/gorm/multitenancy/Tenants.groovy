package grails.gorm.multitenancy

import org.grox.data.Persistence
import org.grox.data.tenant.TenantSupport
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException

/**
 * API de tenants do GORM: {@code Tenants.currentId()}, {@code Tenants.withId(id) { }},
 * {@code Tenants.withCurrent { }}, {@code Tenants.withoutId { }}.
 */
class Tenants {

    /** Tenant forçado por withId/withoutId (tem precedência sobre o resolver). */
    static final ScopedValue<Object> CURRENT = ScopedValue.newInstance()

    static Serializable currentId() {
        if (CURRENT.bound) {
            Object v = CURRENT.get()
            if (v.is(TenantSupport.ROOT)) throw new TenantNotFoundException('Nenhum tenant: bloco withoutId')
            return (Serializable) v
        }
        TenantSupport.resolve()
    }

    static Serializable currentIdOrNull() {
        try {
            currentId()
        } catch (TenantNotFoundException ignored) {
            null
        }
    }

    static <T> T withId(Serializable tenantId, Closure<T> body) {
        if (tenantId == null) throw new TenantNotFoundException('withId com tenant nulo')
        run(TenantSupport.convert(tenantId), body)
    }

    static <T> T withCurrent(Closure<T> body) {
        withId(currentId(), body)
    }

    /** Sem filtro de tenant (vê os dados de todos). */
    static <T> T withoutId(Closure<T> body) {
        run(TenantSupport.ROOT, body)
    }

    private static <T> T run(Object tenant, Closure<T> body) {
        org.grox.core.Scoped.where(CURRENT, tenant, { ->
            def holder = Persistence.currentHolder()
            // a sessão Hibernate fixa o tenant na abertura: se for outro (ou ainda não foi aberta,
            // o que a prenderia a este tenant), usa uma sessão própria para o bloco
            if (holder != null && Persistence.available && (!holder.opened || !TenantSupport.sessionMatches(holder.session, tenant))) {
                return Persistence.withNewSession { invokeBody(body, tenant) }
            }
            invokeBody(body, tenant)
        })
    }

    // não chamar de "call": dentro de closure resolveria para Closure.call
    private static <T> T invokeBody(Closure<T> body, Object tenant) {
        body.maximumNumberOfParameters == 0 ? body.call() : body.call(tenant.is(TenantSupport.ROOT) ? null : tenant)
    }
}
