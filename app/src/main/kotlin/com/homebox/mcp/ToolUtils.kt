package com.homebox.mcp

import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asFailure
import dev.forkhandles.result4k.asSuccess
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> JsonObject.parseArguments(): Result4k<T, CallToolResult> {
	try {
		return Json.decodeFromJsonElement<T>(this).asSuccess()
	} catch (exception: SerializationException) {
		if (exception is MissingFieldException) {
			return textResult("Missing the required fields: " + exception.missingFields.joinToString(",")).asFailure()
		}
		return textResult(exception.message ?: "Unable to parse insert_item arguments. Please ensure the input matches the schema.").asFailure()
	}
}

fun textResult(message: String): CallToolResult = CallToolResult(content = listOf(TextContent(message)))
