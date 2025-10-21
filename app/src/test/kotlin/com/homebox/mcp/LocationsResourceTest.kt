package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlin.uuid.ExperimentalUuidApi
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

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class LocationsResourceTest {
	private val client: HomeboxClient = mock()
	private val resource = LocationsResource(client)

	@Test
	fun `read returns tree with primitive leaves`() =
		runTest {
			whenever(client.getLocationTree(withItems = true)).thenReturn(
				listOf(
					TreeItem(
						id = TestConstants.TEST_ID_1,
						name = "Kitchen",
						type = TreeItemType.LOCATION,
						children = listOf(
							TreeItem(id = TestConstants.TEST_ID_2, name = "Plate", type = TreeItemType.ITEM),
							TreeItem(
								id = TestConstants.TEST_ID_3,
								name = "Pantry",
								type = TreeItemType.LOCATION,
								children = listOf(
									TreeItem(id = TestConstants.TEST_ID_4, name = "Flour", type = TreeItemType.ITEM),
									TreeItem(id = TestConstants.TEST_ID_5, name = "Sugar", type = TreeItemType.ITEM),
								),
							),
						),
					),
				),
			)

			val result = resource.read()
			verify(client).getLocationTree(withItems = true)
			val contents = result.contents.single() as? TextResourceContents
			val payload = contents?.text ?: error("Expected text payload")
			val root = Json.parseToJsonElement(payload).jsonObject
			val kitchen = root.getValue("Kitchen").jsonObject
			assertEquals(3, kitchen.getValue("itemCount").jsonPrimitive.int)
			val pantry = kitchen.getValue("Pantry")
			assertTrue(pantry is JsonPrimitive)
			assertEquals(2, pantry.jsonPrimitive.int)
		}
}
