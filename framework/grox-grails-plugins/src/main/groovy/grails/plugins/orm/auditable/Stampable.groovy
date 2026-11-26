package grails.plugins.orm.auditable

/**
 * {@code Stampable} do plugin audit-logging (createdBy/lastUpdatedBy). Aceito para compilar;
 * no Grox as propriedades de trait não são persistidas, então o carimbo ainda não é gravado.
 */
trait Stampable<D> {
    Date dateCreated
    Date lastUpdated
    String createdBy = 'N/A'
    String lastUpdatedBy = 'N/A'
}
