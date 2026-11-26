package org.grox

import org.grox.http.GroxParams
import org.grox.data.binding.DataBinder
import org.grox.web.Formats
import org.junit.jupiter.api.Test

import java.time.LocalDate

class DataBindingTest {

    static class ComData {
        Date nascimento
    }

    /** Como um command object: guarda os erros de binding. */
    static class Agenda implements org.grox.data.validation.ValidationSupport {
        Date inicio
        LocalDate dia
        java.time.LocalDateTime hora
    }

    @Test
    void 'datePicker: partes validas viram data, invalidas viram typeMismatch sem excecao'() {
        def ok = DataBinder.bind(new Agenda(), [inicio: 'date.struct', inicio_year: '2026', inicio_month: '2', inicio_day: '28',
                                                dia_year: '2026', dia_month: '12', dia_day: '25',
                                                hora_year: '2026', hora_month: '1', hora_day: '2', hora_hour: '10', hora_minute: '30'])
        assert new java.text.SimpleDateFormat('yyyy-MM-dd').format(ok.inicio) == '2026-02-28'
        assert ok.dia == LocalDate.of(2026, 12, 25) && ok.hora == java.time.LocalDateTime.of(2026, 1, 2, 10, 30)
        assert !ok.hasErrors()
        // antes: NumberFormatException e DateTimeException (500); o Date aceitava 31/02 e virava 03/03
        def ruim = DataBinder.bind(new Agenda(), [inicio_year: '20x4', inicio_month: '1', inicio_day: '1',
                                                  dia_year: '2026', dia_month: '13', dia_day: '1',
                                                  hora_year: '2026', hora_month: '2', hora_day: '31', hora_hour: '0', hora_minute: '0'])
        assert ruim.inicio == null && ruim.dia == null && ruim.hora == null
        assert ruim.errors.getFieldError('inicio').codes.contains('typeMismatch') && ruim.errors.getFieldError('inicio').rejectedValue == '20x4-1-1'
        assert ruim.errors.getFieldError('dia') && ruim.errors.getFieldError('hora')
        // sem o ano: o campo fica vazio, sem erro
        assert DataBinder.bind(new Agenda(), [inicio: 'date.struct', inicio_month: '1']).with { inicio == null && !hasErrors() }
    }

    @Test
    void 'grails databinding dateFormats define os formatos de Date aceitos no binding'() {
        def iso = new java.text.SimpleDateFormat('yyyy-MM-dd')
        assert DataBinder.mismatch('25/12/2026', Date)
        DataBinder.dateFormats = ['dd/MM/yyyy HH:mm', 'dd/MM/yyyy']
        try {
            def alvo = DataBinder.bind(new ComData(), [nascimento: '25/12/2026'])
            assert iso.format(alvo.nascimento) == '2026-12-25'
            assert !DataBinder.mismatch('25/12/2026', Date)
            assert new java.text.SimpleDateFormat('yyyy-MM-dd HH:mm').format(DataBinder.bindValue('25/12/2026 10:30', Date)) == '2026-12-25 10:30'
            // os formatos padrão continuam valendo
            assert iso.format(DataBinder.bindValue('2026-01-02', Date)) == '2026-01-02'
            assert DataBinder.mismatch('31/02/2026', Date)
        } finally {
            DataBinder.dateFormats = []
        }
    }

    @Test
    void 'PropertyEditorRegistrar da aplicacao define o formato de Date no binding'() {
        def registrar = { registry ->
            registry.registerCustomEditor(Date, new org.grails.web.binding.StructuredDateEditor(new java.text.SimpleDateFormat('dd/MM/yyyy'), true))
        } as org.springframework.beans.PropertyEditorRegistrar
        org.grox.web.CustomEditors.registrars = [registrar]
        def anterior = org.grox.core.Conversions.editors
        org.grox.core.Conversions.editors = org.grox.web.CustomEditors.LOOKUP   // como o GrailsModule faz na partida
        try {
            def iso = new java.text.SimpleDateFormat('yyyy-MM-dd')
            assert iso.format(DataBinder.bindValue('25/12/2026', Date)) == '2026-12-25'
            assert iso.format(DataBinder.bindValue('2026-01-02', Date)) == '2026-01-02'   // não aceito pelo editor: padrão
            assert DataBinder.bindValue('', Date) == null
            assert DataBinder.bindValue('7', Integer) == 7
            // conversões internas do framework (tenant, ids) não usam os editores da app
            assert DataBinder.convert('25/12/2026', Date) == null
        } finally {
            org.grox.core.Conversions.editors = anterior
            org.grox.web.CustomEditors.registrars = []
        }
    }


    static class Endereco {
        String cidade
    }

    static class Pessoa {
        Long id
        String nome
        int idade
        Boolean ativo
        LocalDate nascimento
        List<String> tags
        Endereco endereco
    }

