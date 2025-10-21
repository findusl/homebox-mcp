package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
		val locationIds = locationQuery?.let { resolveLocationIds(it) }
		val page =
			if (locationQuery != null && locationIds?.isEmpty() == true) {
				ItemPage(items = emptyList(), page = 1, pageSize = PAGE_SIZE, total = 0)
			} else {
				client.listItems(locationIds = locationIds?.takeIf { it.isNotEmpty() }, pageSize = PAGE_SIZE)
			}

		val locationCache = mutableMapOf<String, List<String>>()
		val items = JsonArray(
			page.items.map { item ->
				val path = item.location?.id?.let { resolveLocationPath(it, locationCache) } ?: emptyList()
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

	private suspend fun resolveLocationPath(locationId: String, cache: MutableMap<String, List<String>>): List<String> {
		cache[locationId]?.let { return it }
		val details = client.getLocation(locationId)
		val parentPath = details.parent?.id?.let { resolveLocationPath(it, cache) } ?: emptyList()
		val path = parentPath + details.name
		cache[locationId] = path
		return path
	}

	private suspend fun resolveLocationIds(query: String): List<String> {
		val trimmed = query.trim()
		if (trimmed.isEmpty()) {
			return emptyList()
		}

		val tree = client.getLocationTree()
		val locationNodes = tree.filter { it.type == TreeItemType.LOCATION }
		return if (trimmed.contains('/')) {
			val segments = trimmed.split('/').map { it.trim() }.filter { it.isNotEmpty() }
			if (segments.isEmpty()) {
				emptyList()
			} else {
				findLocationsByPath(locationNodes, segments, 0)
			}
		} else {
			val rootMatches = locationNodes.filter { it.name == trimmed }
			if (rootMatches.isNotEmpty()) {
				rootMatches.map { it.id }
			} else {
				val results = mutableListOf<String>()
				collectLocationsByName(locationNodes, trimmed, results)
				results
			}
		}
	}

	private fun findLocationsByPath(
		nodes: List<TreeItem>,
		segments: List<String>,
		depth: Int,
	): List<String> {
		if (depth >= segments.size) {
			return emptyList()
		}

		val segment = segments[depth]
		val matches = nodes.filter { it.type == TreeItemType.LOCATION && it.name == segment }
		if (matches.isEmpty()) {
			return emptyList()
		}

		return if (depth == segments.lastIndex) {
			matches.map { it.id }
		} else {
			matches.flatMap { match ->
				val childLocations = match.children.filter { it.type == TreeItemType.LOCATION }
				findLocationsByPath(childLocations, segments, depth + 1)
			}
		}
	}

	private fun collectLocationsByName(
		nodes: List<TreeItem>,
		targetName: String,
		results: MutableList<String>,
	) {
		nodes.forEach { node ->
			if (node.type != TreeItemType.LOCATION) {
				return@forEach
			}
			if (node.name == targetName) {
				results += node.id
			}
			collectLocationsByName(node.children.filter { it.type == TreeItemType.LOCATION }, targetName, results)
		}
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
