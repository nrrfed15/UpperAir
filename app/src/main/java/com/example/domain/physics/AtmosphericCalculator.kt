package com.example.domain.physics

import com.example.domain.model.SoundingLevel
import com.example.domain.model.ThermodynamicIndices
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ParcelType(val code: String, val label: String) {
    SURFACE_BASED("SB", "Surface-Based"),
    MIXED_LAYER("ML", "Mixed-Layer"),
    MOST_UNSTABLE("MU", "Most-Unstable")
}

data class ParcelPoint(
    val pressureHpa: Double,
    val heightM: Double,
    val parcelTempC: Double,
    val envTempC: Double
)

data class ParcelAnalysis(
    val trace: List<ParcelPoint>,
    val lclPressureHpa: Double,
    val lclHeightM: Double,
    val lfcHeightM: Double?,
    val elHeightM: Double?,
    val capeJkg: Int,
    val cinJkg: Int,
    val liftedIndex: Double,
    val parcelType: ParcelType = ParcelType.SURFACE_BASED
)

data class SevereComposites(
    val srh01M2S2: Int,
    val srh03M2S2: Int,
    val mucapeJkg: Int,
    val mucinJkg: Int,
    val scp: Double,
    val stp: Double,
    val ehi01: Double
)

object AtmosphericCalculator {
    private const val RD = 287.058       // J/(kg*K) dry air gas constant
    private const val RV = 461.5         // J/(kg*K) water vapor gas constant
    private const val CP = 1005.7        // J/(kg*K) specific heat at constant pressure
    private const val LV = 2.501e6       // J/kg latent heat of vaporization
    private const val KAPPA = RD / CP    // ~0.2854
    private const val G = 9.80665        // m/s^2
    private const val EPS = 0.622        // Rd / Rv

    fun saturationVaporPressureHpa(tempC: Double): Double {
        return 6.112 * exp((17.67 * tempC) / (tempC + 243.5))
    }

    fun dewpointFromTempAndRh(tempC: Double, rhPercent: Double): Double {
        val rh = rhPercent.coerceIn(1.0, 100.0)
        val gamma = ln(rh / 100.0) + (17.67 * tempC) / (243.5 + tempC)
        return (243.5 * gamma) / (17.67 - gamma)
    }

    fun rhFromTempAndDewpoint(tempC: Double, dewpointC: Double): Int {
        val es = saturationVaporPressureHpa(tempC)
        val e = saturationVaporPressureHpa(dewpointC)
        return ((e / es) * 100.0).roundToInt().coerceIn(1, 100)
    }

    fun mixingRatioKgKg(pressureHpa: Double, tempC: Double): Double {
        val es = saturationVaporPressureHpa(tempC).coerceAtMost(pressureHpa * 0.9)
        return EPS * es / (pressureHpa - es)
    }

    fun tempFromMixingRatio(pressureHpa: Double, mixingRatioGKg: Double): Double {
        val w = (mixingRatioGKg / 1000.0).coerceAtLeast(1e-6)
        val e = (w * pressureHpa) / (EPS + w)
        val lnTerm = ln((e / 6.112).coerceAtLeast(1e-6))
        return (243.5 * lnTerm) / (17.67 - lnTerm)
    }

    fun dryAdiabatTempC(thetaK: Double, pressureHpa: Double): Double {
        return thetaK * (pressureHpa / 1000.0).pow(KAPPA) - 273.15
    }

    /**
     * Bolton (1980) equivalent potential temperature (K) used to identify the Most-Unstable parcel.
     */
    fun equivalentPotentialTempK(pressureHpa: Double, tempC: Double, dewpointC: Double): Double {
        val tK = (tempC + 273.15).coerceAtLeast(160.0)
        val tdK = min(dewpointC + 273.15, tK).coerceAtLeast(150.0)
        val tLclK = (1.0 / ((1.0 / (tdK - 56.0)) + ln(tK / tdK) / 800.0) + 56.0).coerceAtLeast(150.0)
        val r = mixingRatioKgKg(pressureHpa, tdK - 273.15).coerceIn(1e-6, 0.06)
        val thetaDl = tK * (1000.0 / pressureHpa.coerceAtLeast(50.0)).pow(0.2854 * (1.0 - 0.28 * r))
        return thetaDl * exp((3.376 / tLclK - 0.00254) * (r * 1000.0) * (1.0 + 0.81 * r))
    }

