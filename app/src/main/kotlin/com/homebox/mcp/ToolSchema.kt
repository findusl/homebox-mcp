package com.homebox.mcp

import com.xemantic.ai.tool.schema.generator.jsonSchemaOf
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val toolArgumentsJson: Json = Json { ignoreUnknownKeys = true }

private val schemaJsonParser: Json = Json { ignoreUnknownKeys = true }

internal inline fun <reified T> toolInputSchema(): Tool.Input {
	val schemaObject = schemaJsonParser.encodeToJsonElement(jsonSchemaOf<T>()).jsonObject
	val properties = schemaObject["properties"]!!.jsonObject
	val required = schemaObject["required"]?.jsonArray?.map { it.jsonPrimitive.content }
	return Tool.Input(
		properties = properties,
		required = required,
	)
}
