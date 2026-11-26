package grails.artefact

/**
 * Compatibilidade com o trait {@code grails.artefact.Controller}: traits da aplicação que o estendem
 * ({@code trait WithClient extends Controller}) enxergam {@code params}, {@code flash}, {@code redirect}...
 */
trait Controller extends org.grox.web.api.ControllerApi {
}
