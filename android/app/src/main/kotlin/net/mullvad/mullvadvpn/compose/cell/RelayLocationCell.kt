package net.mullvad.mullvadvpn.compose.cell

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import net.mullvad.mullvadvpn.R
import net.mullvad.mullvadvpn.compose.component.Chevron
import net.mullvad.mullvadvpn.lib.theme.AppTheme
import net.mullvad.mullvadvpn.lib.theme.Dimens
import net.mullvad.mullvadvpn.lib.theme.color.Alpha40
import net.mullvad.mullvadvpn.lib.theme.color.AlphaInactive
import net.mullvad.mullvadvpn.lib.theme.color.AlphaInvisible
import net.mullvad.mullvadvpn.lib.theme.color.AlphaVisible
import net.mullvad.mullvadvpn.lib.theme.color.selected
import net.mullvad.mullvadvpn.model.GeographicLocationConstraint
import net.mullvad.mullvadvpn.relaylist.RelayItem

@Composable
@Preview
private fun PreviewRelayLocationCell() {
    AppTheme {
        Column(Modifier.background(color = MaterialTheme.colorScheme.background)) {
            val countryActive =
                RelayItem.Country(
                    name = "Relay country Active",
                    code = "RC1",
                    expanded = false,
                    cities =
                        listOf(
                            RelayItem.City(
                                name = "Relay city 1",
                                code = "RI1",
                                expanded = false,
                                location = GeographicLocationConstraint.City("RC1", "RI1"),
                                relays =
                                    listOf(
                                        RelayItem.Relay(
                                            name = "Relay 1",
                                            active = true,
                                            locationName = "",
                                            location =
                                                GeographicLocationConstraint.Hostname(
                                                    "RC1",
                                                    "RI1",
                                                    "NER"
                                                )
                                        )
                                    )
                            ),
                            RelayItem.City(
                                name = "Relay city 2",
                                code = "RI2",
                                expanded = true,
                                location = GeographicLocationConstraint.City("RC1", "RI2"),
                                relays =
                                    listOf(
                                        RelayItem.Relay(
                                            name = "Relay 2",
                                            active = true,
                                            locationName = "",
                                            location =
                                                GeographicLocationConstraint.Hostname(
                                                    "RC1",
                                                    "RI2",
                                                    "NER"
                                                )
                                        ),
                                        RelayItem.Relay(
                                            name = "Relay 3",
                                            active = true,
                                            locationName = "",
                                            location =
                                                GeographicLocationConstraint.Hostname(
                                                    "RC1",
                                                    "RI1",
                                                    "NER"
                                                )
                                        )
                                    )
                            )
                        )
                )
            val countryNotActive =
                RelayItem.Country(
                    name = "Not Enabled Relay country",
                    code = "RC3",
                    expanded = true,
                    cities =
                        listOf(
                            RelayItem.City(
                                name = "Not Enabled city",
                                code = "RI3",
                                expanded = true,
                                location = GeographicLocationConstraint.City("RC3", "RI3"),
                                relays =
                                    listOf(
                                        RelayItem.Relay(
                                            name = "Not Enabled Relay",
                                            active = false,
                                            locationName = "",
                                            location =
                                                GeographicLocationConstraint.Hostname(
                                                    "RC3",
                                                    "RI3",
                                                    "NER"
                                                )
                                        )
                                    )
                            )
                        )
                )
            // Active relay list not expanded
            RelayLocationCell(countryActive)
            // Not Active Relay
            RelayLocationCell(countryNotActive)
            // Relay expanded country and city
            RelayLocationCell(countryActive.copy(expanded = true))
        }
    }
}

@Composable
fun RelayLocationCell(
    relay: RelayItem,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.selected,
    inactiveColor: Color = MaterialTheme.colorScheme.error,
    selectedItem: RelayItem? = null,
    onSelectRelay: (item: RelayItem) -> Unit = {}
) {
    val startPadding =
        when (relay) {
            is RelayItem.Country,
            is RelayItem.CustomList -> Dimens.countryRowPadding
            is RelayItem.City -> Dimens.cityRowPadding
            is RelayItem.Relay -> Dimens.relayRowPadding
        }
    val selected = selectedItem?.code == relay.code
    val expanded =
        rememberSaveable(key = relay.expanded.toString()) { mutableStateOf(relay.expanded) }
    val backgroundColor =
        when {
            selected -> MaterialTheme.colorScheme.inversePrimary
            relay is RelayItem.Country -> MaterialTheme.colorScheme.primary
            relay is RelayItem.City ->
                MaterialTheme.colorScheme.primary
                    .copy(alpha = Alpha40)
                    .compositeOver(MaterialTheme.colorScheme.background)
            relay is RelayItem.Relay -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.primary
        }
    Column(
        modifier =
            modifier.then(
                Modifier.fillMaxWidth()
                    .padding(top = Dimens.listItemDivider)
                    .wrapContentHeight()
                    .fillMaxWidth()
            )
    ) {
        Row(
            modifier =
                Modifier.align(Alignment.Start)
                    .wrapContentHeight()
                    .height(IntrinsicSize.Min)
                    .fillMaxWidth()
                    .background(backgroundColor)
        ) {
            Row(
                modifier =
                    Modifier.weight(1f)
                        .then(
                            if (relay.active) {
                                Modifier.clickable { onSelectRelay(relay) }
                            } else {
                                Modifier
                            }
                        )
            ) {
                Box(
                    modifier =
                        Modifier.align(Alignment.CenterVertically).padding(start = startPadding)
                ) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.CenterStart)
                                .size(Dimens.relayCircleSize)
                                .background(
                                    color =
                                        when {
                                            selected -> Color.Transparent
                                            relay.active -> activeColor
                                            else -> inactiveColor
                                        },
                                    shape = CircleShape
                                )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.icon_tick),
                        modifier =
                            Modifier.align(Alignment.CenterStart)
                                .alpha(
                                    if (selected) {
                                        AlphaVisible
                                    } else {
                                        AlphaInvisible
                                    }
                                ),
                        contentDescription = null
                    )
                }
                Text(
                    text = relay.name,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier.weight(1f)
                            .align(Alignment.CenterVertically)
                            .alpha(
                                if (relay.active) {
                                    AlphaVisible
                                } else {
                                    AlphaInactive
                                }
                            )
                            .padding(
                                horizontal = Dimens.smallPadding,
                                vertical = Dimens.mediumPadding
                            )
                )
            }
            if (relay.hasChildren) {
                VerticalDivider(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.padding(vertical = Dimens.verticalDividerPadding)
                )
                Chevron(
                    isExpanded = expanded.value,
                    modifier =
                        Modifier.fillMaxHeight()
                            .clickable { expanded.value = !expanded.value }
                            .padding(horizontal = Dimens.largePadding)
                            .align(Alignment.CenterVertically)
                )
            }
        }
        if (expanded.value) {
            when (relay) {
                is RelayItem.Country -> {
                    relay.cities.forEach { relayCity ->
                        RelayLocationCell(
                            relay = relayCity,
                            selectedItem = selectedItem,
                            onSelectRelay = onSelectRelay,
                            modifier = Modifier.animateContentSize()
                        )
                    }
                }
                is RelayItem.City -> {
                    relay.relays.forEach { relay ->
                        RelayLocationCell(
                            relay = relay,
                            selectedItem = selectedItem,
                            onSelectRelay = onSelectRelay,
                            modifier = Modifier.animateContentSize()
                        )
                    }
                }
                is RelayItem.Relay,
                is RelayItem.CustomList -> {}
            }
        }
    }
}
