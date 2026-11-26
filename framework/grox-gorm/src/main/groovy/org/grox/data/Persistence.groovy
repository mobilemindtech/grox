package org.grox.data

import org.hibernate.Session
import org.hibernate.Transaction

/**
 * Sessão Hibernate corrente via {@link ScopedValue}: uma por invocação de action (inclui a
 * renderização da view, então lazy loading funciona) e propagada às chamadas de
 * services-ator (quem chama fica bloqueado, logo a sessão nunca é usada em paralelo).
 */
class Persistence {

    /** Sessão aberta sob demanda: actions que não usam o banco não pagam a abertura. */
    static class SessionHolder {
        private Session session
        private final Closure<Session> opener
        TransactionStatus status

        SessionHolder(Session session) {
            this.session = session
            this.opener = null
        }

        SessionHolder(Closure<Session> opener) {
            this.opener = opener
        }

        Session getSession() {
            if (session == null) session = opener.call()
            session
        }

        boolean isOpened() {
            session != null
        }

        void closeIfOpened() {
            if (session?.open) session.close()
        }
    }

    static final ScopedValue<SessionHolder> CURRENT = ScopedValue.newInstance()
    static volatile Datastore datastore

    @groovy.transform.CompileStatic
    static boolean isAvailable() {
        datastore != null
    }

    static Datastore requireDatastore() {
        if (datastore == null) throw new IllegalStateException('Camada de dados não inicializada: nenhuma domain class ou dataSource configurado')
        datastore
    }

    @groovy.transform.CompileStatic
    static SessionHolder currentHolder() {
        CURRENT.bound ? CURRENT.get() : null
    }

    static Session currentSession() {
        if (!CURRENT.bound) throw new IllegalStateException('Nenhuma sessão ativa: use withSession { }')
        CURRENT.get().session
    }

    /** Executa com a sessão corrente ou abre (e fecha) uma. */
    @groovy.transform.CompileStatic
    static <T> T withSession(Closure<T> body) {
        if (CURRENT.bound) return callWith(body, CURRENT.get().session)
        withNewSession(body)
    }

    static <T> T withNewSession(Closure<T> body) {
        Session s = requireDatastore().sessionFactory.openSession()
        try {
            def holder = new SessionHolder(s)
            return org.grox.core.Scoped.where(CURRENT, holder, { -> callWith(body, s) })
        } finally {
            if (s.open) s.close()
        }
    }

    /** Sessão preguiçosa vinculada durante a action (e o BootStrap), se houver camada de dados. */
    /**
     * Executa com uma sessão própria (aberta sob demanda e fechada ao final), sem usar a
     * sessão da requisição. Usado para resolver o tenant: o resolver pode consultar o
     * banco (ex.: usuário logado) enquanto a sessão da requisição ainda está sendo aberta.
     */
    static <T> T withIsolatedSession(Closure<T> body) {
        if (!available) return body.call()
        def holder = new SessionHolder({ -> requireDatastore().sessionFactory.openSession() })
        try {
            return org.grox.core.Scoped.where(CURRENT, holder, body)
        } finally {
            holder.closeIfOpened()
        }
    }

    static <T> T withSessionIfAvailable(Closure<T> body) {
        if (!available || CURRENT.bound) return body.call()
        def holder = new SessionHolder({ -> requireDatastore().sessionFactory.openSession() })
        try {
            return org.grox.core.Scoped.where(CURRENT, holder, body)
        } finally {
            holder.closeIfOpened()
        }
    }

    /** Vincula a sessão de quem chamou (propagação para services-ator). */
    static <T> T bind(SessionHolder holder, Closure<T> body) {
        if (holder == null || (CURRENT.bound && CURRENT.get().is(holder))) return body.call()
        org.grox.core.Scoped.where(CURRENT, holder, body)
    }

    /** Participa da transação corrente ou abre uma nova (commit no fim, rollback em exceção). */
    static <T> T withTransaction(Closure<T> body) {
        withSession { Session s ->
            SessionHolder holder = CURRENT.get()
            Transaction tx = s.transaction
            if (tx.active) return callWith(body, new TransactionStatus(parent: holder.status ?: new TransactionStatus()))
            def status = new TransactionStatus(newTransaction: true)
            holder.status = status
            tx.begin()
            try {
                T result = callWith(body, status)
                if (status.rollbackOnly) tx.rollback() else tx.commit()
                return result
            } catch (Throwable t) {
                if (tx.active) tx.rollback()
                throw t
            } finally {
                holder.status = null
            }
        }
    }

    static <T> T withNewTransaction(Closure<T> body) {
        withNewSession { withTransaction(body) }
    }

    @groovy.transform.CompileStatic
    private static <T> T callWith(Closure<T> body, Object arg) {
        body.maximumNumberOfParameters == 0 ? body.call() : body.call(arg)
    }
}
