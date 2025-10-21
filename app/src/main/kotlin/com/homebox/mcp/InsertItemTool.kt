package com.homebox.mcp

import com.xemantic.ai.tool.schema.meta.Description
import com.xemantic.ai.tool.schema.meta.MinInt
import com.xemantic.ai.tool.schema.meta.Title
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

class InsertItemTool(private val client: HomeboxClient) {
	val name: String = "insert_item"
	val description: String =
		"Insert a new Homebox item given a unique name, quantity, location, and optional description."

	val inputSchema: Tool.Input = toolInputSchema<InsertItemParameters>()

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val parameters = try {
			toolArgumentsJson.decodeFromJsonElement(InsertItemParameters.serializer(), arguments)
		} catch (_: SerializationException) {
			val rawName = arguments["name"]?.jsonPrimitive?.contentOrNull?.trim()
			if (rawName.isNullOrEmpty()) {
				return errorResult("Item name is required to insert an item.")
			}
			val rawLocation = arguments["location"]?.jsonPrimitive?.contentOrNull?.trim()
			if (rawLocation.isNullOrEmpty()) {
				return errorResult("Location is required to insert an item.")
			}
			return errorResult("Unable to parse insert_item arguments. Please ensure the input matches the schema.")
		}

		val name = parameters.name.trim()
		if (name.isEmpty()) {
			return errorResult("Item name is required to insert an item.")
		}

		val location = parameters.location.trim()
		if (location.isEmpty()) {
			return errorResult("Location is required to insert an item.")
		}

		val quantity = parameters.quantity ?: DEFAULT_QUANTITY
		if (quantity <= 0) {
			return errorResult("Quantity must be a positive integer.")
		}

		val description = parameters.description
			?.trim()
			?.takeIf { it.isNotEmpty() }

		val existingItems = client.listItems(query = name, pageSize = DUPLICATE_CHECK_PAGE_SIZE)
		val duplicateExists = existingItems.items.any { it.name.equals(name, ignoreCase = true) }
		if (duplicateExists) {
			return errorResult("An item named \"$name\" already exists. Choose a different name.")
		}

		val locationTree = client.getLocationTree()
		val resolvedLocation = resolveLocation(location, locationTree)
			?: return errorResult("Location '$location' was not found.")

		val createdItem = client.createItem(
			name = name,
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

	@Serializable
	@Title("Insert item arguments")
	private data class InsertItemParameters(
		@Description("The unique name of the item to create.")
		val name: String,
		@Description("Optional quantity for the item (defaults to 1).")
		@MinInt(1)
		val quantity: Int? = null,
		@Description("Location ID or '/' separated absolute path (e.g., 'Home/Kitchen/Shelf A').")
		val location: String,
		@Description("Optional description for the item.")
		val description: String? = null,
	)

	private data class ResolvedLocation(val id: String, val path: List<String>)

	private companion object {
		private const val LOCATION_TYPE = "location"
		private const val DEFAULT_QUANTITY = 1
		private const val DUPLICATE_CHECK_PAGE_SIZE = 50
	}
}
