package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LocationsResourceTest {
	private val client: HomeboxClient = mock()
	private val resource = LocationsResource(client)

	@Test
	fun `read returns tree with primitive leaves`() =
		runTest {
			whenever(client.getLocationTree(withItems = true)).thenReturn(
				listOf(
					TreeItem(
						id = "loc-1",
						name = "Kitchen",
						type = "location",
						children = listOf(
							TreeItem(id = "item-1", name = "Plate", type = "item"),
							TreeItem(
								id = "loc-2",
								name = "Pantry",
								type = "location",
								children = listOf(
									TreeItem(id = "item-2", name = "Flour", type = "item"),
									TreeItem(id = "item-3", name = "Sugar", type = "item"),
								),
							),
						),
					),
				),
			)

			val result = resource.read()
			verify(client).getLocationTree(withItems = true)
			val contents = result.contents.single() as TextResourceContents
			val payload = contents.text ?: error("Expected text payload")
			val root = Json.parseToJsonElement(payload).jsonObject
			val kitchen = root.getValue("Kitchen").jsonObject
			assertEquals(3, kitchen.getValue("itemCount").jsonPrimitive.int)
			val pantry = kitchen.getValue("Pantry")
			assertTrue(pantry is JsonPrimitive)
			assertEquals(2, pantry.jsonPrimitive.int)
		}
}
