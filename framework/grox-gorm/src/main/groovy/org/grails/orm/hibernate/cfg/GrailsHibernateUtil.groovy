package org.grails.orm.hibernate.cfg

import org.hibernate.Hibernate

/** Compatibilidade com o {@code GrailsHibernateUtil} do GORM (subconjunto). */
class GrailsHibernateUtil {

    /** A entidade real por trás de um proxy lazy do Hibernate (inicializando-o). */
    static Object unwrapIfProxy(Object instance) {
        instance == null ? null : Hibernate.unproxy(instance)
    }

    static boolean isInitialized(Object instance, String property = null) {
        property == null ? Hibernate.isInitialized(instance) : Hibernate.isPropertyInitialized(instance, property)
    }
}
