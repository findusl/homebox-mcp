package com.homebox.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
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

	suspend fun getLocationTree(): List<TreeItem> {
		val response = httpClient.get("$baseUrl/v1/locations/tree") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(LocationTreeSerializer, payload)
	}

	suspend fun createLocation(
		name: String,
		parentId: String? = null,
		description: String? = null,
	): LocationSummary {
		require(name.isNotBlank()) { "Location name must not be blank" }
		val response = httpClient.post("$baseUrl/v1/locations") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
			setBody(LocationCreateRequest(name = name, description = description, parentId = parentId))
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(LocationSummary.serializer(), payload)
	}

	private companion object {
		val LocationsSerializer = ListSerializer(Location.serializer())
		val LocationTreeSerializer = ListSerializer(TreeItem.serializer())
	}
}

@Serializable
data class Location(
	val id: String,
	val name: String,
	val description: String? = null,
	@SerialName("itemCount") val itemCount: Int? = null,
)

@Serializable
data class LocationSummary(
	val id: String,
	val name: String,
	val description: String? = null,
)

@Serializable
data class TreeItem(
	val id: String,
	val name: String,
	val type: String,
	val children: List<TreeItem> = emptyList(),
)

@Serializable
private data class LocationCreateRequest(
	val name: String,
	val description: String? = null,
	val parentId: String? = null,
)
