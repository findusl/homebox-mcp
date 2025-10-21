package com.homebox.mcp

import com.xemantic.ai.tool.schema.JsonSchema
import com.xemantic.ai.tool.schema.serialization.JsonSchemaSerializer
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val schemaJson = Json {
	prettyPrint = false
}

internal fun JsonSchema.toToolInput(): Tool.Input {
	val schema = schemaJson.encodeToJsonElement(JsonSchemaSerializer, this).jsonObject
	val properties = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
	val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }

	return Tool.Input(properties = properties, required = required)
}
