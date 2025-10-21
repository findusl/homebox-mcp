package com.homebox.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered

fun main() {
	val baseUrl = System.getenv("HOMEBOX_BASE_URL")?.takeIf { it.isNotBlank() }
		?: error("HOMEBOX_BASE_URL environment variable must be set")
	val apiToken = System.getenv("HOMEBOX_API_TOKEN")?.takeIf { it.isNotBlank() }
		?: error("HOMEBOX_API_TOKEN environment variable must be set")

	val httpClient = HttpClient(CIO) {
		install(ContentNegotiation) {
			json()
		}
		defaultRequest {
			contentType(ContentType.Application.Json)
		}
	}

	val client = HomeboxClient(httpClient, baseUrl, apiToken)
	val server = createHomeboxServer(client)
	val transport = StdioServerTransport(System.`in`.asInput(), System.out.asSink().buffered())

	try {
		runBlocking {
			server.connect(transport)
			awaitCancellation()
		}
	} finally {
		runBlocking { server.close() }
		httpClient.close()
	}
}
