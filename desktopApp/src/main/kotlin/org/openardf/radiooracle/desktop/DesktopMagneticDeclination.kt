package org.openardf.radiooracle.desktop

import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DesktopMagneticDeclinationResult(
    val degrees: Double,
    val usesExpiredCoefficients: Boolean
)

/**
 * Offline WMM2025 declination calculator, ported from NOAA/NCEI's public-domain
 * WMM C implementation and WMM.COF coefficients.
 *
 * Source: NOAA WMM2025 Linux package, `GeomagnetismLibrary.c` and `WMM.COF`.
 * WMM2025 is valid for dates 2025.0 through 2030.0. Radio-Oracle continues
 * extrapolating with expired coefficients because route-map orientation only
 * needs practical ARDF map alignment, not survey-grade magnetic data.
 */
object DesktopMagneticDeclination {
    private const val MODEL_EPOCH = 2025.0
    private const val EXPIRATION_DECIMAL_YEAR = 2030.0
    private const val MAX_DEGREE = 12
    private const val NUM_TERMS = (MAX_DEGREE + 1) * (MAX_DEGREE + 2) / 2
    private const val WGS84_A_KM = 6378.137
    private const val WGS84_B_KM = 6356.7523142
    private const val EARTH_REFERENCE_RADIUS_KM = 6371.2
    private val WGS84_EPSILON_SQUARED = 1.0 - (WGS84_B_KM * WGS84_B_KM) / (WGS84_A_KM * WGS84_A_KM)
    private val coefficients: WmmCoefficients by lazy { WmmCoefficients.from(WMM2025_COEFFICIENTS) }
    private val cache = ConcurrentHashMap<String, Double>()

    fun degrees(point: CourseGeoPoint, date: LocalDate = LocalDate.now()): Double? =
        result(point, date)?.degrees

    internal fun degrees(point: CourseGeoPoint, decimalYear: Double): Double? {
        return result(point, decimalYear)?.degrees
    }

    fun result(point: CourseGeoPoint, date: LocalDate = LocalDate.now()): DesktopMagneticDeclinationResult? =
        result(point, decimalYear(date))

