package wc3viewer.analytics

import wc3viewer.analytics.apm.ApmMetric
import wc3viewer.analytics.apm.EpmMetric
import wc3viewer.analytics.buildorder.BuildOrderMetric
import wc3viewer.analytics.economy.EconomyEstimateMetric
import wc3viewer.analytics.heroes.HeroSkillsMetric
import wc3viewer.analytics.hotkeys.HotkeyMetric
import wc3viewer.domain.metric.Metric

/** Explicit metric list: no reflection on JS (ADR 8). */
object MetricRegistry {
    val default: List<Metric<*>> = listOf(
        ApmMetric,
        EpmMetric,
        BuildOrderMetric,
        HeroSkillsMetric,
        HotkeyMetric,
        EconomyEstimateMetric,
    )
}