    /**
     * Computes dT/dp (C per hPa) along a pseudoadiabat.
     */
    private fun moistLapseRateDtdP(pressureHpa: Double, tempC: Double): Double {
        val tK = (tempC + 273.15).coerceAtLeast(150.0)
        val ws = mixingRatioKgKg(pressureHpa, tempC)
        val num = (1.0 / pressureHpa) * (RD * tK + LV * ws)
        val den = CP + (LV * LV * ws * EPS) / (RD * tK * tK)
        return num / den
    }

    fun computeParcelAnalysis(
        sortedLevels: List<SoundingLevel>,
        mixedLayer: Boolean = false
    ): ParcelAnalysis = computeParcelAnalysis(
        sortedLevels = sortedLevels,
        parcelType = if (mixedLayer) ParcelType.MIXED_LAYER else ParcelType.SURFACE_BASED
    )

    fun computeParcelAnalysis(
        sortedLevels: List<SoundingLevel>,
        parcelType: ParcelType
    ): ParcelAnalysis {
        if (sortedLevels.size < 2) {
            return ParcelAnalysis(emptyList(), 950.0, 500.0, null, null, 0, 0, 0.0, parcelType)
        }
        val sfc = sortedLevels.first()
        val pStart: Double
        val hStart: Double
        val tStartC: Double
        val tdStartC: Double

        when (parcelType) {
            ParcelType.MIXED_LAYER -> {
                val topP = sfc.pressureHpa - 100.0
                val mlLevels = sortedLevels.filter { it.pressureHpa >= topP }.ifEmpty { listOf(sfc) }
                pStart = sfc.pressureHpa
                hStart = sfc.heightM
                tStartC = mlLevels.map { it.tempC }.average()
                tdStartC = mlLevels.mapNotNull { it.dewpointC }.ifEmpty { listOf(tStartC - 5.0) }.average()
            }
            ParcelType.MOST_UNSTABLE -> {
                val minP = sfc.pressureHpa - 300.0
                val candidates = sortedLevels.filter { it.pressureHpa >= minP }.ifEmpty { listOf(sfc) }
                val best = candidates.maxByOrNull { lvl ->
                    val td = lvl.dewpointC ?: (lvl.tempC - 8.0)
                    equivalentPotentialTempK(lvl.pressureHpa, lvl.tempC, td)
                } ?: sfc
                pStart = best.pressureHpa
                hStart = best.heightM
                tStartC = best.tempC
                tdStartC = best.dewpointC ?: (best.tempC - 5.0)
            }
            ParcelType.SURFACE_BASED -> {
                pStart = sfc.pressureHpa
                hStart = sfc.heightM
                tStartC = sfc.tempC
                tdStartC = sfc.dewpointC ?: (sfc.tempC - 5.0)
            }
        }

        // LCL temperature (Bolton 1980)
        val tK = tStartC + 273.15
        val tdK = min(tdStartC + 273.15, tK)
        val tLclK = 1.0 / ((1.0 / (tdK - 56.0)) + ln(tK / tdK) / 800.0) + 56.0
        val pLclHpa = pStart * (tLclK / tK).pow(1.0 / KAPPA)
        val lclHeightM = hStart + max(0.0, 125.0 * (tStartC - (tdK - 273.15)))

        val trace = mutableListOf<ParcelPoint>()
        var currentP = pStart
        var currentT = tStartC

        // Walk upward in 10 hPa steps from parcel origin to 100 hPa for smooth parcel integration
        val stepHpa = 10.0
        var cape = 0.0
        var cin = 0.0
        var lfcH: Double? = null
        var elH: Double? = null
        var wasPositive = false

        var prevEnvT = interpolateTempAtPressure(sortedLevels, currentP)
        var prevH = interpolateHeightAtPressure(sortedLevels, currentP)
        var prevParcelT = currentT
        trace.add(ParcelPoint(currentP, prevH, prevParcelT, prevEnvT))

        while (currentP > 105.0) {
            val nextP = max(100.0, currentP - stepHpa)
            val dp = currentP - nextP
            val nextT = if (currentP > pLclHpa) {
                if (nextP >= pLclHpa) {
                    tK * (nextP / pStart).pow(KAPPA) - 273.15
                } else {
                    // Cross LCL in this step
                    val dpMoist = pLclHpa - nextP
                    val tAtLcl = tLclK - 273.15
                    tAtLcl - moistLapseRateDtdP((pLclHpa + nextP) * 0.5, tAtLcl) * dpMoist
                }
            } else {
                // RK2 moist adiabatic integration
                val k1 = moistLapseRateDtdP(currentP, currentT)
                val tMid = currentT - k1 * (dp * 0.5)
                val k2 = moistLapseRateDtdP(currentP - dp * 0.5, tMid)
                currentT - k2 * dp
            }

            val envT = interpolateTempAtPressure(sortedLevels, nextP)
            val nextH = interpolateHeightAtPressure(sortedLevels, nextP)
            val dz = (nextH - prevH).coerceAtLeast(0.0)

            val prevDiff = prevParcelT - prevEnvT
            val nextDiff = nextT - envT
            val meanEnvK = ((prevEnvT + envT) * 0.5 + 273.15).coerceAtLeast(150.0)
            val meanDiff = (prevDiff + nextDiff) * 0.5

            if (nextP <= pLclHpa || meanDiff > 0.0) {
                if (meanDiff > 0.0) {
                    cape += G * (meanDiff / meanEnvK) * dz
                    if (lfcH == null) lfcH = nextH
                    wasPositive = true
                    elH = nextH
                } else if (nextP <= pLclHpa && !wasPositive && nextP > 400.0) {
                    cin += G * (meanDiff / meanEnvK) * dz // negative value
                }
            } else if (!wasPositive && nextP > 450.0) {
                cin += G * (meanDiff / meanEnvK) * dz
            }

            trace.add(ParcelPoint(nextP, nextH, nextT, envT))
            currentP = nextP
            currentT = nextT
            prevEnvT = envT
            prevH = nextH
            prevParcelT = nextT
        }

        val p500Parcel = trace.minByOrNull { abs(it.pressureHpa - 500.0) }
        val li = if (p500Parcel != null) {
            ((p500Parcel.envTempC - p500Parcel.parcelTempC) * 10.0).roundToInt() / 10.0
        } else 0.0

        return ParcelAnalysis(
            trace = trace,
            lclPressureHpa = pLclHpa,
            lclHeightM = lclHeightM,
            lfcHeightM = if (cape > 10.0) lfcH else null,
            elHeightM = if (cape > 10.0) elH else null,
            capeJkg = cape.roundToInt().coerceAtLeast(0),
            cinJkg = cin.roundToInt().coerceAtMost(0),
            liftedIndex = li,
            parcelType = parcelType
        )
    }

