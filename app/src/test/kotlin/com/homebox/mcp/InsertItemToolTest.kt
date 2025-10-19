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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class InsertItemToolTest {
	private val client: HomeboxClient = mock()
	private val tool = InsertItemTool(client)

	@Test
	fun `returns error when name is missing`() =
		runTest {
			val result = tool.execute(
				buildJsonObject {
					put("location", JsonPrimitive("Home/Kitchen"))
				},
			)

			val text = (result.content.first() as TextContent).text
			assertEquals("Name is required to insert an item.", text)
			verify(client, never()).getLocationTree()
		}

	@Test
	fun `returns error when quantity is invalid`() =
		runTest {
			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("quantity", JsonPrimitive("many"))
					put("location", JsonPrimitive("Home/Kitchen"))
				},
			)

			val text = (result.content.first() as TextContent).text
			assertEquals("Quantity must be an integer value.", text)
			verify(client, never()).getLocationTree()
		}

	@Test
	fun `returns error when location path is unknown`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(TreeItem(id = "root", name = "Home", type = "location")),
			)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("Home/Basement"))
				},
			)

			val text = (result.content.first() as TextContent).text
			assertTrue(text?.contains("No location found") == true)
			verify(client).getLocationTree()
			verify(client, never()).searchItems(any(), any(), any())
		}

	@Test
	fun `returns error when duplicate item is found`() =
		runTest {
			val tree = listOf(
				TreeItem(
					id = "root",
					name = "Home",
					type = "location",
					children = listOf(
						TreeItem(id = "kitchen", name = "Kitchen", type = "location"),
					),
				),
			)
			whenever(client.getLocationTree()).thenReturn(tree)
			whenever(client.searchItems(query = eq("Hammer"), page = eq(1), pageSize = eq(25))).thenReturn(
				ItemPage(
					items = listOf(
						ItemSummary(
							id = "item-1",
							name = "Hammer",
							description = "Steel hammer",
							quantity = 1,
							location = LocationSummary(id = "kitchen", name = "Kitchen"),
						),
					),
					page = 1,
					pageSize = 25,
					total = 1,
				),
			)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("Home/Kitchen"))
				},
			)

			val text = (result.content.first() as TextContent).text
			assertTrue(text?.contains("already exists") == true)
			verify(client).getLocationTree()
			verify(client).searchItems(query = eq("Hammer"), page = eq(1), pageSize = eq(25))
			verify(client, never()).createItem(any(), any(), any(), any())
		}

	@Test
	fun `creates item when location path matches`() =
		runTest {
			val tree = listOf(
				TreeItem(
					id = "root",
					name = "Home",
					type = "location",
					children = listOf(
						TreeItem(
							id = "kitchen",
							name = "Kitchen",
							type = "location",
							children = listOf(
								TreeItem(id = "cupboard", name = "Cupboard A", type = "location"),
							),
						),
					),
				),
			)
			whenever(client.getLocationTree()).thenReturn(tree)
			whenever(client.searchItems(query = eq("Hammer"), page = eq(1), pageSize = eq(25))).thenReturn(
				ItemPage(items = emptyList(), page = 1, pageSize = 25, total = 0),
			)
			whenever(
				client.createItem(
					name = eq("Hammer"),
					locationId = eq("cupboard"),
					description = eq("Steel hammer"),
					quantity = eq(2),
				),
			).thenReturn(
				ItemSummary(
					id = "item-2",
					name = "Hammer",
					description = "Steel hammer",
					quantity = 2,
					location = LocationSummary(id = "cupboard", name = "Cupboard A"),
				),
			)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("quantity", JsonPrimitive(2))
					put("location", JsonPrimitive("Home/Kitchen/Cupboard A"))
					put("description", JsonPrimitive("Steel hammer"))
				},
			)

			val text = (result.content.first() as TextContent).text ?: ""
			assertTrue(text.contains("Created item \"Hammer\""))
			assertTrue(text.contains("quantity 2"))
			assertTrue(text.contains("Home / Kitchen / Cupboard A"))
			assertTrue(text.contains("Description: Steel hammer"))
			verify(client).searchItems(query = eq("Hammer"), page = eq(1), pageSize = eq(25))
			verify(client).createItem(name = eq("Hammer"), locationId = eq("cupboard"), description = eq("Steel hammer"), quantity = eq(2))
		}
}
