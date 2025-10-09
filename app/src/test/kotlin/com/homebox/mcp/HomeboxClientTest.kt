package com.homebox.mcp

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeboxClientTest {
	@Test
	fun `listLocations requests and parses response`() =
		runTest {
			var capturedRequest: HttpRequestData? = null
			val engine = MockEngine { request ->
				capturedRequest = request
				respond(
					content = ByteReadChannel(
						"""[{"id":"1","name":"Kitchen","itemCount":4}]""",
					),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine) {
				install(ContentNegotiation) {
					json(Json { ignoreUnknownKeys = true })
				}
			}

			val client = HomeboxClient(httpClient, "https://example.test", "token")

			val locations = client.listLocations()

			val request = requireNotNull(capturedRequest)
			assertEquals("/v1/locations", request.url.encodedPath)
			assertEquals("Bearer token", request.headers[HttpHeaders.Authorization])
			assertEquals(1, locations.size)
			assertEquals("Kitchen", locations.first().name)
			assertEquals(4, locations.first().itemCount)
		}

	@Test
	fun `listLocations forwards filter parameter`() =
		runTest {
			var capturedUrl: Url? = null
			val engine = MockEngine { request ->
				capturedUrl = request.url
				respond(
					content = ByteReadChannel("[]"),
					status = HttpStatusCode.OK,
					headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
				)
			}

			val httpClient = HttpClient(engine)
			val client = HomeboxClient(httpClient, "https://example.test", "token")

			client.listLocations(filterChildren = true)

			val url = requireNotNull(capturedUrl)
			assertEquals("true", url.parameters["filterChildren"])
		}

	@Test
	fun `constructor rejects blank configuration`() {
		val httpClient = HttpClient(MockEngine { error("unused") })

		assertThrows(IllegalArgumentException::class.java) {
			HomeboxClient(httpClient, "", "token")
		}

		assertThrows(IllegalArgumentException::class.java) {
			HomeboxClient(httpClient, "https://example.test", " ")
		}
	}
}
