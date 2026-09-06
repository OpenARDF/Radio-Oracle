package org.openardf.radiooracle.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.openardf.radiooracle.shared.event.CourseWorkflowAudit
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path

/** CLI adapter around production readers and shared auditing. Never writes the source race/archive. */
internal object DesktopCourseWorkflowCommands {
    /** Characterization tests can pass while acceptance still fails; never hide that distinction. */
    fun verifyReports(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isEmpty()) {
            err.println("course-workflow-report requires at least one workflow report path.")
            return 2
        }
        return try {
            var passed = true
            args.forEach { argument ->
                val report = Json.parseToJsonElement(Files.readString(Path.of(argument))).jsonObject
                val steps = report.getValue("steps").jsonArray
                require(steps.isNotEmpty())
                val statuses = steps.map { it.jsonObject.getValue("status").jsonPrimitive.content }
                require(statuses.all { it in setOf("passed", "failed", "blocked", "skipped") })
                val ready = statuses.all { it == "passed" }
                passed = passed && ready
                out.println("${report.getValue("scenario").jsonPrimitive.content}: ${if (ready) "passed" else "incomplete"} (${statuses.count { it == "passed" }}/${steps.size} steps passed)")
            }
            if (passed) 0 else 1
        } catch (e: Exception) {
            err.println("Workflow evidence could not be verified (${e::class.simpleName}).")
            2
        }
    }

    fun publicationManifest(args: List<String>, out: PrintStream, err: PrintStream): Int = try {
        require(args.size == 2) { "Expected site directory and a new inventory file." }
        val root = Path.of(args[0]).toAbsolutePath().normalize()
        val target = Path.of(args[1]).toAbsolutePath().normalize()
        require(!target.startsWith(root)) { "Keep verification inventories outside the public site." }
        val manifest = org.openardf.radiooracle.shared.publicresults.PublicResultsArtifactVerification.manifest(
            DesktopCloudflarePagesSiteReader.read(root).readFrozenSite())
        Files.writeString(target, Json { prettyPrint = true }.encodeToString(manifest), java.nio.file.StandardOpenOption.CREATE_NEW)
        out.println("Saved a verified inventory of ${manifest.artifacts.size} public artifacts.")
        0
    } catch (error: Exception) {
        err.println("Public inventory could not be created (${error::class.simpleName}).")
        2
    }

    fun publicationVerify(args: List<String>, out: PrintStream, err: PrintStream): Int = try {
        require(args.size == 2) { "Expected public URL and inventory file." }
        val base = java.net.URI(args[0].trimEnd('/') + "/")
        require(base.scheme == "https" && base.host != null && base.userInfo == null && base.query == null && base.fragment == null)
        val manifest = Json.decodeFromString<org.openardf.radiooracle.shared.publicresults.PublicResultsArtifactManifest>(Files.readString(Path.of(args[1])))
        val transport = JavaDesktopCloudflarePagesHttpTransport()
        val failures = org.openardf.radiooracle.shared.publicresults.PublicResultsArtifactVerification.verify(manifest) { path ->
            val url = base.resolve(java.net.URI(null, null, path, "roverify=${java.util.UUID.randomUUID()}", null))
            val response = transport.send(DesktopCloudflarePagesHttpRequest("GET", url,
                mapOf("Cache-Control" to "no-cache, no-store", "Pragma" to "no-cache")))
            require(response.statusCode in 200..299)
            response.bodyBytes
        }
        out.println("Fresh public verification: ${if (failures.isEmpty()) "passed" else "failed"} (${manifest.artifacts.size - failures.size}/${manifest.artifacts.size} artifacts).")
        if (failures.isEmpty()) 0 else 1
    } catch (error: Exception) {
        err.println("Public verification could not run (${error::class.simpleName}).")
        2
    }

    fun audit(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.size != 1 || args.first().startsWith("--")) {
            err.println("course-audit requires one Race File or .roseries path.")
            return 2
        }
        return try {
            val path = Path.of(args.single())
            val projects = readCourseWorkflowProjects(path)
            val reports = projects.map { CourseWorkflowAudit.audit(it.raceData) }
            out.println(Json { prettyPrint = true }.encodeToString(reports))
            if (reports.isNotEmpty() && reports.all { it.status == "passed" }) 0 else 1
        } catch (e: Exception) {
            // Decoder errors can contain course payload fragments; do not echo them to ordinary logs.
            err.println("Course audit could not read this file (${e::class.simpleName}).")
            2
        }
    }
}
