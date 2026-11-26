package sample

/**
 * Service-ator: as chamadas são serializadas pelo ator, então o estado (um HashMap comum)
 * dispensa sincronização. A sessão de dados de quem chama é propagada para o ator.
 */
class EstatisticaService {

    static actor = true

    private final Map<String, Integer> eventos = [:]

    void registrar(String evento) {
        eventos[evento] = (eventos[evento] ?: 0) + 1
        log.debug("evento ${evento}: ${eventos[evento]}")
    }

    Map resumo() {
        [usuarios: Usuario.count(), grupos: Grupo.count(), eventos: new LinkedHashMap(eventos)]
    }
}
