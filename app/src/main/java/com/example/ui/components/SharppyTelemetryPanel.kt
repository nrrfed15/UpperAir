package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import com.example.domain.model.SoundingLevel
import com.example.domain.model.SoundingProfile
import com.example.domain.physics.AtmosphericCalculator
import com.example.domain.physics.ParcelType
import com.example.ui.theme.DewpointEmerald
import com.example.ui.theme.InstabilityCoral
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.StormViolet
import com.example.ui.theme.ThermalAmber
import com.example.ui.util.TimeFormatters
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * High-density SPC / SHARPpy-style meteorological parameter matrices:
 * 1. Mandatory Pressure Levels Quick Strip (850 / 700 / 500 / 300 hPa)
 * 2. Interactive Parcel & Thermodynamics Matrix ([SB | ML | MU] + CAPE, CIN, LI, LCL, LFC, EL)
 * 3. SPC Severe Weather Composites (SCP, Fixed-Layer STP, 0-1km SRH, 0-1km EHI)
 * 4. Kinematics & Shear Table (0-1km, 0-6km, SRH 0-3km, Jet Max)
 * 5. Moisture & Thermal Profile (PWAT, 0°C Freezing, 700-500 LR, K, TT)
 */
@Composable
fun SharppyTelemetryPanel(
    profile: SoundingProfile,
    useFahrenheit: Boolean,
    useMph: Boolean,
    selectedParcelType: ParcelType = ParcelType.SURFACE_BASED,
    onSelectParcelType: (ParcelType) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val idx = profile.indices
    val sortedLevels = remember(profile) { profile.levels.sortedByDescending { it.pressureHpa } }
    val activeParcel = remember(sortedLevels, selectedParcelType) {
        AtmosphericCalculator.computeParcelAnalysis(sortedLevels, selectedParcelType)
    }
    val composites = remember(sortedLevels) {
        AtmosphericCalculator.computeSevereComposites(sortedLevels)
    }

    val wUnit = if (useMph) "mph" else "kt"
    val shear06 = if (useMph) (idx.shear06Kt * 1.15078).roundToInt() else idx.shear06Kt
    val shear01 = if (useMph) (idx.shear01Kt * 1.15078).roundToInt() else idx.shear01Kt
    val maxWind = if (useMph) (idx.maxWindKt * 1.15078).roundToInt() else idx.maxWindKt
    val pwatIn = "%.2f\"".format(idx.pwatMm / 25.4)

    val displayedCape = if (selectedParcelType == ParcelType.SURFACE_BASED) idx.sbcapeJkg else activeParcel.capeJkg
    val displayedCin = if (selectedParcelType == ParcelType.SURFACE_BASED) idx.sbcinJkg else activeParcel.cinJkg
    val displayedLi = if (selectedParcelType == ParcelType.SURFACE_BASED) idx.liftedIndex else activeParcel.liftedIndex
    val displayedLclM = activeParcel.lclHeightM.roundToInt()
    val displayedLclHpa = activeParcel.lclPressureHpa.roundToInt()
    val displayedLfcM = activeParcel.lfcHeightM?.roundToInt()
    val displayedElM = activeParcel.elHeightM?.roundToInt()

    val capeAccent = when {
        idx.sbcapeJkg >= 2000 -> InstabilityCoral
        idx.sbcapeJkg >= 800 -> ThermalAmber
        idx.sbcapeJkg >= 250 -> SkyCyan
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shearAccent = when {
        idx.shear06Kt >= 45 -> InstabilityCoral
        idx.shear06Kt >= 30 -> ThermalAmber
        else -> SkyCyan
    }
    val srhAccent = when {
        idx.srh03M2S2 >= 250 -> InstabilityCoral
        idx.srh03M2S2 >= 150 -> ThermalAmber
        else -> DewpointEmerald
    }
    val lrAccent = when {
        idx.lapseRate700500 >= 7.5 -> InstabilityCoral
        idx.lapseRate700500 >= 6.5 -> ThermalAmber
        else -> MaterialTheme.colorScheme.onSurface
    }

    val mandatoryLevels = remember(profile) {
        listOf(850, 700, 500, 300).map { targetP ->
            val match = profile.levels.minByOrNull { abs(it.pressureHpa - targetP) }
                ?.takeIf { abs(it.pressureHpa - targetP) <= 35.0 }
            targetP to match
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Mandatory Pressure Levels Quick Strip
        SharppySectionFrame(
            title = "MANDATORY LEVELS",
            trailing = if (useFahrenheit) "TEMP (°F) • WIND ($wUnit)" else "TEMP (°C) • WIND ($wUnit)",
            accentColor = SkyCyan
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                mandatoryLevels.forEach { (pHpa, lvl) ->
                    val tStr = lvl?.let { TimeFormatters.formatTemp(it.tempC, useFahrenheit) } ?: "--"
                    val tdStr = lvl?.dewpointC?.let { TimeFormatters.formatTemp(it, useFahrenheit) } ?: "--"
                    val wStr = if (lvl?.windDirDeg != null && lvl.windSpeedKt != null) {
                        val spd = if (useMph) (lvl.windSpeedKt * 1.15078).roundToInt() else lvl.windSpeedKt.roundToInt()
                        "${lvl.windDirDeg.roundToInt().toString().padStart(3, '0')}°/$spd"
                    } else "--"
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "$pHpa",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = tStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = ThermalAmber,
                            maxLines = 1
                        )
                        Text(
                            text = tdStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = DewpointEmerald,
                            maxLines = 1
                        )
                        Text(
                            text = wStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2. Interactive Parcel & Thermodynamics Matrix ([SB | ML | MU])
        SharppySectionFrame(
            title = "PARCEL & THERMODYNAMICS",
            trailing = severityLabel(maxOf(displayedCape, composites.mucapeJkg), idx.shear06Kt),
            accentColor = capeAccent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ParcelType.entries.forEach { pt ->
                    val active = pt == selectedParcelType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) SkyCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                if (active) SkyCyan else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectParcelType(pt) }
                            .testTag("parcel_switch_${pt.code.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${pt.code} • ${pt.label.substringBefore("-")}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) SkyCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            MatrixRow(
                cells = listOf(
                    MatrixCell("${selectedParcelType.code}CAPE", "$displayedCape J/kg", capeAccent),
                    MatrixCell("${selectedParcelType.code}CIN", "$displayedCin J/kg", MaterialTheme.colorScheme.onSurfaceVariant),
                    MatrixCell("SB / ML / MU", "${idx.sbcapeJkg}/${idx.mlcapeJkg}/${composites.mucapeJkg}", SkyCyan)
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            MatrixRow(
                cells = listOf(
                    MatrixCell("LIFTED IDX", "$displayedLi", if (displayedLi <= -4.0) InstabilityCoral else StormViolet),
                    MatrixCell("LCL", "${displayedLclM}m ($displayedLclHpa)", DewpointEmerald),
                    MatrixCell(
                        "LFC / EL",
                        "${displayedLfcM?.let { "${it}m" } ?: "--"} / ${displayedElM?.let { "${(it / 1000.0).let { km -> "%.1fkm".format(km) }}" } ?: "--"}",
                        MaterialTheme.colorScheme.onSurface
                    )
                )
            )
        }

        // 3. SPC Severe Composites Matrix (SCP, Fixed-Layer STP, 0-1km SRH, 0-1km EHI)
        val scpColor = when {
            composites.scp >= 4.0 -> InstabilityCoral
            composites.scp >= 1.0 -> ThermalAmber
            else -> MaterialTheme.colorScheme.onSurface
        }
        val stpColor = when {
            composites.stp >= 3.0 -> InstabilityCoral
            composites.stp >= 1.0 -> ThermalAmber
            else -> MaterialTheme.colorScheme.onSurface
        }
        val srh01Color = when {
            composites.srh01M2S2 >= 200 -> InstabilityCoral
            composites.srh01M2S2 >= 100 -> ThermalAmber
            else -> DewpointEmerald
        }
        val ehiColor = when {
            composites.ehi01 >= 2.0 -> InstabilityCoral
            composites.ehi01 >= 1.0 -> ThermalAmber
            else -> SkyCyan
        }
        SharppySectionFrame(
            title = "SPC SEVERE COMPOSITES",
            trailing = when {
                composites.stp >= 1.0 -> "TOR POTENTIAL"
                composites.scp >= 1.0 -> "SUPERCELL ENV"
                else -> "LOW COMPOSITE"
            },
            accentColor = if (composites.stp >= 1.0 || composites.scp >= 4.0) InstabilityCoral else if (composites.scp >= 1.0) ThermalAmber else DewpointEmerald
        ) {
            MatrixRow(
                cells = listOf(
                    MatrixCell("SCP", "%.1f".format(composites.scp), scpColor),
                    MatrixCell("STP (FIX)", "%.1f".format(composites.stp), stpColor),
                    MatrixCell("0-1KM SRH", "${composites.srh01M2S2} m²/s²", srh01Color),
                    MatrixCell("0-1KM EHI", "%.1f".format(composites.ehi01), ehiColor)
                )
            )
        }

        // 3. Kinematics & Wind Shear Matrix
        SharppySectionFrame(
            title = "KINEMATICS & SHEAR",
            trailing = "BUNKERS & BULK SHEAR",
            accentColor = shearAccent
        ) {
            MatrixRow(
                cells = listOf(
                    MatrixCell("0-1KM SHR", "$shear01 $wUnit", if (idx.shear01Kt >= 20) InstabilityCoral else SkyCyan),
                    MatrixCell("0-6KM SHR", "$shear06 $wUnit", shearAccent),
                    MatrixCell("0-3KM SRH", "${idx.srh03M2S2} m²/s²", srhAccent)
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            MatrixRow(
                cells = listOf(
                    MatrixCell("JET MAX", "$maxWind $wUnit", SkyCyan),
                    MatrixCell("JET LEVEL", "${idx.maxWindPressureHpa} hPa", MaterialTheme.colorScheme.onSurface),
                    MatrixCell("7-5 LAPSE", "${idx.lapseRate700500}°C/km", lrAccent)
                )
            )
        }

        // 4. Moisture & Stability Indices Matrix
        SharppySectionFrame(
            title = "MOISTURE & STABILITY",
            trailing = "COLUMN & INDICES",
            accentColor = DewpointEmerald
        ) {
            MatrixRow(
                cells = listOf(
                    MatrixCell("PWAT", "${idx.pwatMm}mm ($pwatIn)", DewpointEmerald),
                    MatrixCell("0°C FRZ LVL", "${idx.freezingLevelM} m", ThermalAmber),
                    MatrixCell("K / TT", "${idx.kIndex.roundToInt()} / ${idx.totalTotals.roundToInt()}", StormViolet)
                )
            )
        }
    }
}

@Composable
fun SharppyLevelsTable(
    levels: List<SoundingLevel>,
    useFahrenheit: Boolean,
    useMph: Boolean,
    modifier: Modifier = Modifier
) {
    val wUnit = if (useMph) "mph" else "kt"
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PRES", style = MaterialTheme.typography.labelSmall, color = SkyCyan, modifier = Modifier.weight(0.95f))
                Text("HGHT", style = MaterialTheme.typography.labelSmall, color = SkyCyan, modifier = Modifier.weight(1.05f))
                Text("TEMP", style = MaterialTheme.typography.labelSmall, color = ThermalAmber, modifier = Modifier.weight(1f))
                Text("DEWP", style = MaterialTheme.typography.labelSmall, color = DewpointEmerald, modifier = Modifier.weight(1f))
                Text("WIND ($wUnit)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.25f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            levels.take(45).forEachIndexed { idx, lvl ->
                val tStr = TimeFormatters.formatTemp(lvl.tempC, useFahrenheit)
                val tdStr = lvl.dewpointC?.let { TimeFormatters.formatTemp(it, useFahrenheit) } ?: "--"
                val wStr = if (lvl.windDirDeg != null && lvl.windSpeedKt != null) {
                    val spd = if (useMph) "${(lvl.windSpeedKt * 1.15078).roundToInt()}" else "${lvl.windSpeedKt.roundToInt()}"
                    "${lvl.windDirDeg.roundToInt().toString().padStart(3, '0')}°/$spd"
                } else "--"
                val rowBg = if (idx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${lvl.pressureHpa.roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.95f))
                    Text("${lvl.heightM.roundToInt()}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.05f))
                    Text(tStr, style = MaterialTheme.typography.labelSmall, color = ThermalAmber, modifier = Modifier.weight(1f))
                    Text(tdStr, style = MaterialTheme.typography.labelSmall, color = DewpointEmerald, modifier = Modifier.weight(1f))
                    Text(wStr, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.25f))
                }
            }
        }
    }
}

private data class MatrixCell(
    val label: String,
    val value: String,
    val valueColor: Color
)

@Composable
private fun SharppySectionFrame(
    title: String,
    trailing: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(11.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun MatrixRow(cells: List<MatrixCell>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cells.forEach { cell ->
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cell.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = cell.value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cell.valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun severityLabel(cape: Int, shear06: Int): String = when {
    cape >= 2000 && shear06 >= 40 -> "HIGH CONVECTIVE"
    cape >= 1000 && shear06 >= 30 -> "MOD CONVECTIVE"
    cape >= 500 -> "INSTABILITY PRESENT"
    else -> "STABLE / MARGINAL"
}
