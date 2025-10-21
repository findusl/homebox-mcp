@file:OptIn(ExperimentalUuidApi::class)
@file:UseSerializers(UuidAsStringSerializer::class)

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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

@OptIn(ExperimentalUuidApi::class)
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
		parentId: Uuid? = null,
		description: String? = null,
	): LocationSummary {
		require(name.isNotBlank()) { "Location name must not be blank" }
		val response = httpClient.post("$baseUrl/v1/locations") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
			setBody(
				LocationCreateRequest(
					name = name,
					description = description,
					parentId = parentId,
				),
			)
		}

		return json.decodeFromString<LocationSummary>(response.bodyAsText())
	}

	suspend fun listItems(
		query: String? = null,
		locationIds: List<Uuid>? = null,
		pageSize: Int = 100,
	): ItemPage {
		val response = httpClient.get("$baseUrl/v1/items") {
			parameter("pageSize", pageSize)
			query?.takeIf { it.isNotBlank() }?.let { parameter("q", it) }
			locationIds?.forEach { parameter("locations", it.toString()) }
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(ItemPage.serializer(), payload)
	}

	suspend fun createItem(
		name: String,
		locationId: Uuid,
		description: String? = null,
	): ItemSummary {
		require(name.isNotBlank()) { "Item name must not be blank" }

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

	suspend fun updateItemQuantity(id: Uuid, quantity: Int) {
		httpClient.patch("$baseUrl/v1/items/$id") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.ContentType, ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
			setBody(ItemPatchRequest(quantity = quantity))
		}
	}

	suspend fun getLocation(id: Uuid): LocationDetails {
		val response = httpClient.get("$baseUrl/v1/locations/$id") {
			accept(ContentType.Application.Json)
			header(HttpHeaders.Authorization, "Bearer $apiToken")
		}

		val payload = response.bodyAsText()
		return json.decodeFromString(LocationDetails.serializer(), payload)
	}

	private companion object {
		val LocationsSerializer = ListSerializer(Location.serializer())
		val LocationTreeSerializer = ListSerializer(TreeItem.serializer())
	}
}

@Serializable
data class Location(
	val id: Uuid,
	val name: String,
	val description: String? = null,
	@SerialName("itemCount") val itemCount: Int? = null,
)

@Serializable
data class LocationSummary(
	val id: Uuid,
	val name: String,
	val description: String? = null,
)

@Serializable
data class TreeItem(
	val id: Uuid,
	val name: String,
	val type: TreeItemType,
	val children: List<TreeItem> = emptyList(),
)

@Serializable
enum class TreeItemType {
	@SerialName("location")
	LOCATION,

	@SerialName("item")
	ITEM,
}

@Serializable
private data class LocationCreateRequest(
	val name: String,
	val description: String? = null,
	val parentId: Uuid? = null,
)

@Serializable
private data class ItemCreateRequest(
	val name: String,
	val description: String? = null,
	val locationId: Uuid,
)

@Serializable
private data class ItemPatchRequest(
	val quantity: Int,
)

@Serializable
data class ItemSummary(
	val id: Uuid,
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
	val id: Uuid,
	val name: String,
	val description: String? = null,
	val parent: LocationSummary? = null,
)

@OptIn(ExperimentalUuidApi::class)
object UuidAsStringSerializer : KSerializer<Uuid> {
	override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uuid", PrimitiveKind.STRING)

	override fun serialize(encoder: Encoder, value: Uuid) {
		encoder.encodeString(value.toString())
	}

	override fun deserialize(decoder: Decoder): Uuid = Uuid.parse(decoder.decodeString())
}
