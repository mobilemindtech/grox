package org.grox

import org.grox.i18n.MessageSource
import org.junit.jupiter.api.Test

/** Mensagens por locale e os caches limitados aos idiomas que existem. */
class MessageSourceTest {

    @Test
    void 'locale efetivo: o mais especifico com mensagens, senao o padrao'() {
        def ms = new MessageSource(MessageSourceTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        assert ms.effective(null) == Locale.forLanguageTag('pt-BR')
        assert ms.effective(Locale.forLanguageTag('en-GB')) == Locale.of('en')           // há messages_en
        assert ms.effective(Locale.forLanguageTag('pt-x-abc')) == Locale.of('pt')        // extensões não contam
        assert ms.effective(Locale.forLanguageTag('zz-QQ')) == Locale.forLanguageTag('pt-BR')
        assert ms.getMessage('default.blank.message', ['nome', 'Livro'], null, Locale.forLanguageTag('en-GB')) ==
                'Property [nome] of class [Livro] cannot be blank'
    }

    @Test
    void 'locales inventados pelo cliente nao fazem os caches crescer'() {
        def ms = new MessageSource(MessageSourceTest.classLoader, 'messages', Locale.forLanguageTag('pt-BR'))
        // como ?lang=xx-<aleatório> ou Accept-Language variando a cada requisição
        5000.times { int i ->
            ms.getMessage('default.blank.message', ['nome', 'Livro'], null, Locale.of("q${i % 900}", "C${i}"))
            ms.getMessage('default.blank.message', ['nome', 'Livro'], null, Locale.forLanguageTag("pt-x-r${i}"))
        }
        // antes: um mapa por locale pedido (200 mil locales distintos davam +180 MB)
        assert ((Map) ms.@patternCache).size() <= 3
        assert ((Map) ms.@formatCache).size() <= 3
        assert ((Map) ms.@suffixCache).size() <= 3
        assert ((Map) ms.@bundles).size() <= 10
        assert ((Map) ms.@available).size() <= MessageSource.MAX_MISSING_SUFFIXES + 10
    }
}
