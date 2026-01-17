package registry.cerbtk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin
import java.time.Instant
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

val cerbtk = Blockchain
private val mapper = jacksonObjectMapper()
private val nonceRequired: Boolean by lazy {
    System.getProperty("cerbtk.nonceRequired", "false").toBoolean()
}
private val dashboardPath: Path by lazy {
    Paths.get(System.getProperty("cerbtk.dashboardPath", "dashboard"))
}
private val samplePayloadPath: Path by lazy {
    Paths.get(System.getProperty("cerbtk.samplePayloadPath", "config/sample_payload.json"))
}

fun main(args: Array<String>) {
    val app = Javalin.start(23230)
    app.get("/dashboard") { ctx ->
        serveDashboardFile(ctx, "index.html", "text/html")
    }
    app.get("/dashboard/") { ctx ->
        serveDashboardFile(ctx, "index.html", "text/html")
    }
    app.get("/dashboard/styles.css") { ctx ->
        serveDashboardFile(ctx, "styles.css", "text/css")
    }
    app.get("/dashboard/app.js") { ctx ->
        serveDashboardFile(ctx, "app.js", "application/javascript")
    }
    app.get("/dashboard/sample") { ctx ->
        if (!Files.exists(samplePayloadPath)) {
            ctx.status(404).json(mapOf("error" to "sample_not_found"))
            return@get
        }
        ctx.contentType("application/json")
        ctx.result(Files.newInputStream(samplePayloadPath))
    }
    app.get("/device/all") { ctx ->
        ctx.json(cerbtk.chain)
    }
    app.get("/device/:id") { ctx ->
        val id = ctx.param("id")
        if (id == null) {
            ctx.status(400).json(mapOf("error" to "missing_id"))
            return@get
        }
        val block = cerbtk.findBlockByHash(id)
        if (block == null) {
            ctx.status(404).json(mapOf("error" to "not_found"))
        } else {
            ctx.json(block)
        }
    }
    app.get("/device/id/:deviceId") { ctx ->
        val deviceId = ctx.param("deviceId")
        if (deviceId == null) {
            ctx.status(400).json(mapOf("error" to "missing_device_id"))
            return@get
        }
        val block = cerbtk.findBlockByDeviceId(deviceId)
        if (block == null) {
            ctx.status(404).json(mapOf("error" to "not_found"))
        } else {
            ctx.json(block)
        }
    }
    app.get("/device/nonce/:deviceId") { ctx ->
        val deviceId = ctx.param("deviceId")
        if (deviceId == null) {
            ctx.status(400).json(mapOf("error" to "missing_device_id"))
            return@get
        }
        val entry = NonceStore.issue(deviceId)
        ctx.json(mapOf("deviceId" to deviceId, "nonce" to entry.nonce, "expiresAt" to entry.expiresAt.toString()))
    }
    app.get("/chain/validate") { ctx ->
        ctx.json(mapOf("valid" to cerbtk.isChainValid()))
    }
    app.get("/chain/head") { ctx ->
        val latest = cerbtk.latestBlock
        ctx.json(mapOf("index" to latest.index, "hash" to latest.hash))
    }
    app.get("/chain/anchor") { ctx ->
        val latest = cerbtk.latestBlock
        val anchor = "cerbtk:${latest.index}:${latest.hash}"
        ctx.json(mapOf("index" to latest.index, "hash" to latest.hash, "anchor" to anchor))
    }
    app.post("/device/write") { ctx ->
        val payload = ctx.body()
        if (payload.isBlank()) {
            ctx.status(400).json(mapOf("error" to "empty_payload"))
            return@post
        }
        if (!payload.trim().startsWith("{")) {
            ctx.status(400).json(mapOf("error" to "unsupported_payload"))
            return@post
        }

        val signedPayload = try {
            mapper.readValue(payload, SignedRegistrationPayload::class.java)
        } catch (ex: Exception) {
            ctx.status(400).json(mapOf("error" to "invalid_json"))
            return@post
        }

        val errors = validatePayload(signedPayload, nonceRequired)
        if (errors.isNotEmpty()) {
            ctx.status(400).json(mapOf("error" to "invalid_schema", "details" to errors))
            return@post
        }

        if (nonceRequired && !NonceStore.verify(signedPayload.deviceId, signedPayload.nonce!!)) {
            ctx.status(400).json(mapOf("error" to "invalid_nonce"))
            return@post
        }

        if (!verifySignedPayload(signedPayload)) {
            ctx.status(400).json(mapOf("error" to "invalid_signature"))
            return@post
        }

        val minedBlock = cerbtk.mineBlock(payload)
        cerbtk.indexDevice(signedPayload.deviceId, minedBlock.hash)
        ctx.json(minedBlock)
    }
}

private fun validatePayload(payload: SignedRegistrationPayload, requireNonce: Boolean): List<String> {
    val errors = mutableListOf<String>()
    if (payload.deviceId.isBlank()) errors.add("deviceId")
    if (payload.owner.isBlank()) errors.add("owner")
    if (payload.timestamp.isBlank()) {
        errors.add("timestamp")
    } else {
        try {
            Instant.parse(payload.timestamp)
        } catch (ex: Exception) {
            errors.add("timestamp_format")
        }
    }
    if (payload.signature.isBlank()) errors.add("signature")
    if (payload.publicKey.isBlank()) errors.add("publicKey")
    if (payload.firmwareHash.isNullOrBlank()) errors.add("firmwareHash")
    if (payload.buildId.isNullOrBlank()) errors.add("buildId")
    if (payload.recipe.isNullOrBlank()) errors.add("recipe")
    if (payload.boardRev.isNullOrBlank()) errors.add("boardRev")
    if (requireNonce && payload.nonce.isNullOrBlank()) errors.add("nonce")
    return errors
}

private fun serveDashboardFile(ctx: io.javalin.Context, fileName: String, contentType: String) {
    val path = dashboardPath.resolve(fileName)
    if (!Files.exists(path)) {
        ctx.status(404).result("Dashboard file not found: $fileName")
        return
    }
    ctx.contentType(contentType)
    ctx.result(Files.newInputStream(path))
}
