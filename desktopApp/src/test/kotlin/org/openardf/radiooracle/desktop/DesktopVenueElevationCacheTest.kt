package org.openardf.radiooracle.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DesktopVenueElevationCacheTest {
    @Test
    fun detectsExplicitGdalFootUnits() {
        val units = desktopGdalElevationUnitsFromInfo(
            """
            Band 1 Block=128x128 Type=Float32, ColorInterp=Gray
              Unit Type: US survey foot
            """.trimIndent()
        )

        assertEquals("US survey foot", units.label)
        assertEquals(1200.0 / 3937.0, units.valueMultiplier, 0.0000001)
    }

    @Test
    fun keepsExplicitGdalMeterUnitsWhenFilenameMentionsFeet() {
        val units = desktopGdalElevationUnitsFromInfo(
            """
            Coordinate System is:
            PROJCRS["NAD83(2011) / North Carolina (ftUS)",
                CS[Cartesian,2],
                    AXIS["easting",east,
                        LENGTHUNIT["US survey foot",0.304800609601219]]]
            Band 1 Block=128x128 Type=Float32, ColorInterp=Gray
              Unit Type: metre
            """.trimIndent(),
            "/vsizip//Downloads/Stanly_2016_QL1_03ft_CountywideRaster.zip/Stanly_Ground_3ft.tif"
        )

        assertEquals("meter", units.label)
        assertEquals(1.0, units.valueMultiplier, 0.0)
    }

    @Test
    fun infersUsSurveyFeetFromProjectedCrsWhenBandUnitIsMissing() {
        val units = desktopGdalElevationUnitsFromInfo(
            """
            Coordinate System is:
            PROJCRS["NAD83(2011) / North Carolina (ftUS)",
                CS[Cartesian,2],
                    AXIS["easting",east,
                        LENGTHUNIT["US survey foot",0.304800609601219]],
                    AXIS["northing",north,
                        LENGTHUNIT["US survey foot",0.304800609601219]]]
            Band 1 Block=128x128 Type=Float32, ColorInterp=Gray
            """.trimIndent(),
            "/vsizip//Downloads/Stanly_2016_QL1_03ft_CountywideRaster.zip/Stanly_Ground_3ft.tif"
        )

        assertEquals("US survey foot (inferred)", units.label)
        assertEquals(1200.0 / 3937.0, units.valueMultiplier, 0.0000001)
    }

    @Test
    fun infersFeetFromRasterFilenameWhenMetadataIsSilent() {
        val units = desktopGdalElevationUnitsFromInfo(
            """
            Coordinate System is:
            GEOGCRS["WGS 84"]
            Band 1 Block=128x128 Type=Float32, ColorInterp=Gray
            """.trimIndent(),
            "/Downloads/local/venue_3ft_dem.tif"
        )

        assertEquals("foot (inferred)", units.label)
        assertEquals(0.3048, units.valueMultiplier, 0.0000001)
    }

    @Test
    fun recognizesLocalElevationSourceTypes() {
        assertEquals(LocalElevationSourceType.GeoTiff, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.tif"))
        assertEquals(LocalElevationSourceType.GeoTiff, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.TIFF"))
        assertEquals(LocalElevationSourceType.GeoTiffZip, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.zip"))
        assertEquals(LocalElevationSourceType.LasPointCloud, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.las"))
        assertEquals(LocalElevationSourceType.LasPointCloud, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.LAZ"))
        assertEquals(null, DesktopVenueElevationCache.desktopLocalElevationSourceType("venue.txt"))
    }

    @Test
    fun parsesMultipleLocalElevationSourcePaths() {
        assertEquals(
            listOf("/data/a.laz", "/data/b.laz"),
            DesktopVenueElevationCache.desktopLocalElevationSourcePathTexts("/data/a.laz; /data/b.laz")
        )
        assertEquals(
            listOf("/data/a.laz", "/data/b.laz"),
            DesktopVenueElevationCache.desktopLocalElevationSourcePathTexts("/data/a.laz\n/data/b.laz")
        )
        assertEquals(
            listOf(LocalElevationSourceType.LasPointCloud, LocalElevationSourceType.LasPointCloud),
            DesktopVenueElevationCache.desktopLocalElevationSourceTypes("/data/a.laz; /data/b.LAS")
        )
    }

    @Test
    fun buildsPdalMergePipelineForLasPointCloudRasterization() {
        val pipeline = desktopPdalLasPointCloudRasterPipeline(
            sourcePaths = listOf(Path.of("/data/source-a.laz"), Path.of("/data/source-b.laz")),
            outputRaster = Path.of("/tmp/output.tif"),
            resolutionMeters = 2.5
        )

        assertTrue(pipeline.contains("source-a.laz"))
        assertTrue(pipeline.contains("source-b.laz"))
        assertTrue(pipeline.contains("readers.las"))
        assertTrue(pipeline.contains("filters.merge"))
        assertTrue(pipeline.contains("filters.reprojection"))
        assertTrue(pipeline.contains("EPSG:3857"))
        assertTrue(!pipeline.contains("filters.crop"))
        assertTrue(pipeline.contains("writers.gdal"))
        assertTrue(pipeline.contains("\"resolution\":2.5"))
        assertTrue(pipeline.contains("\"output_type\":\"idw\""))
    }

    @Test
    fun parsesGdalWgs84ExtentBoundingBox() {
        val boundingBox = desktopGdalWgs84BoundingBoxFromInfo(
            """
            {
              "wgs84Extent": {
                "type": "Polygon",
                "coordinates": [[
                  [-122.10, 44.90],
                  [-122.10, 45.20],
                  [-121.80, 45.20],
                  [-121.80, 44.90],
                  [-122.10, 44.90]
                ]]
              }
            }
            """.trimIndent()
        )

        assertEquals(44.90, boundingBox.minLatitude, 0.000001)
        assertEquals(45.20, boundingBox.maxLatitude, 0.000001)
        assertEquals(-122.10, boundingBox.minLongitude, 0.000001)
        assertEquals(-121.80, boundingBox.maxLongitude, 0.000001)
    }

    @Test
    fun parsesPdalStacTwoDimensionalBoundingBox() {
        val boundingBox = desktopPdalStacWgs84BoundingBoxFromInfo(
            """
            {
              "type": "Feature",
              "bbox": [-122.30, 44.80, -121.70, 45.40]
            }
            """.trimIndent()
        )

        assertEquals(44.80, boundingBox.minLatitude, 0.000001)
        assertEquals(45.40, boundingBox.maxLatitude, 0.000001)
        assertEquals(-122.30, boundingBox.minLongitude, 0.000001)
        assertEquals(-121.70, boundingBox.maxLongitude, 0.000001)
    }

    @Test
    fun parsesPdalStacThreeDimensionalBoundingBox() {
        val boundingBox = desktopPdalStacWgs84BoundingBoxFromInfo(
            """
            {
              "type": "Feature",
              "bbox": [-122.30, 44.80, 95.0, -121.70, 45.40, 180.0]
            }
            """.trimIndent()
        )

        assertEquals(44.80, boundingBox.minLatitude, 0.000001)
        assertEquals(45.40, boundingBox.maxLatitude, 0.000001)
        assertEquals(-122.30, boundingBox.minLongitude, 0.000001)
        assertEquals(-121.70, boundingBox.maxLongitude, 0.000001)
    }

    @Test
    fun parsesPdalStacBoundingBoxAfterWarningPrefix() {
        val boundingBox = desktopPdalStacWgs84BoundingBoxFromInfo(
            """
            (pdal info readers.copc Warning) COPC source reported a non-fatal metadata warning.
            {
              "type": "Feature",
              "bbox": [-122.30, 44.80, -121.70, 45.40]
            }
            """.trimIndent()
        )

        assertEquals(44.80, boundingBox.minLatitude, 0.000001)
        assertEquals(45.40, boundingBox.maxLatitude, 0.000001)
        assertEquals(-122.30, boundingBox.minLongitude, 0.000001)
        assertEquals(-121.70, boundingBox.maxLongitude, 0.000001)
    }

    @Test
    fun acceptsCompleteGdalLocationInfoOutputWhenExitCodeReportsEdgeNoData() {
        assertTrue(gdalLocationInfoExitIsAcceptable(exitCode = 0, error = "", outputLineCount = 10, pointCount = 10))
        assertTrue(gdalLocationInfoExitIsAcceptable(exitCode = 1, error = "", outputLineCount = 10, pointCount = 10))
        assertTrue(!gdalLocationInfoExitIsAcceptable(exitCode = 1, error = "failed", outputLineCount = 10, pointCount = 10))
        assertTrue(!gdalLocationInfoExitIsAcceptable(exitCode = 1, error = "", outputLineCount = 9, pointCount = 10))
        assertTrue(!gdalLocationInfoExitIsAcceptable(exitCode = 2, error = "", outputLineCount = 10, pointCount = 10))
    }

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
    fun prefersNorthCarolinaLidarDemCacheOverFinerUsgsCache() {
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
                path = cacheDirectory.resolve("b-nc-state-portal-3m.roelev.json"),
                sourceName = "NC State University Libraries / GIS Portal LiDAR",
                resolutionMeters = 3.0,
                elevationMeters = 30.0
            )

            assertEquals(
                30.0,
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

    @Test
    fun importsValidatedDemJsonFilesIntoCacheDirectory() {
        withTemporaryUserHome { home ->
            val sourceDirectory = home.resolve("Downloads")
            Files.createDirectories(sourceDirectory)
            val sourcePath = sourceDirectory.resolve("billy-bob.json")
            writeCache(
                path = sourcePath,
                sourceName = "Oregon DOGAMI LiDAR DTM",
                resolutionMeters = 3.0,
                elevationMeters = 200.0
            )

            val review = DesktopVenueElevationCache.reviewDemFileImport(listOf(sourcePath))
            val summary = DesktopVenueElevationCache.importReviewedDemFiles(review)

            assertEquals(1, review.importableCount)
            assertEquals(0, review.issues.size)
            assertEquals(0, review.overwriteCount)
            assertEquals(1, summary.importedCount)
            assertTrue(Files.exists(summary.targetDirectory.resolve("billy-bob.roelev.json")))
            assertEquals(listOf("Test Venue"), DesktopVenueElevationCache.listings().map { it.venueName })
        }
    }

    @Test
    fun importsValidDemJsonFilesFromZipAndReportsInvalidEntries() {
        withTemporaryUserHome { home ->
            val sourceDirectory = home.resolve("Downloads")
            Files.createDirectories(sourceDirectory)
            val zipPath = sourceDirectory.resolve("oregon-dem.zip")
            ZipOutputStream(Files.newOutputStream(zipPath)).use { zip ->
                zip.putNextEntry(ZipEntry("white-river-west.json"))
                zip.write(cacheJson("Oregon DOGAMI LiDAR DTM", 3.0, 300.0).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("not-a-cache.json"))
                zip.write("""{"not":"a Radio-Oracle DEM cache"}""".toByteArray())
                zip.closeEntry()
            }

            val review = DesktopVenueElevationCache.reviewDemFileImport(listOf(zipPath))
            val summary = DesktopVenueElevationCache.importReviewedDemFiles(review)

            assertEquals(1, review.importableCount)
            assertEquals(1, review.issues.size)
            assertEquals(1, summary.importedCount)
            assertTrue(Files.exists(summary.targetDirectory.resolve("white-river-west.roelev.json")))
        }
    }

    @Test
    fun warnsWhenImportedDemFileWouldOverwriteCachedVenueFile() {
        withTemporaryUserHome { home ->
            val cacheDirectory = home
                .resolve("Library")
                .resolve("Application Support")
                .resolve("Radio-Oracle")
                .resolve("elevations")
            Files.createDirectories(cacheDirectory)
            writeCache(
                path = cacheDirectory.resolve("skyline.roelev.json"),
                sourceName = "USGS 3DEP",
                resolutionMeters = 10.0,
                elevationMeters = 100.0
            )
            val sourceDirectory = home.resolve("Downloads")
            Files.createDirectories(sourceDirectory)
            val sourcePath = sourceDirectory.resolve("skyline.json")
            writeCache(
                path = sourcePath,
                sourceName = "Oregon DOGAMI LiDAR DTM",
                resolutionMeters = 3.0,
                elevationMeters = 400.0
            )

            val review = DesktopVenueElevationCache.reviewDemFileImport(listOf(sourcePath))
            val summary = DesktopVenueElevationCache.importReviewedDemFiles(review)

            assertEquals(1, review.importableCount)
            assertEquals(1, review.overwriteCount)
            assertEquals(1, summary.overwrittenCount)
            assertEquals(
                listOf("Oregon DOGAMI LiDAR DTM"),
                DesktopVenueElevationCache.listings().map { it.sourceName }
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
            cacheJson(sourceName, resolutionMeters, elevationMeters)
        )
    }

    private fun cacheJson(
        sourceName: String,
        resolutionMeters: Double,
        elevationMeters: Double
    ): String =
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
}
