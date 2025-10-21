package com.homebox.mcp

import com.xemantic.ai.tool.schema.meta.Description
import com.xemantic.ai.tool.schema.meta.Title
import dev.forkhandles.result4k.onFailure
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalUuidApi::class)
class CreateLocationTool(private val client: HomeboxClient) {
	val name: String = "create_location"
	val description: String =
		"Create a Homebox location. Provide a single name or a slash-separated path to create nested locations, reusing existing parents when present."

	val inputSchema: Tool.Input = toolInputSchema<CreateLocationParameters>()

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val parameters = arguments.parseArguments<CreateLocationParameters>().onFailure { return it.reason }

		val rawPath = parameters.path.trim()
		if (rawPath.isEmpty()) {
			return textResult("Path is required to create a location.")
		}

		val description = parameters.description?.trim()?.takeIf { it.isNotEmpty() }

		val segments = rawPath
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		if (segments.isEmpty()) {
			return textResult("Path must include at least one non-empty location segment.")
		}

		val topLevelLocations = client.getLocationTree()
		var parentPointer = KnownLocation(null, topLevelLocations)
		val createdLocations = mutableListOf<LocationSummary>()
		segments.forEachIndexed { index, segment ->
			val existingLocation = parentPointer.children.find { it.name.equals(segment, ignoreCase = true) }
			if (existingLocation != null) {
				parentPointer = KnownLocation(existingLocation.id, existingLocation.children)
				return@forEachIndexed
			}
			val isLast = index == segments.lastIndex
			val created = client.createLocation(
				name = segment,
				parentId = parentPointer.id,
				description = if (isLast) description else null,
			)
			parentPointer = KnownLocation(created.id, emptyList())
			createdLocations += created
		}

		val finalName = segments.last()
		val fullPath = segments.joinToString(separator = " / ")
		val message = if (createdLocations.isEmpty()) {
			"""Location "$finalName" already exists at path: $fullPath."""
		} else {
			val createdNames = createdLocations.joinToString(separator = " ; ") { it.name }
			val locString = if (createdLocations.size == 1) "location" else "locations"
			"Created $locString: $createdNames. Full path: $fullPath"
		}

		return textResult(message)
	}

	@Serializable
	@Title("Create location arguments")
	private data class CreateLocationParameters(
		@Description("The location name or a '/' separated path (e.g., 'Home/Basement/Shelf A'). Matching is case-insensitive and preserves spaces.")
		val path: String,
		@Description("Optional description applied to the final location created in the path.")
		val description: String? = null,
	)

	private data class KnownLocation(
		val id: Uuid?,
		val children: List<TreeItem>,
	)
}
