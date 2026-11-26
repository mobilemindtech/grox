package org.grails.orm.hibernate

import org.grails.datastore.mapping.model.MappingContext

/**
 * Compatibilidade com o {@code HibernateDatastore} do GORM: o bean {@code hibernateDatastore}
 * ({@code org.grox.data.Datastore}) o implementa, para {@code HibernateDatastore hibernateDatastore}
 * injetado em services.
 */
interface HibernateDatastore extends org.grails.datastore.mapping.core.Datastore {
    MappingContext getMappingContext()

    org.hibernate.SessionFactory getSessionFactory()
}
