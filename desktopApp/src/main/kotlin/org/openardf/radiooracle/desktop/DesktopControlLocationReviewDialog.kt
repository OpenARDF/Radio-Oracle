package org.openardf.radiooracle.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DesktopControlLocationReviewDialog(
    review: DesktopControlLocationReview,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    DesktopAlertDialog(
        onDismissRequest = onReject,
        modifier = Modifier.testTag("control-location-review"),
        title = { Text("Review Control Location Change") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${review.controlLabel}: " +
                        "${review.previousLatitude.decimalText()}, ${review.previousLongitude.decimalText()} → " +
                        "${review.updatedLatitude.decimalText()}, ${review.updatedLongitude.decimalText()}"
                )
                Text(
                    "The following calculated routes and measurements will replace the current course data. " +
                        "Accept applies the same revised course data used by reports, results, exports, and Course Analyzer."
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    review.courseChanges.forEach { change ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                change.categoryName + if (change.usesMovedControl) " — uses this control" else " — recalculated with race design",
                                style = MaterialTheme.typography.subtitle1
                            )
                            Text("Ideal order: ${orderText(change.previousIdealOrder)} → ${orderText(change.updatedIdealOrder)}")
                            Text("Horizontal length: ${metricChange(change.previousHorizontalLengthMeters, change.updatedHorizontalLengthMeters)}")
                            Text("Climb: ${metricChange(change.previousClimbMeters, change.updatedClimbMeters)}")
                            Text("Effective length: ${metricChange(change.previousEffectiveLengthMeters, change.updatedEffectiveLengthMeters)}")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("Reject") }
        },
        confirmButton = {
            Button(onClick = onAccept, modifier = Modifier.testTag("accept-control-location")) {
                Text("Accept Changes")
            }
        }
    )
}

private fun metricChange(previous: Int?, updated: Int?): String =
    "${metricText(previous)} → ${metricText(updated)}"

private fun metricText(value: Int?): String = value?.let { "$it m" } ?: "Unavailable"

private fun orderText(value: String): String = value.ifBlank { "Unavailable" }
