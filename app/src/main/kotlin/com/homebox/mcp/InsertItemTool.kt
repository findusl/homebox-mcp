package com.homebox.mcp

import com.xemantic.ai.tool.schema.meta.Description
import com.xemantic.ai.tool.schema.meta.MinInt
import com.xemantic.ai.tool.schema.meta.Title
import dev.forkhandles.result4k.onFailure
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

class InsertItemTool(private val client: HomeboxClient) {
	val name: String = "insert_item"
	val description: String =
		"Insert a new Homebox item given a unique name, quantity, location, and optional description."

	val inputSchema: Tool.Input = toolInputSchema<InsertItemParameters>()

	@OptIn(ExperimentalSerializationApi::class)
	suspend fun execute(arguments: JsonObject): CallToolResult {
		val parameters = arguments.parseArguments<InsertItemParameters>().onFailure { return it.reason }

		val name = parameters.name.trim()
		if (name.isBlank()) {
			return textResult("Item name is required to insert an item.")
		}

		val location = parameters.location.trim()
		if (location.isBlank()) {
			return textResult("Location is required to insert an item.")
		}

		val quantity = parameters.quantity ?: DEFAULT_QUANTITY
		if (quantity <= 0) {
			return textResult("Quantity must be a positive integer.")
		}

		val description = parameters.description?.trim()?.takeIf { it.isNotEmpty() }

		val existingItems = client.listItems(query = name, pageSize = DUPLICATE_CHECK_PAGE_SIZE)
		val duplicateExists = existingItems.items.any { it.name.equals(name, ignoreCase = true) }
		if (duplicateExists) {
			return textResult("An item named \"$name\" already exists. Choose a different name.")
		}

		val locationTree = client.getLocationTree()
		val resolvedLocation = LocationResolver(locationTree).resolve(location)
			?: return textResult("Location '$location' was not found.")

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

	private companion object {
		private const val DEFAULT_QUANTITY = 1
		private const val DUPLICATE_CHECK_PAGE_SIZE = 50
	}
}
