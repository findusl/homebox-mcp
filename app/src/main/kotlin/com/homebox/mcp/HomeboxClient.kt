package com.homebox.mcp

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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

	suspend fun getLocationTree(withItems: Boolean = false): List<TreeItem> {
		val response = httpClient.get("$baseUrl/v1/locations/tree") {
			if (withItems) {
				parameter("withItems", true)
			}
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

	suspend fun listItems(
		page: Int = 1,
		locationIds: List<String>? = null,
		pageSize: Int = 100,
	): ItemPage {
		val response = httpClient.get("$baseUrl/v1/items") {
			parameter("page", page)
			parameter("pageSize", pageSize)
			locationIds?.forEach { parameter("locations", it) }
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(ItemPage.serializer(), payload)
	}

	suspend fun getLocation(id: String): LocationDetails {
		val response = httpClient.get("$baseUrl/v1/locations/$id") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(LocationDetails.serializer(), payload)
	}

	suspend fun createItem(
		name: String,
		locationId: String,
		description: String? = null,
	): ItemSummary {
		require(name.isNotBlank()) { "Item name must not be blank" }
		require(locationId.isNotBlank()) { "Location ID must not be blank" }

		val response = httpClient.post("$baseUrl/v1/items") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
			setBody(
				ItemCreateRequest(
					name = name,
					description = description,
					locationId = locationId,
				),
			)
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(ItemSummary.serializer(), payload)
	}

	suspend fun updateItemQuantity(itemId: String, quantity: Int) {
		require(itemId.isNotBlank()) { "Item ID must not be blank" }

		httpClient.patch("$baseUrl/v1/items/$itemId") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
			setBody(ItemPatchRequest(quantity = quantity))
		}
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

@Serializable
private data class ItemCreateRequest(
	val name: String,
	val description: String? = null,
	val locationId: String,
	val labelIds: List<String>? = null,
	val parentId: String? = null,
)

@Serializable
private data class ItemPatchRequest(
	val quantity: Int? = null,
)

@Serializable
data class ItemSummary(
	val id: String,
	val name: String,
	val description: String? = null,
	val quantity: Int? = null,
	val location: LocationSummary? = null,
)

@Serializable
data class ItemPage(
	val items: List<ItemSummary>,
	val page: Int,
	val pageSize: Int,
	val total: Int,
)

@Serializable
data class LocationDetails(
	val id: String,
	val name: String,
	val description: String? = null,
	val parent: LocationSummary? = null,
)
