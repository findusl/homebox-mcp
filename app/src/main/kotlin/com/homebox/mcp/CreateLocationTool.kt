package com.homebox.mcp

import com.xemantic.ai.tool.schema.meta.Description
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

class CreateLocationTool(private val client: HomeboxClient) {
	val name: String = "create_location"
	val description: String =
		"Create a Homebox location. Provide a single name or a slash-separated path to create nested locations, reusing existing parents when present."

	val inputSchema: Tool.Input = toolInputSchema<CreateLocationParameters>()

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val parameters = try {
			toolArgumentsJson.decodeFromJsonElement(CreateLocationParameters.serializer(), arguments)
		} catch (_: SerializationException) {
			val rawPath = arguments["path"]?.jsonPrimitive?.contentOrNull?.trim()
			if (rawPath.isNullOrEmpty()) {
				return CallToolResult(content = listOf(TextContent("Path is required to create a location.")))
			}
			return CallToolResult(content = listOf(TextContent("Unable to parse create_location arguments. Please ensure the input matches the schema.")))
		}

		val rawPath = parameters.path.trim()
		if (rawPath.isEmpty()) {
			return CallToolResult(content = listOf(TextContent("Path is required to create a location.")))
		}

		val description = parameters.description
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
		val knownLocationsByParent = mutableMapOf<String?, MutableMap<String, KnownLocation>>()

		segments.forEachIndexed { index, segment ->
			knownLocationsByParent.ensureParentChildren(currentParentId, currentChildren)
			val normalizedSegment = segment.lowercase()
			val knownSibling = knownLocationsByParent[currentParentId]?.get(normalizedSegment)
			if (knownSibling != null) {
				currentParentId = knownSibling.id
				currentName = knownSibling.name
				currentChildren = knownSibling.children.filterLocations()
				return@forEachIndexed
			}

			val isLast = index == segments.lastIndex
			val created = client.createLocation(
				name = segment,
				parentId = currentParentId,
				description = if (isLast) description else null,
			)
			createdLocations += created
			knownLocationsByParent.recordCreated(currentParentId, created)
			currentParentId = created.id
			currentName = created.name
			currentChildren = emptyList()
		}

		val finalName = currentName ?: segments.last()
		val fullPath = segments.joinToString(separator = " / ")
		val message = if (createdLocations.isEmpty()) {
			"""Location "$finalName" already exists at path: $fullPath."""
		} else {
			val createdNames = createdLocations.joinToString(separator = " ; ") { it.name }
			val locString = if (createdLocations.size == 1) "location" else "locations"
			"Created $locString: $createdNames. Full path: $fullPath"
		}

		return CallToolResult(content = listOf(TextContent(message)))
	}

	private fun List<TreeItem>.filterLocations(): List<TreeItem> = filter { it.type.equals(LOCATION_TYPE, ignoreCase = true) }

	private fun MutableMap<String?, MutableMap<String, KnownLocation>>.ensureParentChildren(parentId: String?, children: List<TreeItem>) {
		if (children.isEmpty()) {
			return
		}
		val parentMap = getOrPut(parentId) { mutableMapOf() }
		children
			.filter { it.type.equals(LOCATION_TYPE, ignoreCase = true) }
			.forEach { child ->
				val key = child.name.lowercase()
				parentMap.putIfAbsent(
					key,
					KnownLocation(
						id = child.id,
						name = child.name,
						children = child.children,
					),
				)
			}
	}

	private fun MutableMap<String?, MutableMap<String, KnownLocation>>.recordCreated(parentId: String?, created: LocationSummary) {
		val parentMap = getOrPut(parentId) { mutableMapOf() }
		parentMap.putIfAbsent(
			created.name.lowercase(),
			KnownLocation(id = created.id, name = created.name, children = emptyList()),
		)
	}

	@Serializable
	@Title("Create location arguments")
	private data class CreateLocationParameters(
		@Description("The location name or a '/' separated path (e.g., 'Home/Basement/Shelf A'). Matching is case-insensitive and preserves spaces.")
		val path: String,
		@Description("Optional description applied to the final location created in the path.")
		val description: String? = null,
	)

	private companion object {
		private const val LOCATION_TYPE = "location"
	}

	private data class KnownLocation(
		val id: String,
		val name: String,
		val children: List<TreeItem>,
	)
}
