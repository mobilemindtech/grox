package org.grox

import grails.async.Promise
import org.grox.security.Authentication
import org.grox.security.GroxUser
import org.grox.security.SecurityContextHolder
import org.junit.jupiter.api.Test

import java.util.concurrent.TimeUnit

import static grails.async.Promises.*
import static org.junit.jupiter.api.Assertions.assertThrows

class PromisesTest {

    @Test
    void 'task, tasks em lista e em mapa, then, waitAll e erros'() {
        assert task { 21 * 2 }.get() == 42
        assert tasks({ 1 }, { 2 }, { 3 }).get() == [1, 2, 3]
        assert tasks(a: { 'x' }, b: { Thread.currentThread().virtual }).get() == [a: 'x', b: true]
        assert task { 10 }.then { it + 1 }.then { it * 2 }.get(1, TimeUnit.SECONDS) == 22
        assert waitAll(task { 'a' }, createBoundPromise('b')) == ['a', 'b']
        def falha = task { throw new IllegalArgumentException('ruim') }
        def e = assertThrows(IllegalArgumentException) { falha.get() }
        assert e.message == 'ruim'
        Throwable capturada = null
        falha.onError { capturada = it }
        Thread.sleep(50)
        assert capturada instanceof IllegalArgumentException
    }

    @Test
    void 'contexto de seguranca propagado para a tarefa e para o then'() {
        def u = new GroxUser(username: 'ana', tenantId: 'acme')
        Promise p = SecurityContextHolder.with(new Authentication(principal: u, method: 'form')) {
            task { SecurityContextHolder.authentication?.name }.then { String n -> n + '/' + SecurityContextHolder.authentication?.principal?.tenantId }
        }
        assert p.get() == 'ana/acme'
        assert task { SecurityContextHolder.authentication }.get() == null
    }
}
