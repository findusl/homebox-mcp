package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LocationsResource(
	private val client: HomeboxClient,
	private val json: Json = Json {},
) {
	val uri: String = "resource://homebox/locations"
	val name: String = "Homebox locations"
	val description: String = "Hierarchical Homebox locations with item counts."
	val mimeType: String = "application/json"

	suspend fun read(): ReadResourceResult {
		val tree = client.getLocationTree(withItems = true)
		val payload = json.encodeToString(
			JsonObject.serializer(),
			buildJsonObject {
				tree.filter { it.type == "location" }.forEach { location ->
					val (element, _) = buildLocationElement(location)
					put(location.name, element)
				}
			},
		)

		return ReadResourceResult(
			contents = listOf(
				TextResourceContents(
					text = payload,
					uri = uri,
					mimeType = mimeType,
				),
			),
		)
	}

	private fun buildLocationElement(location: TreeItem): Pair<JsonElement, Int> {
		require(location.type == "location") { "Expected location node" }

		var totalItems = 0
		val childLocations = mutableMapOf<String, JsonElement>()

		location.children.forEach { child ->
			when (child.type) {
				"location" -> {
					val (element, childCount) = buildLocationElement(child)
					childLocations[child.name] = element
					totalItems += childCount
				}
				"item" -> totalItems += 1
			}
		}

		val element =
			if (childLocations.isEmpty()) {
				JsonPrimitive(totalItems)
			} else {
				buildJsonObject {
					put("itemCount", totalItems)
					childLocations.forEach { (name, childElement) ->
						put(name, childElement)
					}
				}
			}

		return element to totalItems
	}
}