    fun computeIndices(
        levels: List<SoundingLevel>,
        modelCapeOverride: Int? = null,
        modelCinOverride: Int? = null,
        modelLiOverride: Double? = null,
        modelFreezingOverrideM: Int? = null
    ): ThermodynamicIndices {
        val sorted = levels.sortedByDescending { it.pressureHpa }
        if (sorted.size < 3) {
            return ThermodynamicIndices(
                0, 0, 0, 0.0, 0, 1000, null, null, 0.0, 0.0, 0.0, 0, 0, 0, 0.0, 0, 0, 500
            )
        }

        val sbParcel = computeParcelAnalysis(sorted, mixedLayer = false)
        val mlParcel = computeParcelAnalysis(sorted, mixedLayer = true)

        val t850 = interpolateTempAtPressure(sorted, 850.0)
        val td850 = interpolateDewpointAtPressure(sorted, 850.0)
        val t700 = interpolateTempAtPressure(sorted, 700.0)
        val td700 = interpolateDewpointAtPressure(sorted, 700.0)
        val t500 = interpolateTempAtPressure(sorted, 500.0)
        val h700 = interpolateHeightAtPressure(sorted, 700.0)
        val h500 = interpolateHeightAtPressure(sorted, 500.0)

        val kIndex = ((t850 - t500) + td850 - (t700 - td700))
        val totalTotals = (t850 - t500) + (td850 - t500)
        val lapse700500 = if (h500 > h700) {
            ((t700 - t500) / ((h500 - h700) / 1000.0))
        } else 6.5

        // Precipitable water (mm) = (1/g) * integral(q dp)
        var pwatMm = 0.0
        for (i in 0 until sorted.size - 1) {
            val l1 = sorted[i]
            val l2 = sorted[i + 1]
            val q1 = mixingRatioKgKg(l1.pressureHpa, l1.dewpointC ?: (l1.tempC - 8.0))
            val q2 = mixingRatioKgKg(l2.pressureHpa, l2.dewpointC ?: (l2.tempC - 8.0))
            val dpPa = (l1.pressureHpa - l2.pressureHpa) * 100.0
            if (dpPa > 0) {
                pwatMm += 0.5 * (q1 + q2) * dpPa / G
            }
        }

        // Freezing level (first height above surface where T <= 0 C)
        val freezingM = modelFreezingOverrideM ?: findFreezingLevelM(sorted)

        // Wind shear and SRH
        val sfcHeight = sorted.first().heightM
        val (uSfc, vSfc) = windVectorAtHeight(sorted, sfcHeight)
        val (u1km, v1km) = windVectorAtHeight(sorted, sfcHeight + 1000.0)
        val (u6km, v6km) = windVectorAtHeight(sorted, sfcHeight + 6000.0)

        val shear01 = hypot(u1km - uSfc, v1km - vSfc).roundToInt()
        val shear06 = hypot(u6km - uSfc, v6km - vSfc).roundToInt()
        val srh03 = computeSrh03(sorted)

        val maxWindLevel = sorted.maxByOrNull { it.windSpeedKt ?: 0.0 }

        return ThermodynamicIndices(
            sbcapeJkg = modelCapeOverride ?: sbParcel.capeJkg,
            sbcinJkg = modelCinOverride ?: sbParcel.cinJkg,
            mlcapeJkg = mlParcel.capeJkg,
            liftedIndex = modelLiOverride ?: sbParcel.liftedIndex,
            lclMeters = sbParcel.lclHeightM.roundToInt(),
            lclHpa = sbParcel.lclPressureHpa.roundToInt(),
            lfcMeters = sbParcel.lfcHeightM?.roundToInt(),
            elMeters = sbParcel.elHeightM?.roundToInt(),
            kIndex = (kIndex * 10.0).roundToInt() / 10.0,
            totalTotals = (totalTotals * 10.0).roundToInt() / 10.0,
            pwatMm = (pwatMm * 10.0).roundToInt() / 10.0,
            shear01Kt = shear01,
            shear06Kt = shear06,
            srh03M2S2 = srh03,
            lapseRate700500 = (lapse700500 * 10.0).roundToInt() / 10.0,
            freezingLevelM = freezingM,
            maxWindKt = (maxWindLevel?.windSpeedKt ?: 0.0).roundToInt(),
            maxWindPressureHpa = (maxWindLevel?.pressureHpa ?: 250.0).roundToInt()
        )
    }

