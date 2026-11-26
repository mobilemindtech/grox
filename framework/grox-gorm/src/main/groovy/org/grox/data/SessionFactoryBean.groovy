package org.grox.data

import org.hibernate.Session
import org.hibernate.SessionFactory

/**
 * Bean {@code sessionFactory}: {@code currentSession} é a sessão da requisição/transação
 * corrente do Grox; os demais métodos vão para o {@link SessionFactory} do Hibernate.
 */
class SessionFactoryBean {
    private final Datastore datastore

    SessionFactoryBean(Datastore datastore) { this.datastore = datastore }

    Session getCurrentSession() { Persistence.currentSession() }

    SessionFactory getNativeSessionFactory() { datastore.sessionFactory }

    def methodMissing(String name, Object args) {
        datastore.sessionFactory.invokeMethod(name, args)
    }

    def propertyMissing(String name) {
        datastore.sessionFactory[name]
    }
}
