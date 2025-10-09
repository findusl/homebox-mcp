package com.homebox.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class HomeboxClient(
	private val httpClient: HttpClient,
	private val baseUrl: String,
	private val apiToken: String,
	private val json: Json = Json { ignoreUnknownKeys = true },
) {
	init {
		require(baseUrl.isNotBlank()) { "Homebox base URL must not be blank" }
		require(apiToken.isNotBlank()) { "Homebox API token must not be blank" }
	}

	suspend fun listLocations(filterChildren: Boolean? = null): List<Location> {
		val response = httpClient.get("$baseUrl/v1/locations") {
			filterChildren?.let { parameter("filterChildren", it) }
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(LocationsSerializer, payload)
	}

	private companion object {
		val LocationsSerializer = ListSerializer(Location.serializer())
	}
}

@Serializable
data class Location(
	val id: String,
	val name: String,
	val description: String? = null,
	@SerialName("itemCount") val itemCount: Int? = null,
)
