package com.homebox.mcp

import com.xemantic.ai.tool.schema.generator.jsonSchemaOf
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val toolArgumentsJson: Json = Json { ignoreUnknownKeys = true }

private val schemaJsonParser: Json = Json { ignoreUnknownKeys = true }

internal inline fun <reified T> toolInputSchema(): Tool.Input {
	val schemaObject = schemaJsonParser.parseToJsonElement(jsonSchemaOf<T>().toString()).jsonObject
	val properties = schemaObject["properties"]?.jsonObject ?: JsonObject(emptyMap())
	val required = schemaObject["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
	return Tool.Input(
		properties = properties,
		required = required.takeIf { it.isNotEmpty() },
	)
}
