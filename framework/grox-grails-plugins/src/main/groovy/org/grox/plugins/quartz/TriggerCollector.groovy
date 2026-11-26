package org.grox.plugins.quartz

/** Delegate de {@code static triggers} para {@link JobApi#triggers()}: guarda cada trigger como no plugin quartz. */
class TriggerCollector {
    final List<Expando> created = []

    void cron(Map args) { created << new Expando(triggerAttributes: new LinkedHashMap(args), type: 'cron') }

    void simple(Map args) { created << new Expando(triggerAttributes: new LinkedHashMap(args), type: 'simple') }

    void custom(Map args) { created << new Expando(triggerAttributes: new LinkedHashMap(args), type: 'custom') }
}
