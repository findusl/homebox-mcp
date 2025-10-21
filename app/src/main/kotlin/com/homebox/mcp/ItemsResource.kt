package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ItemsResource(
	private val client: HomeboxClient,
	private val json: Json = Json,
) {
	val uri: String = "resource://homebox/items"
	val name: String = "Homebox items"
	val description: String = "Homebox items with optional location filter."
	val mimeType: String = "application/json"

	suspend fun read(request: ReadResourceRequest): ReadResourceResult {
		val locationQuery = extractLocationQuery(request.uri)
		var resolver: LocationResolver? = null

		suspend fun ensureResolver(): LocationResolver {
			val existing = resolver
			if (existing != null) {
				return existing
			}

			val tree = client.getLocationTree()
			val created = LocationResolver(tree)
			resolver = created
			return created
		}

		suspend fun buildResult(page: ItemPage): ReadResourceResult {
			val items = JsonArray(
				page.items.map { item ->
					val path = item.location?.id?.let { locationId ->
						ensureResolver().resolve(locationId)?.path ?: emptyList()
					} ?: emptyList()
					buildJsonObject {
						put("id", JsonPrimitive(item.id))
						put("name", JsonPrimitive(item.name))
						put("quantity", item.quantity?.let { JsonPrimitive(it) } ?: JsonNull)
						put("description", item.description?.let { JsonPrimitive(it) } ?: JsonNull)
						put("locationPath", JsonArray(path.map { JsonPrimitive(it) }))
					}
				},
			)

			val payload = json.encodeToString(
				JsonObject.serializer(),
				buildJsonObject {
					put("items", items)
					val moreAvailable = page.total > page.page * page.pageSize
					put("moreAvailable", JsonPrimitive(moreAvailable))
				},
			)

			return ReadResourceResult(
				contents = listOf(
					TextResourceContents(
						text = payload,
						uri = uri,
						mimeType = mimeType,
					),
				),
			)
		}

		val locationIds =
			if (locationQuery != null) {
				val resolved = ensureResolver().resolve(locationQuery)
				if (resolved == null) {
					return buildResult(
						ItemPage(
							items = emptyList(),
							page = 1,
							pageSize = PAGE_SIZE,
							total = 0,
						),
					)
				}
				listOf(resolved.id)
			} else {
				null
			}

		val page = client.listItems(locationIds = locationIds, pageSize = PAGE_SIZE)

		return buildResult(page)
	}

	private fun extractLocationQuery(rawUri: String): String? {
		return try {
			val uri = URI(rawUri)
			val query = uri.rawQuery ?: return null
			query
				.split('&')
				.mapNotNull { parameter ->
					val parts = parameter.split('=', limit = 2)
					if (parts.isEmpty() || parts[0] != "location") {
						return@mapNotNull null
					}
					val value = if (parts.size == 2) parts[1] else ""
					URLDecoder.decode(value, StandardCharsets.UTF_8)
				}.firstOrNull()
				?.takeIf { it.isNotBlank() }
		} catch (_: URISyntaxException) {
			null
		}
	}

	private companion object {
		const val PAGE_SIZE = 100
	}
}
