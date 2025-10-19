package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class InsertItemTool(private val client: HomeboxClient) {
	val name: String = "insert_item"
	val description: String =
		"Create a Homebox item with optional quantity and description. Location may be a location id or a '/' separated path."

	val inputSchema: Tool.Input = Tool.Input(
		properties = buildJsonObject {
			put(
				"name",
				buildJsonObject {
					put("type", "string")
					put("description", "Name of the item to insert. Must be unique within Homebox.")
				},
			)
			put(
				"quantity",
				buildJsonObject {
					put("type", "integer")
					put("description", "Optional quantity for the item. Defaults to 1 when omitted.")
					put("default", DEFAULT_QUANTITY)
					put("minimum", 1)
				},
			)
			put(
				"location",
				buildJsonObject {
					put("type", "string")
					put(
						"description",
						"Location id or '/' separated path where the item should be stored (e.g., 'Home/Kitchen/Shelf A').",
					)
				},
			)
			put(
				"description",
				buildJsonObject {
					put("type", "string")
					put("description", "Optional item description.")
				},
			)
		},
		required = listOf("name", "location"),
	)

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val rawName = arguments["name"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawName.isNullOrEmpty()) {
			return CallToolResult(content = listOf(TextContent("Name is required to insert an item.")))
		}

		val quantityResult = parseQuantity(arguments["quantity"])
		if (quantityResult is QuantityResult.Error) {
			return CallToolResult(content = listOf(TextContent(quantityResult.message)))
		}
		val quantity = when (quantityResult) {
			is QuantityResult.Success -> quantityResult.value
			is QuantityResult.Error -> DEFAULT_QUANTITY
		}

		val rawLocation = arguments["location"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawLocation.isNullOrEmpty()) {
			return CallToolResult(content = listOf(TextContent("Location is required to insert an item.")))
		}

		val description = arguments["description"]
			?.jsonPrimitive
			?.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }

		val tree = client.getLocationTree()
		val locationResolution = resolveLocation(rawLocation, tree)
		if (locationResolution is LocationResolution.Error) {
			return CallToolResult(content = listOf(TextContent(locationResolution.message)))
		}
		val resolvedLocation = locationResolution as LocationResolution.Success
		val locationId = resolvedLocation.locationId

		val existingItems = client.searchItems(query = rawName, page = 1, pageSize = DUPLICATE_CHECK_PAGE_SIZE)
		val duplicate = existingItems.items.firstOrNull { it.name.equals(rawName, ignoreCase = true) }
		if (duplicate != null) {
			return CallToolResult(
				content = listOf(
					TextContent("An item named \"$rawName\" already exists (id: ${duplicate.id}). Provide a unique name."),
				),
			)
		}

		val created = client.createItem(
			name = rawName,
			locationId = locationId,
			description = description,
			quantity = quantity,
		)

		val locationPath = resolvedLocation.path
		val locationDescription = if (locationPath.isNotEmpty()) {
			locationPath.joinToString(separator = " / ")
		} else {
			resolvedLocation.locationId
		}
		val finalDescription = created.description?.takeIf { it.isNotBlank() } ?: description
		val message = buildString {
			append("Created item \"")
			append(created.name)
			append("\"")
			append(" with quantity ")
			append(quantity)
			append(" in location: ")
			append(locationDescription)
			finalDescription?.let {
				append(". Description: ")
				append(it)
			}
		}

		return CallToolResult(content = listOf(TextContent(message)))
	}

	private fun parseQuantity(element: JsonElement?): QuantityResult {
		if (element == null) {
			return QuantityResult.Success(DEFAULT_QUANTITY)
		}

		val primitive = element as? JsonPrimitive ?: return QuantityResult.Error("Quantity must be an integer value.")
		val value = primitive.intOrNull ?: return QuantityResult.Error("Quantity must be an integer value.")
		if (value <= 0) {
			return QuantityResult.Error("Quantity must be greater than zero.")
		}
		return QuantityResult.Success(value)
	}

	private fun resolveLocation(raw: String, tree: List<TreeItem>): LocationResolution {
		val trimmed = raw.trim()
		if (trimmed.isEmpty()) {
			return LocationResolution.Error("Location is required to insert an item.")
		}

		findLocationPathById(tree, trimmed)?.let { path ->
			return LocationResolution.Success(locationId = trimmed, path = path)
		}

		val segments = trimmed.split('/').map { it.trim() }.filter { it.isNotEmpty() }
		if (segments.isEmpty()) {
			return LocationResolution.Error("Location path must include at least one segment.")
		}

		val matches = findLocationsByPath(tree, segments)
		if (matches.isEmpty()) {
			return LocationResolution.Error("No location found for path: ${segments.joinToString(" / ")}.")
		}
		if (matches.size > 1) {
			return LocationResolution.Error(
				"Multiple locations match path: ${segments.joinToString(" / ")}. Please provide a more specific path or use a location id.",
			)
		}

		return LocationResolution.Success(locationId = matches.single().node.id, path = matches.single().path)
	}

	private fun findLocationsByPath(nodes: List<TreeItem>, segments: List<String>): List<PathMatch> {
		if (segments.isEmpty()) {
			return emptyList()
		}

		var current = nodes.filterLocations().map { PathMatch(node = it, path = listOf(it.name)) }
		segments.forEachIndexed { index, segment ->
			current = current.filter { it.node.name.equals(segment, ignoreCase = true) }
			if (current.isEmpty()) {
				return emptyList()
			}
			if (index != segments.lastIndex) {
				current = current.flatMap { match ->
					match.node.children
						.filterLocations()
						.map { child -> PathMatch(node = child, path = match.path + child.name) }
				}
			}
		}

		return current
	}

	private fun findLocationPathById(
		nodes: List<TreeItem>,
		targetId: String,
		currentPath: List<String> = emptyList(),
	): List<String>? {
		for (node in nodes) {
			if (!node.type.equals(LOCATION_TYPE, ignoreCase = true)) {
				continue
			}
			val nextPath = currentPath + node.name
			if (node.id == targetId) {
				return nextPath
			}
			val childPath = findLocationPathById(node.children, targetId, nextPath)
			if (childPath != null) {
				return childPath
			}
		}
		return null
	}

	private fun List<TreeItem>.filterLocations(): List<TreeItem> = filter { it.type.equals(LOCATION_TYPE, ignoreCase = true) }

	private sealed interface QuantityResult {
		data class Success(val value: Int) : QuantityResult

		data class Error(val message: String) : QuantityResult
	}

	private sealed interface LocationResolution {
		data class Success(val locationId: String, val path: List<String>) : LocationResolution

		data class Error(val message: String) : LocationResolution
	}

	private data class PathMatch(val node: TreeItem, val path: List<String>)

	private companion object {
		private const val DEFAULT_QUANTITY = 1
		private const val LOCATION_TYPE = "location"
		private const val DUPLICATE_CHECK_PAGE_SIZE = 25
	}
}
