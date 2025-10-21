package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.TextResourceContents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ItemsResourceTest {
	private val client: HomeboxClient = mock()
	private val resource = ItemsResource(client)

	@Test
	fun `read without query returns items with cached location paths`() =
		runTest {
			whenever(client.listItems(locationIds = null, pageSize = 100)).thenReturn(
				ItemPage(
					items = listOf(
						ItemSummary(
							id = "item-1",
							name = "Hammer",
							description = "Steel hammer",
							quantity = 2,
							location = LocationSummary(id = "loc-1", name = "Shelf A"),
						),
						ItemSummary(
							id = "item-2",
							name = "Nails",
							description = null,
							quantity = 100,
							location = LocationSummary(id = "loc-1", name = "Shelf A"),
						),
					),
					page = 1,
					pageSize = 100,
					total = 150,
				),
			)
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "root",
						name = "Home",
						type = "location",
						children = listOf(
							TreeItem(
								id = "loc-0",
								name = "Basement",
								type = "location",
								children = listOf(
									TreeItem(
										id = "loc-1",
										name = "Shelf A",
										type = "location",
									),
								),
							),
						),
					),
				),
			)

			val result = resource.read(ReadResourceRequest("resource://homebox/items", buildJsonObject { }))

			verify(client).listItems(locationIds = null, pageSize = 100)
			verify(client).getLocationTree()

			val contents = result.contents.single() as TextResourceContents
			val payload = requireNotNull(contents.text)
			val root = Json.parseToJsonElement(payload).jsonObject
			assertTrue(root.getValue("moreAvailable").jsonPrimitive.boolean)
			val items = root.getValue("items").jsonArray
			assertEquals(2, items.size)
			val firstItem = items.first().jsonObject
			assertEquals("Hammer", firstItem.getValue("name").jsonPrimitive.content)
			assertEquals(listOf("Home", "Basement", "Shelf A"), firstItem.getValue("locationPath").jsonArray.map { it.jsonPrimitive.content })
		}

	@Test
	fun `read with location path query filters items`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "root",
						name = "Home",
						type = "location",
						children = listOf(TreeItem(id = "loc-2", name = "Basement", type = "location")),
					),
				),
			)
			whenever(client.listItems(locationIds = listOf("loc-2"), pageSize = 100)).thenReturn(
				ItemPage(
					items = listOf(
						ItemSummary(
							id = "item-3",
							name = "Drill",
							description = null,
							quantity = 1,
							location = LocationSummary(id = "loc-2", name = "Basement"),
						),
					),
					page = 1,
					pageSize = 100,
					total = 1,
				),
			)
			val request = ReadResourceRequest("resource://homebox/items?location=Home/Basement", buildJsonObject { })
			val result = resource.read(request)

			verify(client).getLocationTree()
			verify(client).listItems(locationIds = listOf("loc-2"), pageSize = 100)

			val contents = result.contents.single() as TextResourceContents
			val payload = requireNotNull(contents.text)
			val root = Json.parseToJsonElement(payload).jsonObject
			assertFalse(root.getValue("moreAvailable").jsonPrimitive.boolean)
			val items = root.getValue("items").jsonArray
			assertEquals(1, items.size)
			val path = items.first().jsonObject.getValue("locationPath").jsonArray.map { it.jsonPrimitive.content }
			assertEquals(listOf("Home", "Basement"), path)
		}

	@Test
	fun `read with unmatched location returns empty set`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(TreeItem(id = "root", name = "Home", type = "location")),
			)

			val request = ReadResourceRequest("resource://homebox/items?location=Unknown", buildJsonObject { })
			val result = resource.read(request)

			verify(client).getLocationTree()
			verify(client, never()).listItems(anyOrNull(), anyOrNull(), any())

			val contents = result.contents.single() as TextResourceContents
			val payload = requireNotNull(contents.text)
			val root = Json.parseToJsonElement(payload).jsonObject
			assertFalse(root.getValue("moreAvailable").jsonPrimitive.boolean)
			assertTrue(root.getValue("items").jsonArray.isEmpty())
		}
}
