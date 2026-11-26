package org.grails.datastore.mapping.multitenancy.exceptions

class TenantNotFoundException extends RuntimeException {
    TenantNotFoundException(String message) {
        super(message)
    }
}
