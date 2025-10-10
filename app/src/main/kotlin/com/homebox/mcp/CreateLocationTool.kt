package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class CreateLocationTool(private val client: HomeboxClient) {
	val name: String = "create_location"
	val description: String =
		"Create a Homebox location. Provide a single name or a slash-separated path to create nested locations, reusing existing parents when present."

	val inputSchema: Tool.Input = Tool.Input(
		properties = buildJsonObject {
			put(
				"path",
				buildJsonObject {
					put("type", "string")
					put(
						"description",
						"The location name or a '/' separated path (e.g., 'Home/Basement/Shelf A'). Matching is case-insensitive and preserves spaces.",
					)
				},
			)
			put(
				"description",
				buildJsonObject {
					put("type", "string")
					put(
						"description",
						"Optional description applied to the final location created in the path.",
					)
				},
			)
		},
		required = listOf("path"),
	)

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val rawPath = arguments["path"]?.jsonPrimitive?.contentOrNull?.trim()
		if (rawPath.isNullOrBlank()) {
			return CallToolResult(content = listOf(TextContent("Path is required to create a location.")))
		}

		val description = arguments["description"]
			?.jsonPrimitive
			?.contentOrNull
			?.trim()
			?.takeIf { it.isNotEmpty() }

		val segments = rawPath
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		if (segments.isEmpty()) {
			return CallToolResult(
				content = listOf(TextContent("Path must include at least one non-empty location segment.")),
			)
		}

		val tree = client.getLocationTree()
		var currentParentId: String? = null
		var currentChildren = tree.filterLocations()
		var currentName: String? = null
		val createdLocations = mutableListOf<LocationSummary>()

		segments.forEachIndexed { index, segment ->
			val existing = currentChildren.firstOrNull { it.matchesName(segment) }
			if (existing != null) {
				currentParentId = existing.id
				currentName = existing.name
				currentChildren = existing.children.filterLocations()
			} else {
				val isLast = index == segments.lastIndex
				val created = client.createLocation(
					name = segment,
					parentId = currentParentId,
					description = if (isLast) description else null,
				)
				createdLocations += created
				currentParentId = created.id
				currentName = created.name
				currentChildren = emptyList()
			}
		}

		val finalName = currentName ?: segments.last()
		val fullPath = segments.joinToString(separator = " / ")
		val message = if (createdLocations.isEmpty()) {
			"Location \"$finalName\" already exists at path: $fullPath."
		} else {
			val createdNames = createdLocations.joinToString(separator = " / ") { it.name }
			buildString {
				append("Created ")
				append(if (createdLocations.size == 1) "location" else "locations")
				append(':')
				append(' ')
				append(createdNames)
				append(". Full path: ")
				append(fullPath)
			}
		}

		return CallToolResult(content = listOf(TextContent(message)))
	}

	private fun List<TreeItem>.filterLocations(): List<TreeItem> = filter { it.type.equals(LOCATION_TYPE, ignoreCase = true) }

	private fun TreeItem.matchesName(name: String): Boolean = type.equals(LOCATION_TYPE, ignoreCase = true) && this.name.equals(name, ignoreCase = true)

	private companion object {
		private const val LOCATION_TYPE = "location"
	}
}
