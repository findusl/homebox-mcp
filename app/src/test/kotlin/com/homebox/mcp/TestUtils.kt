package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertNotNull

fun Tool.Input.assertHasParameter(name: String, type: String) {
	val parameter = properties[name]
	assertNotNull(parameter, "Expected parameter $name to exist.")
	assertEquals(type, parameter.jsonObject["type"]?.jsonPrimitive?.content)
}