    fun findFreezingLevelM(sorted: List<SoundingLevel>): Int {
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (a.tempC >= 0.0 && b.tempC <= 0.0 && abs(a.tempC - b.tempC) > 0.001) {
                val frac = a.tempC / (a.tempC - b.tempC)
                return (a.heightM + frac * (b.heightM - a.heightM)).roundToInt()
            }
        }
        return if ((sorted.firstOrNull()?.tempC ?: 0.0) <= 0.0) {
            sorted.firstOrNull()?.heightM?.roundToInt() ?: 0
        } else 4500
    }

    /**
     * Bunkers Right-Moving Storm Motion in knots (u, v).
     */
    fun computeBunkersRightMotionKt(sorted: List<SoundingLevel>): Pair<Double, Double> {
        if (sorted.size < 2) return 0.0 to 0.0
        val sfcH = sorted.first().heightM

        // 0-6 km mean wind
        var uMean = 0.0
        var vMean = 0.0
        val steps = 12
        for (i in 0..steps) {
            val h = sfcH + (6000.0 * i / steps)
            val (u, v) = windVectorAtHeight(sorted, h)
            uMean += u
            vMean += v
        }
        uMean /= (steps + 1)
        vMean /= (steps + 1)

        // 0-500m and 5.5-6km mean winds for shear vector
        val (uLow, vLow) = windVectorAtHeight(sorted, sfcH + 250.0)
        val (uHigh, vHigh) = windVectorAtHeight(sorted, sfcH + 5750.0)
        val du = uHigh - uLow
        val dv = vHigh - vLow
        val mag = hypot(du, dv).coerceAtLeast(1e-3)

        // Deviate 7.5 m/s (~14.6 kt) perpendicular right of shear vector
        val devKt = 14.6
        val uRight = uMean + devKt * (dv / mag)
        val vRight = vMean - devKt * (du / mag)
        return uRight to vRight
    }

    fun computeSrh01(sorted: List<SoundingLevel>): Int = computeSrh(sorted, 1000.0)

    fun computeSrh03(sorted: List<SoundingLevel>): Int = computeSrh(sorted, 3000.0)

    fun computeSrh(sorted: List<SoundingLevel>, topHeightAglM: Double): Int {
        if (sorted.size < 3 || topHeightAglM <= 0.0) return 0
        val sfcH = sorted.first().heightM
        val (cxKt, cyKt) = computeBunkersRightMotionKt(sorted)
        val ktToMs = 0.514444
        val cx = cxKt * ktToMs
        val cy = cyKt * ktToMs

        val samples = if (topHeightAglM <= 1500.0) 10 else 15
        var srh = 0.0
        var prevPair = windVectorAtHeight(sorted, sfcH)
        for (i in 1..samples) {
            val h = sfcH + (topHeightAglM * i / samples)
            val currPair = windVectorAtHeight(sorted, h)
            val u1 = prevPair.first * ktToMs - cx
            val v1 = prevPair.second * ktToMs - cy
            val u2 = currPair.first * ktToMs - cx
            val v2 = currPair.second * ktToMs - cy
            srh += (u2 * v1 - u1 * v2)
            prevPair = currPair
        }
        return srh.roundToInt()
    }

    /**
     * Computes SPC-style operational severe weather composite indices (SCP, fixed-layer STP, 0-1km EHI)
     * dynamically from the sounding vertical profile.
     */
    fun computeSevereComposites(levels: List<SoundingLevel>): SevereComposites {
        val sorted = levels.sortedByDescending { it.pressureHpa }
        if (sorted.size < 3) {
            return SevereComposites(0, 0, 0, 0, 0.0, 0.0, 0.0)
        }
        val sbParcel = computeParcelAnalysis(sorted, ParcelType.SURFACE_BASED)
        val muParcel = computeParcelAnalysis(sorted, ParcelType.MOST_UNSTABLE)
        val srh01 = computeSrh01(sorted)
        val srh03 = computeSrh03(sorted)

        val sfcH = sorted.first().heightM
        val (uSfc, vSfc) = windVectorAtHeight(sorted, sfcH)
        val (u6km, v6km) = windVectorAtHeight(sorted, sfcH + 6000.0)
        val shear06Kt = hypot(u6km - uSfc, v6km - vSfc)
        val shear06Ms = shear06Kt * 0.514444

        // Supercell Composite Parameter (SCP): (MUCAPE/1000) * (SRH03/50) * (BRN/0-6km shear term) * (CIN term)
        val scpShearTerm = when {
            shear06Ms < 10.0 -> 0.0
            shear06Ms > 20.0 -> 1.0
            else -> shear06Ms / 20.0
        }
        val scpCinTerm = if (muParcel.cinJkg >= -40) {
            1.0
        } else {
            (-40.0 / muParcel.cinJkg.toDouble()).coerceIn(0.0, 1.0)
        }
        val rawScp = (muParcel.capeJkg / 1000.0) * (max(0, srh03) / 50.0) * scpShearTerm * scpCinTerm

        // Significant Tornado Parameter (fixed-layer STP)
        val lclAglM = (sbParcel.lclHeightM - sfcH).coerceAtLeast(0.0)
        val lclTerm = when {
            lclAglM < 1000.0 -> 1.0
            lclAglM > 2000.0 -> 0.0
            else -> (2000.0 - lclAglM) / 1000.0
        }
        val stpShearTerm = when {
            shear06Ms < 12.5 -> 0.0
            shear06Ms > 30.0 -> 1.5
            else -> shear06Ms / 20.0
        }
        val stpCinTerm = when {
            sbParcel.cinJkg >= -50 -> 1.0
            sbParcel.cinJkg <= -200 -> 0.0
            else -> (200.0 + sbParcel.cinJkg) / 150.0
        }
        val rawStp = (sbParcel.capeJkg / 1500.0) *
            lclTerm *
            (max(0, srh01) / 150.0) *
            stpShearTerm *
            stpCinTerm

        // 0-1 km Energy-Helicity Index (EHI)
        val rawEhi01 = (sbParcel.capeJkg * max(0, srh01)) / 160000.0

        return SevereComposites(
            srh01M2S2 = srh01,
            srh03M2S2 = srh03,
            mucapeJkg = muParcel.capeJkg,
            mucinJkg = muParcel.cinJkg,
            scp = (rawScp * 10.0).roundToInt() / 10.0,
            stp = (rawStp * 10.0).roundToInt() / 10.0,
            ehi01 = (rawEhi01 * 10.0).roundToInt() / 10.0
        )
    }

    fun windVectorFromMet(dirDeg: Double, speedKt: Double): Pair<Double, Double> {
        val rad = dirDeg * PI / 180.0
        val u = -speedKt * sin(rad)
        val v = -speedKt * cos(rad)
        return u to v
    }

    fun metFromWindVector(u: Double, v: Double): Pair<Double, Double> {
        val spd = hypot(u, v)
        val dir = ((atan2(-u, -v) * 180.0 / PI) + 360.0) % 360.0
        return dir to spd
    }

    fun windVectorAtHeight(sorted: List<SoundingLevel>, targetHeightM: Double): Pair<Double, Double> {
        val valid = sorted.filter { it.windDirDeg != null && it.windSpeedKt != null }
            .sortedBy { it.heightM }
        if (valid.isEmpty()) return 0.0 to 0.0
        if (targetHeightM <= valid.first().heightM) {
            val l = valid.first()
            return windVectorFromMet(l.windDirDeg!!, l.windSpeedKt!!)
        }
        if (targetHeightM >= valid.last().heightM) {
            val l = valid.last()
            return windVectorFromMet(l.windDirDeg!!, l.windSpeedKt!!)
        }
        for (i in 0 until valid.size - 1) {
            val a = valid[i]
            val b = valid[i + 1]
            if (targetHeightM in a.heightM..b.heightM) {
                val frac = if (b.heightM > a.heightM) (targetHeightM - a.heightM) / (b.heightM - a.heightM) else 0.0
                val (ua, va) = windVectorFromMet(a.windDirDeg!!, a.windSpeedKt!!)
                val (ub, vb) = windVectorFromMet(b.windDirDeg!!, b.windSpeedKt!!)
                return (ua + frac * (ub - ua)) to (va + frac * (vb - va))
            }
        }
        return 0.0 to 0.0
    }

    fun interpolateTempAtPressure(sorted: List<SoundingLevel>, targetP: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (targetP >= sorted.first().pressureHpa) return sorted.first().tempC
        if (targetP <= sorted.last().pressureHpa) return sorted.last().tempC
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (targetP <= a.pressureHpa && targetP >= b.pressureHpa) {
                val lnA = ln(a.pressureHpa)
                val lnB = ln(b.pressureHpa)
                val lnT = ln(targetP)
                val frac = if (lnA != lnB) (lnA - lnT) / (lnA - lnB) else 0.0
                return a.tempC + frac * (b.tempC - a.tempC)
            }
        }
        return sorted.last().tempC
    }

    fun interpolateDewpointAtPressure(sorted: List<SoundingLevel>, targetP: Double): Double {
        val valid = sorted.filter { it.dewpointC != null }
        if (valid.isEmpty()) return interpolateTempAtPressure(sorted, targetP) - 10.0
        if (targetP >= valid.first().pressureHpa) return valid.first().dewpointC!!
        if (targetP <= valid.last().pressureHpa) return valid.last().dewpointC!!
        for (i in 0 until valid.size - 1) {
            val a = valid[i]
            val b = valid[i + 1]
            if (targetP <= a.pressureHpa && targetP >= b.pressureHpa) {
                val lnA = ln(a.pressureHpa)
                val lnB = ln(b.pressureHpa)
                val lnT = ln(targetP)
                val frac = if (lnA != lnB) (lnA - lnT) / (lnA - lnB) else 0.0
                return a.dewpointC!! + frac * (b.dewpointC!! - a.dewpointC!!)
            }
        }
        return valid.last().dewpointC!!
    }

    fun interpolateHeightAtPressure(sorted: List<SoundingLevel>, targetP: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (targetP >= sorted.first().pressureHpa) return sorted.first().heightM
        if (targetP <= sorted.last().pressureHpa) return sorted.last().heightM
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (targetP <= a.pressureHpa && targetP >= b.pressureHpa) {
                val lnA = ln(a.pressureHpa)
                val lnB = ln(b.pressureHpa)
                val lnT = ln(targetP)
                val frac = if (lnA != lnB) (lnA - lnT) / (lnA - lnB) else 0.0
                return a.heightM + frac * (b.heightM - a.heightM)
            }
        }
        return sorted.last().heightM
    }
}
