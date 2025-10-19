package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class InsertItemTool(private val client: HomeboxClient) {
	val name: String = "insert_item"
	val description: String =
		"Insert an item into Homebox, creating it at the specified location path or location ID."

	val inputSchema: Tool.Input = Tool.Input(
		properties = buildJsonObject {
			put(
				"name",
				buildJsonObject {
					put("type", "string")
					put("description", "Name of the item. Must be unique across all items.")
				},
			)
			put(
				"location",
				buildJsonObject {
					put("type", "string")
					put(
						"description",
						"Target location expressed either as a location ID or as a '/' separated path (e.g., 'Home/Kitchen/Shelf A').",
					)
				},
			)
			put(
				"quantity",
				buildJsonObject {
					put("type", "integer")
					put("description", "Optional quantity. Defaults to 1 when omitted.")
				},
			)
			put(
				"description",
				buildJsonObject {
					put("type", "string")
					put("description", "Optional description for the item.")
				},
			)
		},
		required = listOf("name", "location"),
	)

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val rawName = arguments["name"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawName.isNullOrEmpty()) {
			return CallToolResult(
				content = listOf(TextContent("Item name is required and must not be blank.")),
			)
		}

		val rawLocation = arguments["location"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawLocation.isNullOrEmpty()) {
			return CallToolResult(
				content = listOf(TextContent("Location is required and must not be blank.")),
			)
		}

		val quantityResult = parseQuantity(arguments["quantity"])
		if (quantityResult is QuantityResult.Error) {
			return CallToolResult(content = listOf(TextContent(quantityResult.message)))
		}
		val quantity = (quantityResult as QuantityResult.Value).quantity

		val description = arguments["description"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

		when (val locationResult = resolveLocation(rawLocation)) {
			is LocationResult.Error -> {
				return CallToolResult(content = listOf(TextContent(locationResult.message)))
			}
			is LocationResult.Value -> {
				if (!isNameUnique(rawName)) {
					return CallToolResult(
						content = listOf(TextContent("An item named \"$rawName\" already exists.")),
					)
				}

				val created = client.createItem(
					name = rawName,
					locationId = locationResult.location.id,
					description = description,
				)

				if (quantity != DEFAULT_QUANTITY) {
					client.updateItemQuantity(created.id, quantity)
				}

				val pathDisplay = locationResult.location.path.joinToString(separator = " / ")
				val quantityDisplay =
					quantity.takeIf { it != DEFAULT_QUANTITY }
						?: (created.quantity ?: DEFAULT_QUANTITY)
				val message = buildString {
					append("Created item \"")
					append(created.name)
					append("\" with quantity ")
					append(quantityDisplay)
					append(" at location ")
					append(pathDisplay)
					append('.')
				}

				return CallToolResult(content = listOf(TextContent(message)))
			}
		}

		error("Unreachable")
	}

	private fun parseQuantity(element: JsonElement?): QuantityResult {
		if (element == null) {
			return QuantityResult.Value(DEFAULT_QUANTITY)
		}

		val primitive = element.jsonPrimitive
		val value = primitive.intOrNull
			?: return QuantityResult.Error("Quantity must be a positive integer when provided.")
		if (value <= 0) {
			return QuantityResult.Error("Quantity must be a positive integer when provided.")
		}
		return QuantityResult.Value(value)
	}

	private suspend fun resolveLocation(reference: String): LocationResult {
		val cleaned = reference.trim().trim('/')
		if (cleaned.isEmpty()) {
			return LocationResult.Error("Location is required and must not be blank.")
		}

		val tree = client.getLocationTree()
		val segments = cleaned.split('/').map { it.trim() }.filter { it.isNotEmpty() }

		findLocationById(tree, cleaned)?.let { return LocationResult.Value(it) }

		if (segments.isEmpty()) {
			return LocationResult.Error("Unable to resolve location: $reference")
		}

		val matches = findLocationsByPath(tree.filterLocations(), segments, 0, emptyList())
		if (matches.isEmpty()) {
			return LocationResult.Error("Unable to resolve location: $reference")
		}
		if (matches.size > 1) {
			val options = matches.joinToString(separator = "; ") {
				it.path.joinToString(separator = " / ")
			}
			return LocationResult.Error("Location path is ambiguous. Possible matches: $options")
		}
		return LocationResult.Value(matches.first())
	}

	private fun List<TreeItem>.filterLocations(): List<TreeItem> = filter { it.type.equals(LOCATION_TYPE, ignoreCase = true) }

	private fun findLocationById(
		nodes: List<TreeItem>,
		targetId: String,
		path: List<String> = emptyList(),
	): ResolvedLocation? {
		nodes.forEach { node ->
			if (!node.type.equals(LOCATION_TYPE, ignoreCase = true)) {
				return@forEach
			}
			val currentPath = path + node.name
			if (node.id == targetId) {
				return ResolvedLocation(node.id, currentPath)
			}
			findLocationById(node.children, targetId, currentPath)?.let { return it }
		}
		return null
	}

	private fun findLocationsByPath(
		nodes: List<TreeItem>,
		segments: List<String>,
		depth: Int,
		path: List<String>,
	): List<ResolvedLocation> {
		if (depth >= segments.size) {
			return emptyList()
		}

		val segment = segments[depth]
		val matches = mutableListOf<ResolvedLocation>()

		nodes.forEach { node ->
			if (!node.type.equals(LOCATION_TYPE, ignoreCase = true)) {
				return@forEach
			}
			if (!node.name.equals(segment, ignoreCase = true)) {
				return@forEach
			}

			val currentPath = path + node.name
			if (depth == segments.lastIndex) {
				matches += ResolvedLocation(node.id, currentPath)
			} else {
				matches += findLocationsByPath(
					node.children.filterLocations(),
					segments,
					depth + 1,
					currentPath,
				)
			}
		}

		return matches
	}

	private suspend fun isNameUnique(name: String): Boolean {
		var page = 1
		val normalized = name.lowercase()

		while (true) {
			val result = client.listItems(page = page, pageSize = ITEM_PAGE_SIZE)
			if (result.items.any { it.name.lowercase() == normalized }) {
				return false
			}

			if (result.page * result.pageSize >= result.total) {
				return true
			}
			page += 1
		}
	}

	private data class ResolvedLocation(val id: String, val path: List<String>)

	private sealed interface QuantityResult {
		data class Value(val quantity: Int) : QuantityResult

		data class Error(val message: String) : QuantityResult
	}

	private sealed interface LocationResult {
		data class Value(val location: ResolvedLocation) : LocationResult

		data class Error(val message: String) : LocationResult
	}

	private companion object {
		private const val DEFAULT_QUANTITY = 1
		private const val ITEM_PAGE_SIZE = 100
		private const val LOCATION_TYPE = "location"
	}
}
