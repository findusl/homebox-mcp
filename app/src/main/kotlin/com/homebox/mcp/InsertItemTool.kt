package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class InsertItemTool(private val client: HomeboxClient) {
	val name: String = "insert_item"
	val description: String =
		"Insert a new Homebox item given a unique name, quantity, location, and optional description."

	val inputSchema: Tool.Input = Tool.Input(
		properties = buildJsonObject {
			put(
				"name",
				buildJsonObject {
					put("type", "string")
					put("description", "The unique name of the item to create.")
				},
			)
			put(
				"quantity",
				buildJsonObject {
					put("type", "integer")
					put("description", "Optional quantity for the item (defaults to 1).")
				},
			)
			put(
				"location",
				buildJsonObject {
					put("type", "string")
					put(
						"description",
						"Location ID or '/' separated absolute path (e.g., 'Home/Kitchen/Shelf A').",
					)
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
		if (rawName.isNullOrBlank()) {
			return errorResult("Item name is required to insert an item.")
		}

		val rawLocation = arguments["location"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawLocation.isNullOrBlank()) {
			return errorResult("Location is required to insert an item.")
		}

		val quantity = arguments["quantity"]?.jsonPrimitive?.intOrNull ?: DEFAULT_QUANTITY
		if (quantity <= 0) {
			return errorResult("Quantity must be a positive integer.")
		}

		val description = arguments["description"]
			?.jsonPrimitive
			?.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }

		val existingItems = client.listItems(query = rawName, pageSize = DUPLICATE_CHECK_PAGE_SIZE)
		val duplicateExists = existingItems.items.any { it.name.equals(rawName, ignoreCase = true) }
		if (duplicateExists) {
			return errorResult("An item named \"$rawName\" already exists. Choose a different name.")
		}

		val locationTree = client.getLocationTree()
		val resolvedLocation = resolveLocation(rawLocation, locationTree)
			?: return errorResult("Location '$rawLocation' was not found.")

		val createdItem = client.createItem(
			name = rawName,
			locationId = resolvedLocation.id,
			description = description,
		)

		if (quantity != DEFAULT_QUANTITY) {
			client.updateItemQuantity(createdItem.id, quantity)
		}

		val locationPath = resolvedLocation.path.joinToString(separator = " / ")
		val message = buildString {
			append("""Created item "${createdItem.name}" at location: $locationPath.""")
			if (quantity != DEFAULT_QUANTITY) {
				append(" Quantity set to $quantity.")
			} else {
				append(" Quantity defaults to $DEFAULT_QUANTITY.")
			}
		}

		return CallToolResult(content = listOf(TextContent(message)))
	}

	private fun resolveLocation(rawLocation: String, tree: List<TreeItem>): ResolvedLocation? {
		val flattened = mutableListOf<ResolvedLocation>()

		fun traverse(node: TreeItem, path: List<String>) {
			if (!node.type.equals(LOCATION_TYPE, ignoreCase = true)) {
				return
			}
			val currentPath = path + node.name
			flattened += ResolvedLocation(id = node.id, path = currentPath)
			node.children.forEach { traverse(it, currentPath) }
		}

		tree.forEach { traverse(it, emptyList()) }

		flattened.firstOrNull { it.id == rawLocation }?.let { return it }

		val segments = rawLocation.split('/').map { it.trim() }.filter { it.isNotEmpty() }
		if (segments.isEmpty()) {
			return null
		}

		return flattened.firstOrNull { resolved ->
			if (resolved.path.size != segments.size) {
				return@firstOrNull false
			}
			resolved.path.zip(segments).all { (actual, expected) -> actual.equals(expected, ignoreCase = true) }
		}
	}

	private fun errorResult(message: String): CallToolResult = CallToolResult(content = listOf(TextContent(message)))

	private data class ResolvedLocation(val id: String, val path: List<String>)

	private companion object {
		private const val LOCATION_TYPE = "location"
		private const val DEFAULT_QUANTITY = 1
		private const val DUPLICATE_CHECK_PAGE_SIZE = 50
	}
}