    internal fun result(point: CourseGeoPoint, decimalYear: Double): DesktopMagneticDeclinationResult? {
        if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..360.0) {
            return null
        }
        val key = listOf(
            String.format(Locale.US, "%.4f", point.latitude),
            String.format(Locale.US, "%.4f", point.longitude),
            String.format(Locale.US, "%.4f", decimalYear)
        ).joinToString("|")
        val degrees = cache.getOrPut(key) {
            calculateDeclinationDegrees(
                latitudeDegrees = point.latitude.coerceIn(-89.99999, 89.99999),
                longitudeDegrees = point.longitude,
                decimalYear = decimalYear
            )
        }
        return DesktopMagneticDeclinationResult(
            degrees = degrees,
            usesExpiredCoefficients = decimalYear >= EXPIRATION_DECIMAL_YEAR
        )
    }

    private fun decimalYear(date: LocalDate): Double =
        date.year + (date.dayOfYear - 1).toDouble() / date.lengthOfYear().toDouble()

    private fun calculateDeclinationDegrees(
        latitudeDegrees: Double,
        longitudeDegrees: Double,
        decimalYear: Double
    ): Double {
        val spherical = geodeticToSpherical(latitudeDegrees, longitudeDegrees)
        val legendre = associatedLegendreFunction(spherical.geocentricLatitudeDegrees)
        val harmonic = sphericalHarmonicVariables(spherical)
        val sphericalField = harmonicSummation(legendre, harmonic, spherical.geocentricLatitudeDegrees, decimalYear)
        val geoField = rotateToGeodetic(sphericalField, spherical.geocentricLatitudeDegrees, latitudeDegrees)
        return Math.toDegrees(atan2(geoField.by, geoField.bx))
    }

    private fun geodeticToSpherical(latitudeDegrees: Double, longitudeDegrees: Double): SphericalCoordinate {
        val cosLat = cos(Math.toRadians(latitudeDegrees))
        val sinLat = sin(Math.toRadians(latitudeDegrees))
        val radiusOfCurvature = WGS84_A_KM / sqrt(1.0 - WGS84_EPSILON_SQUARED * sinLat * sinLat)
        val xp = radiusOfCurvature * cosLat
        val zp = radiusOfCurvature * (1.0 - WGS84_EPSILON_SQUARED) * sinLat
        val radiusKm = sqrt(xp * xp + zp * zp)
        return SphericalCoordinate(
            longitudeDegrees = longitudeDegrees,
            geocentricLatitudeDegrees = Math.toDegrees(kotlin.math.asin(zp / radiusKm)),
            radiusKm = radiusKm
        )
    }

    private fun associatedLegendreFunction(geocentricLatitudeDegrees: Double): LegendreFunction {
        val x = sin(Math.toRadians(geocentricLatitudeDegrees))
        val z = sqrt((1.0 - x) * (1.0 + x))
        val pcup = DoubleArray(NUM_TERMS)
        val dPcup = DoubleArray(NUM_TERMS)
        val schmidtQuasiNorm = DoubleArray(NUM_TERMS)

        pcup[0] = 1.0
        dPcup[0] = 0.0
        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                val index = coefficientIndex(n, m)
                when {
                    n == m -> {
                        val index1 = coefficientIndex(n - 1, m - 1)
                        pcup[index] = z * pcup[index1]
                        dPcup[index] = z * dPcup[index1] + x * pcup[index1]
                    }
                    n == 1 && m == 0 -> {
                        val index1 = coefficientIndex(n - 1, m)
                        pcup[index] = x * pcup[index1]
                        dPcup[index] = x * dPcup[index1] - z * pcup[index1]
                    }
                    n > 1 -> {
                        val index1 = coefficientIndex(n - 2, m)
                        val index2 = coefficientIndex(n - 1, m)
                        if (m > n - 2) {
                            pcup[index] = x * pcup[index2]
                            dPcup[index] = x * dPcup[index2] - z * pcup[index2]
                        } else {
                            val k = (((n - 1) * (n - 1)) - (m * m)).toDouble() /
                                ((2 * n - 1) * (2 * n - 3)).toDouble()
                            pcup[index] = x * pcup[index2] - k * pcup[index1]
                            dPcup[index] = x * dPcup[index2] - z * pcup[index2] - k * dPcup[index1]
                        }
                    }
                }
            }
        }

        schmidtQuasiNorm[0] = 1.0
        for (n in 1..MAX_DEGREE) {
            var index = coefficientIndex(n, 0)
            var index1 = coefficientIndex(n - 1, 0)
            schmidtQuasiNorm[index] = schmidtQuasiNorm[index1] * (2 * n - 1).toDouble() / n.toDouble()
            for (m in 1..n) {
                index = coefficientIndex(n, m)
                index1 = coefficientIndex(n, m - 1)
                val firstOrderMultiplier = if (m == 1) 2 else 1
                schmidtQuasiNorm[index] = schmidtQuasiNorm[index1] *
                    sqrt(((n - m + 1) * firstOrderMultiplier).toDouble() / (n + m).toDouble())
            }
        }

        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                val index = coefficientIndex(n, m)
                pcup[index] *= schmidtQuasiNorm[index]
                dPcup[index] = -dPcup[index] * schmidtQuasiNorm[index]
            }
        }
        return LegendreFunction(pcup, dPcup)
    }

    private fun sphericalHarmonicVariables(spherical: SphericalCoordinate): SphericalHarmonicVariables {
        val relativeRadiusPower = DoubleArray(MAX_DEGREE + 1)
        val cosMLambda = DoubleArray(MAX_DEGREE + 1)
        val sinMLambda = DoubleArray(MAX_DEGREE + 1)
        val radiusRatio = EARTH_REFERENCE_RADIUS_KM / spherical.radiusKm
        val cosLambda = cos(Math.toRadians(spherical.longitudeDegrees))
        val sinLambda = sin(Math.toRadians(spherical.longitudeDegrees))

        relativeRadiusPower[0] = radiusRatio * radiusRatio
        for (n in 1..MAX_DEGREE) {
            relativeRadiusPower[n] = relativeRadiusPower[n - 1] * radiusRatio
        }

        cosMLambda[0] = 1.0
        sinMLambda[0] = 0.0
        cosMLambda[1] = cosLambda
        sinMLambda[1] = sinLambda
        for (m in 2..MAX_DEGREE) {
            cosMLambda[m] = cosMLambda[m - 1] * cosLambda - sinMLambda[m - 1] * sinLambda
            sinMLambda[m] = cosMLambda[m - 1] * sinLambda + sinMLambda[m - 1] * cosLambda
        }
        return SphericalHarmonicVariables(relativeRadiusPower, cosMLambda, sinMLambda)
    }

    private fun harmonicSummation(
        legendre: LegendreFunction,
        harmonic: SphericalHarmonicVariables,
        geocentricLatitudeDegrees: Double,
        decimalYear: Double
    ): MagneticField {
        var bx = 0.0
        var by = 0.0
        var bz = 0.0
        val yearsFromEpoch = decimalYear - MODEL_EPOCH
        for (n in 1..MAX_DEGREE) {
            for (m in 0..n) {
                val index = coefficientIndex(n, m)
                val g = coefficients.g[index] + yearsFromEpoch * coefficients.dg[index]
                val h = coefficients.h[index] + yearsFromEpoch * coefficients.dh[index]
                val fieldTerm = g * harmonic.cosMLambda[m] + h * harmonic.sinMLambda[m]
                bz -= harmonic.relativeRadiusPower[n] * fieldTerm * (n + 1).toDouble() * legendre.pcup[index]
                by += harmonic.relativeRadiusPower[n] *
                    (g * harmonic.sinMLambda[m] - h * harmonic.cosMLambda[m]) *
                    m.toDouble() *
                    legendre.pcup[index]
                bx -= harmonic.relativeRadiusPower[n] * fieldTerm * legendre.dPcup[index]
            }
        }
        val cosPhi = cos(Math.toRadians(geocentricLatitudeDegrees))
        if (abs(cosPhi) > 1.0e-10) {
            by /= cosPhi
        }
        return MagneticField(bx, by, bz)
    }

    private fun rotateToGeodetic(
        sphericalField: MagneticField,
        geocentricLatitudeDegrees: Double,
        geodeticLatitudeDegrees: Double
    ): MagneticField {
        val psi = Math.toRadians(geocentricLatitudeDegrees - geodeticLatitudeDegrees)
        return MagneticField(
            bx = sphericalField.bx * cos(psi) - sphericalField.bz * sin(psi),
            by = sphericalField.by,
            bz = sphericalField.bx * sin(psi) + sphericalField.bz * cos(psi)
        )
    }

    private fun coefficientIndex(n: Int, m: Int): Int =
        n * (n + 1) / 2 + m

    private data class SphericalCoordinate(
        val longitudeDegrees: Double,
        val geocentricLatitudeDegrees: Double,
        val radiusKm: Double
    )

    private data class LegendreFunction(
        val pcup: DoubleArray,
        val dPcup: DoubleArray
    )

    private data class SphericalHarmonicVariables(
        val relativeRadiusPower: DoubleArray,
        val cosMLambda: DoubleArray,
        val sinMLambda: DoubleArray
    )

    private data class MagneticField(
        val bx: Double,
        val by: Double,
        val bz: Double
    )

    private data class Coefficient(
        val n: Int,
        val m: Int,
        val g: Double,
        val h: Double,
        val dg: Double,
        val dh: Double
    )

    private data class WmmCoefficients(
        val g: DoubleArray,
        val h: DoubleArray,
        val dg: DoubleArray,
        val dh: DoubleArray
    ) {
        companion object {
            fun from(coefficients: List<Coefficient>): WmmCoefficients {
                val g = DoubleArray(NUM_TERMS)
                val h = DoubleArray(NUM_TERMS)
                val dg = DoubleArray(NUM_TERMS)
                val dh = DoubleArray(NUM_TERMS)
                coefficients.forEach { coefficient ->
                    val index = coefficientIndex(coefficient.n, coefficient.m)
                    g[index] = coefficient.g
                    h[index] = coefficient.h
                    dg[index] = coefficient.dg
                    dh[index] = coefficient.dh
                }
                return WmmCoefficients(g, h, dg, dh)
            }
        }
    }

    private val WMM2025_COEFFICIENTS = listOf(
        Coefficient(1, 0, -29351.8, 0.0, 12.0, 0.0),
        Coefficient(1, 1, -1410.8, 4545.4, 9.7, -21.5),
        Coefficient(2, 0, -2556.6, 0.0, -11.6, 0.0),
        Coefficient(2, 1, 2951.1, -3133.6, -5.2, -27.7),
        Coefficient(2, 2, 1649.3, -815.1, -8.0, -12.1),
        Coefficient(3, 0, 1361.0, 0.0, -1.3, 0.0),
        Coefficient(3, 1, -2404.1, -56.6, -4.2, 4.0),
        Coefficient(3, 2, 1243.8, 237.5, 0.4, -0.3),
        Coefficient(3, 3, 453.6, -549.5, -15.6, -4.1),
        Coefficient(4, 0, 895.0, 0.0, -1.6, 0.0),
        Coefficient(4, 1, 799.5, 278.6, -2.4, -1.1),
        Coefficient(4, 2, 55.7, -133.9, -6.0, 4.1),
        Coefficient(4, 3, -281.1, 212.0, 5.6, 1.6),
        Coefficient(4, 4, 12.1, -375.6, -7.0, -4.4),
        Coefficient(5, 0, -233.2, 0.0, 0.6, 0.0),
        Coefficient(5, 1, 368.9, 45.4, 1.4, -0.5),
        Coefficient(5, 2, 187.2, 220.2, 0.0, 2.2),
        Coefficient(5, 3, -138.7, -122.9, 0.6, 0.4),
        Coefficient(5, 4, -142.0, 43.0, 2.2, 1.7),
        Coefficient(5, 5, 20.9, 106.1, 0.9, 1.9),
        Coefficient(6, 0, 64.4, 0.0, -0.2, 0.0),
        Coefficient(6, 1, 63.8, -18.4, -0.4, 0.3),
        Coefficient(6, 2, 76.9, 16.8, 0.9, -1.6),
        Coefficient(6, 3, -115.7, 48.8, 1.2, -0.4),
        Coefficient(6, 4, -40.9, -59.8, -0.9, 0.9),
        Coefficient(6, 5, 14.9, 10.9, 0.3, 0.7),
        Coefficient(6, 6, -60.7, 72.7, 0.9, 0.9),
        Coefficient(7, 0, 79.5, 0.0, -0.0, 0.0),
        Coefficient(7, 1, -77.0, -48.9, -0.1, 0.6),
        Coefficient(7, 2, -8.8, -14.4, -0.1, 0.5),
        Coefficient(7, 3, 59.3, -1.0, 0.5, -0.8),
        Coefficient(7, 4, 15.8, 23.4, -0.1, 0.0),
        Coefficient(7, 5, 2.5, -7.4, -0.8, -1.0),
        Coefficient(7, 6, -11.1, -25.1, -0.8, 0.6),
        Coefficient(7, 7, 14.2, -2.3, 0.8, -0.2),
        Coefficient(8, 0, 23.2, 0.0, -0.1, 0.0),
        Coefficient(8, 1, 10.8, 7.1, 0.2, -0.2),
        Coefficient(8, 2, -17.5, -12.6, 0.0, 0.5),
        Coefficient(8, 3, 2.0, 11.4, 0.5, -0.4),
        Coefficient(8, 4, -21.7, -9.7, -0.1, 0.4),
        Coefficient(8, 5, 16.9, 12.7, 0.3, -0.5),
        Coefficient(8, 6, 15.0, 0.7, 0.2, -0.6),
        Coefficient(8, 7, -16.8, -5.2, -0.0, 0.3),
        Coefficient(8, 8, 0.9, 3.9, 0.2, 0.2),
        Coefficient(9, 0, 4.6, 0.0, -0.0, 0.0),
        Coefficient(9, 1, 7.8, -24.8, -0.1, -0.3),
        Coefficient(9, 2, 3.0, 12.2, 0.1, 0.3),
        Coefficient(9, 3, -0.2, 8.3, 0.3, -0.3),
        Coefficient(9, 4, -2.5, -3.3, -0.3, 0.3),
        Coefficient(9, 5, -13.1, -5.2, 0.0, 0.2),
        Coefficient(9, 6, 2.4, 7.2, 0.3, -0.1),
        Coefficient(9, 7, 8.6, -0.6, -0.1, -0.2),
        Coefficient(9, 8, -8.7, 0.8, 0.1, 0.4),
        Coefficient(9, 9, -12.9, 10.0, -0.1, 0.1),
        Coefficient(10, 0, -1.3, 0.0, 0.1, 0.0),
        Coefficient(10, 1, -6.4, 3.3, 0.0, 0.0),
        Coefficient(10, 2, 0.2, 0.0, 0.1, -0.0),
        Coefficient(10, 3, 2.0, 2.4, 0.1, -0.2),
        Coefficient(10, 4, -1.0, 5.3, -0.0, 0.1),
        Coefficient(10, 5, -0.6, -9.1, -0.3, -0.1),
        Coefficient(10, 6, -0.9, 0.4, 0.0, 0.1),
        Coefficient(10, 7, 1.5, -4.2, -0.1, 0.0),
        Coefficient(10, 8, 0.9, -3.8, -0.1, -0.1),
        Coefficient(10, 9, -2.7, 0.9, -0.0, 0.2),
        Coefficient(10, 10, -3.9, -9.1, -0.0, -0.0),
        Coefficient(11, 0, 2.9, 0.0, 0.0, 0.0),
        Coefficient(11, 1, -1.5, 0.0, -0.0, -0.0),
        Coefficient(11, 2, -2.5, 2.9, 0.0, 0.1),
        Coefficient(11, 3, 2.4, -0.6, 0.0, -0.0),
        Coefficient(11, 4, -0.6, 0.2, 0.0, 0.1),
        Coefficient(11, 5, -0.1, 0.5, -0.1, -0.0),
        Coefficient(11, 6, -0.6, -0.3, 0.0, -0.0),
        Coefficient(11, 7, -0.1, -1.2, -0.0, 0.1),
        Coefficient(11, 8, 1.1, -1.7, -0.1, -0.0),
        Coefficient(11, 9, -1.0, -2.9, -0.1, 0.0),
        Coefficient(11, 10, -0.2, -1.8, -0.1, 0.0),
        Coefficient(11, 11, 2.6, -2.3, -0.1, 0.0),
        Coefficient(12, 0, -2.0, 0.0, 0.0, 0.0),
        Coefficient(12, 1, -0.2, -1.3, 0.0, -0.0),
        Coefficient(12, 2, 0.3, 0.7, -0.0, 0.0),
        Coefficient(12, 3, 1.2, 1.0, -0.0, -0.1),
        Coefficient(12, 4, -1.3, -1.4, -0.0, 0.1),
        Coefficient(12, 5, 0.6, -0.0, -0.0, -0.0),
        Coefficient(12, 6, 0.6, 0.6, 0.1, -0.0),
        Coefficient(12, 7, 0.5, -0.1, -0.0, -0.0),
        Coefficient(12, 8, -0.1, 0.8, 0.0, 0.0),
        Coefficient(12, 9, -0.4, 0.1, 0.0, -0.0),
        Coefficient(12, 10, -0.2, -1.0, -0.1, -0.0),
        Coefficient(12, 11, -1.3, 0.1, -0.0, 0.0),
        Coefficient(12, 12, -0.7, 0.2, -0.1, -0.1)
    )
}
