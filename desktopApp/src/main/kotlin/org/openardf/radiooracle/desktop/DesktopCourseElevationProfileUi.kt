package org.openardf.radiooracle.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun CourseAnalysisElevationProfile(
    title: String,
    profile: List<DesktopCourseElevationProfilePoint>,
    markers: List<DesktopCourseElevationProfileMarker>,
    modifier: Modifier = Modifier.width(620.dp),
    chartHeight: Dp = 180.dp,
    markerMaxLines: Int = 2
) {
    if (profile.isEmpty()) {
        return
    }
    val minElevation = profile.minOf { it.elevationMeters }
    val maxElevation = profile.maxOf { it.elevationMeters }
    val totalDistanceMeters = profile.lastOrNull()?.distanceMeters ?: 0
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = DesktopPalette.Black,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "0.00 km to ${twoDecimalText(totalDistanceMeters / 1000.0)} km, " +
                "${minElevation.roundToInt()} m to ${maxElevation.roundToInt()} m",
            color = DesktopPalette.Black,
            fontSize = 13.sp
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .border(1.dp, DesktopPalette.LightGrey)
                .padding(8.dp)
        ) {
            val leftPadding = 36f
            val rightPadding = 10f
            val topPadding = 12f
            val bottomPadding = 24f
            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding
            if (chartWidth <= 0f || chartHeight <= 0f) {
                return@Canvas
            }
            val elevationRange = max(1.0, maxElevation - minElevation)
            val distanceRange = max(1.0, totalDistanceMeters.toDouble())
            fun xFor(distanceMeters: Int): Float =
                leftPadding + (distanceMeters / distanceRange).toFloat() * chartWidth
            fun yFor(elevationMeters: Double): Float =
                topPadding + ((maxElevation - elevationMeters) / elevationRange).toFloat() * chartHeight

            repeat(4) { index ->
                val fraction = index / 3f
                val y = topPadding + fraction * chartHeight
                drawLine(
                    color = DesktopPalette.LightGrey,
                    start = Offset(leftPadding, y),
                    end = Offset(leftPadding + chartWidth, y),
                    strokeWidth = 1f
                )
            }
            drawLine(
                color = DesktopPalette.Disconnected,
                start = Offset(leftPadding, topPadding),
                end = Offset(leftPadding, topPadding + chartHeight),
                strokeWidth = 1.5f
            )
            drawLine(
                color = DesktopPalette.Disconnected,
                start = Offset(leftPadding, topPadding + chartHeight),
                end = Offset(leftPadding + chartWidth, topPadding + chartHeight),
                strokeWidth = 1.5f
            )
            profile.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = DesktopPalette.Primary,
                    start = Offset(xFor(start.distanceMeters), yFor(start.elevationMeters)),
                    end = Offset(xFor(end.distanceMeters), yFor(end.elevationMeters)),
                    strokeWidth = 3f
                )
            }
            markers.forEach { marker ->
                drawCircle(
                    color = DesktopPalette.Warning,
                    radius = 4.5f,
                    center = Offset(xFor(marker.distanceMeters), yFor(marker.elevationMeters))
                )
            }
        }
        val markerText = markers.takeIf { it.isNotEmpty() }
            ?.joinToString("  ") { "${it.label} ${twoDecimalText(it.distanceMeters / 1000.0)} km" }
        markerText?.let {
            Text(
                text = it,
                color = DesktopPalette.Black,
                fontSize = 11.sp,
                maxLines = markerMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
