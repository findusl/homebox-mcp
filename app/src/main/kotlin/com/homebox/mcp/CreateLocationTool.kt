package com.homebox.mcp

import com.xemantic.ai.tool.schema.generator.jsonSchemaOf
import com.xemantic.ai.tool.schema.meta.Description
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class CreateLocationTool(private val client: HomeboxClient) {
	val name: String = "create_location"
	val description: String =
		"Create a Homebox location. Provide a single name or a slash-separated path to create nested locations, reusing existing parents when present."

	val inputSchema: Tool.Input = SCHEMA

	@OptIn(ExperimentalSerializationApi::class)
	suspend fun execute(arguments: JsonObject): CallToolResult {
		val input = try {
			json.decodeFromJsonElement(Parameters.serializer(), arguments)
		} catch (missing: MissingFieldException) {
			return errorResult("Path is required to create a location.")
		} catch (serialization: SerializationException) {
			return errorResult(
				"Invalid arguments for create_location tool: ${serialization.message ?: "serialization error"}",
			)
		}

		val rawPath = input.path.trim()
		if (rawPath.isEmpty()) {
			return errorResult("Path is required to create a location.")
		}

		val description = input.description?.trim()?.takeIf { it.isNotEmpty() }
		val segments = rawPath
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }
		if (segments.isEmpty()) {
			return errorResult("Path must include at least one non-empty location segment.")
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

	private fun errorResult(message: String): CallToolResult = CallToolResult(content = listOf(TextContent(message)))

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

	private companion object {
		private const val LOCATION_TYPE = "location"

		private val json = Json {
			ignoreUnknownKeys = true
		}

		private val SCHEMA: Tool.Input = jsonSchemaOf<Parameters>().toToolInput()
	}

	private data class KnownLocation(
		val id: String,
		val name: String,
		val children: List<TreeItem>,
	)

	@Serializable
	@Description("Parameters for the create_location tool")
	private data class Parameters(
		@Description("The location name or a '/' separated path (e.g., 'Home/Basement/Shelf A'). Matching is case-insensitive and preserves spaces.")
		val path: String,
		@Description("Optional description applied to the final location created in the path.")
		val description: String? = null,
	)
}
