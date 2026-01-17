package registry.cerbtk

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.javalin.Javalin

val cerbtk = Blockchain
private val mapper = jacksonObjectMapper()

fun main(args: Array<String>) {
    val app = Javalin.start(23230)
    app.get("/device/all") { ctx ->
        ctx.json(cerbtk.chain)
    }
    app.get("/device/{id}") { ctx ->
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
    app.get("/chain/validate") { ctx ->
        ctx.json(mapOf("valid" to cerbtk.isChainValid()))
    }
    app.get("/chain/head") { ctx ->
        val latest = cerbtk.latestBlock
        ctx.json(mapOf("index" to latest.index, "hash" to latest.hash))
    }
    app.post("/device/write") { ctx ->
        val payload = ctx.body()
        if (payload.isBlank()) {
            ctx.status(400).json(mapOf("error" to "empty_payload"))
            return@post
        }
        val minedBlock = if (payload.trim().startsWith("{")) {
            val signedPayload = try {
                mapper.readValue(payload, SignedRegistrationPayload::class.java)
            } catch (ex: Exception) {
                null
            }
            if (signedPayload != null) {
                if (!verifySignedPayload(signedPayload)) {
                    ctx.status(400).json(mapOf("error" to "invalid_signature"))
                    return@post
                }
                cerbtk.mineBlock(payload)
            } else {
                cerbtk.mineBlock(payload)
            }
        } else {
            cerbtk.mineBlock(payload)
        }
        ctx.json(minedBlock)
    }
}
