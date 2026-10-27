package wc3viewer.app.ui.match

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wc3viewer.app.ui.components.EmptyState
import wc3viewer.app.ui.components.EstimateBadge
import wc3viewer.app.ui.components.HDivider
import wc3viewer.app.ui.components.ObjectIcon
import wc3viewer.app.ui.components.ObjectLabel
import wc3viewer.app.ui.components.Panel
import wc3viewer.app.ui.components.PlayerSwatch
import wc3viewer.app.ui.components.RaceBadge
import wc3viewer.app.ui.components.SectionLabel
import wc3viewer.app.ui.components.Txt
import wc3viewer.app.ui.theme.Palette
import wc3viewer.app.ui.theme.Shapes
import wc3viewer.app.ui.theme.Space
import wc3viewer.app.ui.theme.Type
import wc3viewer.presentation.HeroUi
import wc3viewer.presentation.ObjectKind
import wc3viewer.presentation.PlayerUi

@Composable
fun HeroesTab(data: MatchData, wide: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            EstimateBadge()
            Txt(
                "Level is a lower bound: one level per learned skill. A player may keep a skill point unspent, " +
                    "and a failed learn order looks the same in the replay.",
                Type.caption,
            )
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                for (p in data.players) PlayerHeroes(data, p, Modifier.weight(1f))
            }
        } else {
            for (p in data.players) PlayerHeroes(data, p, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerHeroes(data: MatchData, p: PlayerUi, modifier: Modifier) {
    val heroes = data.sections.heroes[p.id].orEmpty()
    val orders = data.sections.heroOrders[p.id].orEmpty()
    Panel(
        modifier,
        title = p.name,
        trailing = { RaceBadge(p.raceKey, p.race); PlayerSwatch(data.color(p)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
            if (heroes.isEmpty()) EmptyState("No heroes", "No skill was learned in this game.")
            for (h in heroes) HeroCard(h)
            if (orders.isNotEmpty()) {
                HDivider()
                SectionLabel("Hire and train orders")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    for (o in orders) {
                        Row(
                            Modifier.background(Palette.raised, Shapes.s).padding(horizontal = 8.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt(o.time, Type.numSmall)
                            ObjectLabel(o.code, o.name)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(h: HeroUi) {
    Column(
        Modifier.fillMaxWidth().background(Palette.raised, Shapes.m).border(1.dp, Palette.border, Shapes.m).padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            ObjectIcon(h.heroCode ?: "????", ObjectKind.HERO, if (h.hasName) h.hero else null, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HeroName(h, Type.heading)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Meta("Hired", h.hiredAt ?: "—")
                    Meta("First skill", h.firstSkill)
                    if (h.retrainedAt != null) Meta("Retrained", h.retrainedAt!!)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("Lv", Type.caption)
                    Txt("≥ ${h.levelEstimate}", Type.numLarge)
                }
                EstimateBadge()
            }
        }
        if (h.skills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (s in h.skills) SkillChip(s.level, s.code, s.name, s.time)
            }
        }
    }
}

/** Hero name; the fourcc in monospace only when the name is unknown. */
@Composable
fun HeroName(h: HeroUi, style: androidx.compose.ui.text.TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (h.hasName) {
            Txt(h.hero, style, maxLines = 1)
        } else {
            Txt(h.hero, Type.mono.copy(color = Palette.textPrimary, fontSize = style.fontSize), maxLines = 1)
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Txt(label, Type.caption)
        Txt(value, Type.numSmall.copy(color = Palette.textPrimary))
    }
}

@Composable
private fun SkillChip(level: Int, code: String, name: String?, time: String) {
    Row(
        Modifier.background(Palette.surface, Shapes.s).border(1.dp, Palette.border, Shapes.s).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(22.dp).background(Palette.overlay, Shapes.s).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Txt("$level", Type.numSmall.copy(color = Palette.textPrimary))
        }
        ObjectIcon(code, ObjectKind.SKILL, name, size = 18.dp)
        if (name != null) {
            Txt(name, Type.small.copy(color = Palette.textPrimary), maxLines = 1)
        } else {
            Txt(code, Type.mono.copy(color = Palette.textPrimary, fontSize = Type.caption.fontSize))
        }
        Txt(time, Type.numSmall.copy(color = Palette.textMuted))
    }
}
