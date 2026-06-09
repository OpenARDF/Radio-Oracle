package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesktopVenueElevationCacheTest {
    @Test
    fun prefersLidarDtmCacheOverFinerUsgsCache() {
        withTemporaryUserHome { home ->
            val cacheDirectory = home
                .resolve("Library")
                .resolve("Application Support")
                .resolve("Radio-Oracle")
                .resolve("elevations")
            Files.createDirectories(cacheDirectory)
            writeCache(
                path = cacheDirectory.resolve("a-usgs-1m.roelev.json"),
                sourceName = "USGS 3DEP",
                resolutionMeters = 1.0,
                elevationMeters = 10.0
            )
            writeCache(
                path = cacheDirectory.resolve("b-oregon-dogami-3m.roelev.json"),
                sourceName = "Oregon DOGAMI LiDAR DTM",
                resolutionMeters = 3.0,
                elevationMeters = 20.0
            )

            assertEquals(
                20.0,
                DesktopVenueElevationCache.elevationMeters(CourseGeoPoint(latitude = 45.0, longitude = -122.0)) ?: -1.0,
                0.001
            )
        }
    }

    @Test
    fun usesFinerResolutionWithinSameSourceTier() {
        withTemporaryUserHome { home ->
            val cacheDirectory = home
                .resolve("Library")
                .resolve("Application Support")
                .resolve("Radio-Oracle")
                .resolve("elevations")
            Files.createDirectories(cacheDirectory)
            writeCache(
                path = cacheDirectory.resolve("a-oregon-dogami-3m.roelev.json"),
                sourceName = "Oregon DOGAMI LiDAR DTM",
                resolutionMeters = 3.0,
                elevationMeters = 30.0
            )
            writeCache(
                path = cacheDirectory.resolve("b-wa-dnr-1m.roelev.json"),
                sourceName = "Washington DNR LiDAR DTM - Demo",
                resolutionMeters = 1.0,
                elevationMeters = 40.0
            )

            assertEquals(
                40.0,
                DesktopVenueElevationCache.elevationMeters(CourseGeoPoint(latitude = 45.0, longitude = -122.0)) ?: -1.0,
                0.001
            )
        }
    }

    @Test
    fun reportsAnalysisSourceNotesForSelectedCacheFile() {
        withTemporaryUserHome { home ->
            val cacheDirectory = home
                .resolve("Library")
                .resolve("Application Support")
                .resolve("Radio-Oracle")
                .resolve("elevations")
            Files.createDirectories(cacheDirectory)
            writeCache(
                path = cacheDirectory.resolve("a-usgs-1m.roelev.json"),
                sourceName = "USGS 3DEP",
                resolutionMeters = 1.0,
                elevationMeters = 10.0
            )
            writeCache(
                path = cacheDirectory.resolve("b-oregon-dogami-3m.roelev.json"),
                sourceName = "Oregon DOGAMI LiDAR DTM",
                resolutionMeters = 3.0,
                elevationMeters = 20.0
            )

            assertEquals(
                listOf("Elevation cache: Test Venue - Oregon DOGAMI LiDAR DTM, 3 m grid (b-oregon-dogami-3m.roelev.json)"),
                DesktopVenueElevationCache.analysisSourceNotes(
                    listOf(CourseGeoPoint(latitude = 45.0, longitude = -122.0))
                )
            )
        }
    }

    @Test
    fun reportsWhenNoAnalysisCacheMatchesRoutePoints() {
        withTemporaryUserHome { home ->
            val cacheDirectory = home
                .resolve("Library")
                .resolve("Application Support")
                .resolve("Radio-Oracle")
                .resolve("elevations")
            Files.createDirectories(cacheDirectory)
            writeCache(
                path = cacheDirectory.resolve("a-usgs-1m.roelev.json"),
                sourceName = "USGS 3DEP",
                resolutionMeters = 1.0,
                elevationMeters = 10.0
            )

            assertEquals(
                listOf("Elevation cache: no local cache file matched the route/profile points."),
                DesktopVenueElevationCache.analysisSourceNotes(
                    listOf(CourseGeoPoint(latitude = 40.0, longitude = -120.0))
                )
            )
        }
    }

    private fun withTemporaryUserHome(block: (Path) -> Unit) {
        val originalHome = System.getProperty("user.home")
        val home = Files.createTempDirectory("radio-oracle-home")
        try {
            System.setProperty("user.home", home.toString())
            block(home)
        } finally {
            System.setProperty("user.home", originalHome)
            home.toFile().deleteRecursively()
        }
    }

    private fun writeCache(
        path: Path,
        sourceName: String,
        resolutionMeters: Double,
        elevationMeters: Double
    ) {
        Files.writeString(
            path,
            """
            {
              "metadata": {
                "version": 1,
                "venueName": "Test Venue",
                "sourceName": "$sourceName",
                "sourceUrl": "test",
                "resolutionMeters": $resolutionMeters,
                "rowCount": 2,
                "columnCount": 2,
                "boundingBox": {
                  "minLatitude": 44.9,
                  "maxLatitude": 45.1,
                  "minLongitude": -122.1,
                  "maxLongitude": -121.9
                },
                "createdAtIso": "2026-06-08T00:00:00Z"
              },
              "elevations": [$elevationMeters, $elevationMeters, $elevationMeters, $elevationMeters]
            }
            """.trimIndent()
        )
    }
}
