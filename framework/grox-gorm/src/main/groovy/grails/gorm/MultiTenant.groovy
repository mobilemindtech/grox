package grails.gorm

/**
 * Domain class multi-tenant (modo DISCRIMINATOR): a propriedade {@code tenantId} (ou a de
 * {@code mapping { tenantId name: 'x' }}) é preenchida no insert e filtra todas as consultas.
 */
trait MultiTenant<D> {
}
