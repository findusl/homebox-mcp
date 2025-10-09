package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class ListLocationsTool(private val client: HomeboxClient) {
	val name: String = "list_locations"
	val description: String =
		"List Homebox locations and optionally include the current item counts."

	val inputSchema: Tool.Input = Tool.Input(
		properties = buildJsonObject {
			put(
				"filterChildren",
				buildJsonObject {
					put("type", "boolean")
					put(
						"description",
						"When true, only return locations that have a parent location.",
					)
				},
			)
			put(
				"includeCounts",
				buildJsonObject {
					put("type", "boolean")
					put(
						"description",
						"When true, include the stored item counts next to each location name.",
					)
				},
			)
		},
		required = emptyList(),
	)

	suspend fun execute(arguments: JsonObject): CallToolResult {
		val filterChildren = arguments["filterChildren"]?.jsonPrimitive?.booleanOrNull
		val includeCounts = arguments["includeCounts"]?.jsonPrimitive?.booleanOrNull ?: false

		val locations = client.listLocations(filterChildren)
		if (locations.isEmpty()) {
			return CallToolResult(content = listOf(TextContent("No locations found.")))
		}

		val lines = locations.map { location ->
			buildString {
				append(location.name)
				if (includeCounts) {
					val countText = location.itemCount?.toString() ?: "unknown"
					append(" (items: $countText)")
				}
			}
		}

		return CallToolResult(content = listOf(TextContent(lines.joinToString(separator = "\n"))))
	}
}
