package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ListLocationsToolTest {
	private val client: HomeboxClient = mock()
	private val tool = ListLocationsTool(client)

	@Test
	fun `returns helpful message when no locations exist`() =
		runTest {
			whenever(client.listLocations(null)).thenReturn(emptyList())

			val result = tool.execute(buildJsonObject {})
			val text = (result.content.firstOrNull() as? TextContent)?.text

			assertEquals("No locations found.", text)
			verify(client).listLocations(null)
		}

	@Test
	fun `includes counts when requested`() =
		runTest {
			whenever(client.listLocations(null)).thenReturn(
				listOf(Location(id = "1", name = "Pantry", itemCount = 3)),
			)

			val result = tool.execute(
				buildJsonObject {
					put("includeCounts", JsonPrimitive(true))
				},
			)

			val text = (result.content.first() as TextContent).text ?: ""
			assertTrue(text.contains("Pantry (items: 3)"))
			verify(client).listLocations(null)
		}

	@Test
	fun `applies filter argument`() =
		runTest {
			whenever(client.listLocations(true)).thenReturn(emptyList())

			tool.execute(
				buildJsonObject {
					put("filterChildren", JsonPrimitive(true))
				},
			)

			verify(client).listLocations(true)
		}
}
