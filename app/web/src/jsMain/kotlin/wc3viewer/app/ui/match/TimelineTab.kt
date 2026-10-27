package wc3viewer.app.ui.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wc3viewer.app.ui.charts.ChartLayout
import wc3viewer.app.ui.charts.ChartLegend
import wc3viewer.app.ui.charts.TimeCursor
import wc3viewer.app.ui.charts.TimeSeriesChart
import wc3viewer.app.ui.charts.TimelineChart
import wc3viewer.app.ui.charts.TimelineEvent
import wc3viewer.app.ui.charts.TimelineLane
import wc3viewer.app.ui.charts.TimelineRows
import wc3viewer.app.ui.charts.rowLabel
import wc3viewer.app.ui.components.FilterChip
import wc3viewer.app.ui.components.Panel
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.app.ui.theme.kindColor
import wc3viewer.presentation.ObjectKind

fun timelineLanes(data: MatchData): List<TimelineLane> = data.players.map { p ->
    val events = ArrayList<TimelineEvent>()
    for (r in data.sections.buildOrders[p.id].orEmpty()) {
        if (r.kindKey == ObjectKind.CANCEL) continue
        events += TimelineEvent(r.timeSec, r.kindKey, r.code, if (r.hasName) r.name else null)
    }
    for (h in data.sections.heroes[p.id].orEmpty()) {
        for (s in h.skills) events += TimelineEvent(s.timeSec, ObjectKind.SKILL, s.code, s.name, "${h.hero} lv ${s.level}")
    }
    TimelineLane(p.name, data.color(p), events.sortedBy { it.sec })
}

@Composable
fun TimelineTab(data: MatchData, cursor: TimeCursor, modifier: Modifier) {
    val lanes = remember(data) { timelineLanes(data) }
    var visible by remember { mutableStateOf(TimelineRows.toSet()) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.l)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Txt("Show", Type.caption)
            for (k in TimelineRows) {
                val count = lanes.sumOf { l -> l.events.count { it.kind == k } }
                FilterChip(rowLabel(k), k in visible, { visible = if (k in visible) visible - k else visible + k }, kindColor(k), count)
            }
        }
        Panel(
            title = "Events",
            subtitle = "Order times: when the command was given, not when the object appeared",
            trailing = { ChartLegend(data.players.map { data.color(it) to it.name }) },
        ) {
            TimelineChart(lanes, data.maxSec, cursor, visible)
        }
        Panel(title = "APM", subtitle = "Same time axis · 60 s window") {
            TimeSeriesChart(apmSeries(data), data.maxSec, cursor, Modifier.fillMaxWidth().height(170.dp), leftGutter = ChartLayout.timelineLeft)
        }
    }
}
