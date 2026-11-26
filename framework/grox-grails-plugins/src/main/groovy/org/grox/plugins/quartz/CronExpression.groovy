package org.grox.plugins.quartz

import groovy.transform.CompileStatic

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Expressão cron no formato do Quartz: {@code seg min hora dia-do-mês mês dia-da-semana [ano]}.
 * Suporta {@code *}, {@code ?}, valores, listas ({@code 7,8,10}), intervalos ({@code MON-FRI}),
 * passos ({@code 0/15}, {@code *}{@code /5}) e nomes de mês/dia. Não suporta {@code L}, {@code W} e {@code #}.
 * Dia da semana como no Quartz: 1 = domingo ... 7 = sábado.
 */
@CompileStatic
class CronExpression {

    private static final List<String> MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC']
    private static final List<String> DAYS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']

    final String expression
    private final BitSet seconds, minutes, hours, daysOfMonth, months, daysOfWeek, years
    private final boolean anyDayOfMonth, anyDayOfWeek

    CronExpression(String expression) {
        this.expression = expression
        String[] f = expression.trim().split(/\s+/)
        if (f.length < 6 || f.length > 7) throw new IllegalArgumentException("Expressão cron inválida (6 ou 7 campos): '$expression'")
        seconds = parse(f[0], 0, 59, null)
        minutes = parse(f[1], 0, 59, null)
        hours = parse(f[2], 0, 23, null)
        anyDayOfMonth = f[3] == '?' || f[3] == '*'
        daysOfMonth = parse(f[3], 1, 31, null)
        months = parse(f[4], 1, 12, MONTHS)
        anyDayOfWeek = f[5] == '?' || f[5] == '*'
        daysOfWeek = parse(f[5], 1, 7, DAYS)
        years = f.length == 7 ? parse(f[6], 1970, 2199, null) : null
    }

    private BitSet parse(String field, int min, int max, List<String> names) {
        BitSet bits = new BitSet(max + 1)
        for (String part : field.toUpperCase().split(',')) {
            if (part.contains('#') || part ==~ /(\d*L|\d+W|LW|L-\d+)/) throw new IllegalArgumentException("Cron: '$part' não suportado em '$expression'")
            int step = 1
            String range = part
            int slash = part.indexOf('/')
            if (slash >= 0) {
                step = Integer.parseInt(part.substring(slash + 1))
                range = part.substring(0, slash)
            }
            int from, to
            if (range == '*' || range == '?') {
                from = min; to = max
            } else if (range.contains('-')) {
                String[] r = range.split('-')
                from = value(r[0], names, min); to = value(r[1], names, min)
            } else {
                from = value(range, names, min)
                to = slash >= 0 ? max : from
            }
            if (from < min || to > max || from > to) throw new IllegalArgumentException("Cron: '$part' fora de $min-$max em '$expression'")
            for (int i = from; i <= to; i += step) bits.set(i)
        }
        bits
    }

    private static int value(String s, List<String> names, int min) {
        if (names != null && names.contains(s)) return names.indexOf(s) + min
        Integer.parseInt(s)
    }

    /** Próximo instante estritamente depois de {@code after} que satisfaz a expressão. */
    ZonedDateTime nextAfter(ZonedDateTime after) {
        ZonedDateTime t = after.truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)
        ZonedDateTime limit = after.plusYears(5)
        while (t.isBefore(limit)) {
            if (years != null && !years.get(t.year)) {
                t = t.plusYears(1).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS); continue
            }
            if (!months.get(t.monthValue)) {
                t = t.plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS); continue
            }
            if (!dayMatches(t)) {
                t = t.plusDays(1).truncatedTo(ChronoUnit.DAYS); continue
            }
            if (!hours.get(t.hour)) {
                t = t.plusHours(1).truncatedTo(ChronoUnit.HOURS); continue
            }
            if (!minutes.get(t.minute)) {
                t = t.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES); continue
            }
            if (!seconds.get(t.second)) {
                t = t.plusSeconds(1); continue
            }
            return t
        }
        null
    }

    Date nextAfter(Date after, ZoneId zone = ZoneId.systemDefault()) {
        ZonedDateTime n = nextAfter(after.toInstant().atZone(zone))
        n == null ? null : Date.from(n.toInstant())
    }

    private boolean dayMatches(ZonedDateTime t) {
        int dow = t.dayOfWeek == DayOfWeek.SUNDAY ? 1 : t.dayOfWeek.value + 1
        boolean dom = daysOfMonth.get(t.dayOfMonth)
        boolean dw = daysOfWeek.get(dow)
        if (anyDayOfMonth && anyDayOfWeek) return true
        if (anyDayOfMonth) return dw
        if (anyDayOfWeek) return dom
        dom && dw
    }

    @Override
    String toString() { expression }
}