    @Test
    void 'conversao de tipos simples'() {
        assert DataBinder.convert('42', Integer) == 42
        assert DataBinder.convert('42', long) == 42L
        assert DataBinder.convert('abc', Integer) == null
        assert DataBinder.convert('abc', int) == 0
        assert DataBinder.convert('', Long) == null
        assert DataBinder.convert(['7', '8'], Integer) == 7
        assert DataBinder.convert('7', List) == ['7']
        assert DataBinder.convert('on', Boolean)
        assert DataBinder.convert('2026-09-28', LocalDate) == LocalDate.of(2026, 9, 28)
        assert DataBinder.convert('1.5', BigDecimal) == 1.5G
    }

    static class Conta {
        String nome
        String tenant
        Boolean ativa
        static constraints = {
            tenant bindable: false
            ativa bindable: false, nullable: true
        }
    }

    @Test
    void 'bindable false nao vincula, a menos de include explicito'() {
        def c = (Conta) DataBinder.bind(new Conta(), [nome: 'Ana', tenant: 'outro', ativa: 'true'])
        assert c.nome == 'Ana' && c.tenant == null && c.ativa == null
        def admin = (Conta) DataBinder.bind(new Conta(), [nome: 'Ana', tenant: 'acme'], [include: ['nome', 'tenant']])
        assert admin.tenant == 'acme'
    }

    @Test
    void 'bind de params com chaves aninhadas e include-exclude'() {
        def params = new GroxParams()
        params.putAll([id: '9', nome: 'Ana', idade: '30', ativo: 'true', nascimento: '1996-01-02',
                       tags: ['a', 'b'], 'endereco.cidade': 'Recife'])
        def p = (Pessoa) DataBinder.bind(new Pessoa(), params, [exclude: ['id']])
        assert p.id == null
        assert p.nome == 'Ana' && p.idade == 30 && p.ativo
        assert p.nascimento == LocalDate.of(1996, 1, 2)
        assert p.tags == ['a', 'b']
        assert p.endereco.cidade == 'Recife'

        def q = (Pessoa) DataBinder.bind(new Pessoa(), [nome: 'Bia', endereco: [cidade: 'Natal']], [include: ['nome']])
        assert q.nome == 'Bia' && q.endereco == null
    }

    @Test
    void 'params tipados'() {
        def params = new GroxParams()
        params.putAll([max: '10', ativo: 'off', ids: ['1', '2'], 'livro.titulo': 'X'])
        assert params.int('max') == 10
        assert params.int('nada', 5) == 5
        assert params.long('max') == 10L
        assert params.boolean('ativo') == false
        assert params.list('ids') == ['1', '2']
        assert params.list('max') == ['10']
        assert params.livro.titulo == 'X'
    }

    @Test
    void 'negociacao pelo Accept'() {
        assert Formats.fromAccept(null) == 'all'
        assert Formats.fromAccept('*/*') == 'all'
        assert Formats.fromAccept('application/json') == 'json'
        assert Formats.fromAccept('text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8') == 'html'
        assert Formats.fromAccept('text/html;q=0.5, application/json') == 'json'
        assert Formats.fromAccept('application/vnd.api+json') == 'json'
        // q inválido vem do cliente: conta como 0, sem exceção (antes: NumberFormatException e 500)
        assert Formats.fromAccept('text/html;q=x') == 'html'
        assert Formats.fromAccept('text/html;q=abc, application/json') == 'json'
        assert Formats.fromAccept('text/html;q=1e9, application/json;q=0.1') == 'json'
        assert Formats.fromAccept('text/html;q=, application/json;q=0.2') == 'html'
    }

    @Test
    void 'formato da requisicao pelo Content-Type, usado no request withFormat'() {
        assert Formats.fromContentType(null) == 'all'
        assert Formats.fromContentType('application/x-www-form-urlencoded; charset=UTF-8') == 'form'
        assert Formats.fromContentType('multipart/form-data; boundary=x') == 'multipartForm'
        assert Formats.fromContentType('application/json') == 'json'
        assert Formats.fromContentType('application/vnd.api+json') == 'json'
        assert Formats.fromContentType('application/octet-stream') == 'all'
    }

    @Test
    void 'JSON dos erros de validacao: mensagem pelo resolver dado, senao a padrao'() {
        def errors = new org.grox.data.validation.ValidationErrors('Livro')
        errors.rejectValue('titulo', 'nullable', null, 'Título obrigatório', null)
        assert org.grox.data.DomainJson.errors(errors).errors == [[object: 'Livro', field: 'titulo', 'rejected-value': null, message: 'Título obrigatório']]
        def traduzido = { fe -> "erro em ${fe.field}".toString() } as java.util.function.Function
        assert org.grox.data.DomainJson.errors(errors, traduzido).errors*.message == ['erro em titulo']
        def semTexto = { fe -> null } as java.util.function.Function
        assert org.grox.data.DomainJson.errors(errors, semTexto).errors*.message == ['Título obrigatório']
    }
}
