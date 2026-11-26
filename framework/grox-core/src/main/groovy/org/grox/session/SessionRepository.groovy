package org.grox.session

import org.grox.http.GroxSession

/** Onde as sessões HTTP ficam guardadas ({@code grox.session.store: memory | redis}). */
interface SessionRepository extends AutoCloseable {

    String getCookieName()

    int getTimeoutSeconds()

    /** @return a sessão ou null (inexistente ou expirada) */
    GroxSession find(String id)

    /** Nova sessão; só é gravada no {@link #save}. */
    GroxSession create()

    /** Grava alterações (ou a sessão inteira, se nova) e renova a expiração. */
    void save(GroxSession session)

    void delete(String id)

    /** Troca o id da sessão (session fixation), mantendo os dados. */
    void changeId(GroxSession session)

    /** Sessões ativas (não expiradas), para telas administrativas ({@code sessionRegistry.allPrincipals}). */
    default List<GroxSession> listActive() { [] }
}
