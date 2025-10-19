package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class InsertItemToolTest {
	private val client: HomeboxClient = mock()

	@BeforeEach
	fun resetMocks() {
		reset(client)
	}

	@Test
	fun `creates item with default quantity when omitted`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "home",
						name = "Home",
						type = "location",
						children = listOf(
							TreeItem(
								id = "kitchen",
								name = "Kitchen",
								type = "location",
							),
						),
					),
				),
			)
			whenever(client.listItems(page = any(), locationIds = anyOrNull(), pageSize = any())).thenReturn(
				ItemPage(items = emptyList(), page = 1, pageSize = 100, total = 0),
			)
			whenever(
				client.createItem(
					name = eq("Spoon"),
					locationId = eq("kitchen"),
					description = eq("Stainless"),
				),
			).thenReturn(
				ItemSummary(
					id = "item-1",
					name = "Spoon",
					description = "Stainless",
					quantity = 1,
					location = LocationSummary(id = "kitchen", name = "Kitchen", description = null),
				),
			)

			val tool = InsertItemTool(client)
			val result = tool.execute(
				buildJsonObject {
					put("name", "Spoon")
					put("location", "Home/Kitchen")
					put("description", "Stainless")
				},
			)

			val message = (result.content.first() as TextContent).text ?: error("Expected text content")
			assertEquals("Created item \"Spoon\" with quantity 1 at location Home / Kitchen.", message)
			verify(client, never()).updateItemQuantity(any(), any())
		}

	@Test
	fun `creates item with explicit quantity`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "garage",
						name = "Garage",
						type = "location",
					),
				),
			)
			whenever(client.listItems(page = any(), locationIds = anyOrNull(), pageSize = any())).thenReturn(
				ItemPage(items = emptyList(), page = 1, pageSize = 100, total = 0),
			)
			whenever(
				client.createItem(
					name = eq("Wrench"),
					locationId = eq("garage"),
					description = eq(null),
				),
			).thenReturn(
				ItemSummary(
					id = "item-2",
					name = "Wrench",
					description = null,
					quantity = 1,
					location = LocationSummary(id = "garage", name = "Garage", description = null),
				),
			)

			val tool = InsertItemTool(client)
			val result = tool.execute(
				buildJsonObject {
					put("name", "Wrench")
					put("location", "garage")
					put("quantity", 4)
				},
			)

			verify(client).updateItemQuantity("item-2", 4)
			val message = (result.content.first() as TextContent).text ?: error("Expected text content")
			assertEquals("Created item \"Wrench\" with quantity 4 at location Garage.", message)
		}

	@Test
	fun `returns error when duplicate name exists`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "home",
						name = "Home",
						type = "location",
					),
				),
			)
			whenever(client.listItems(page = eq(1), locationIds = anyOrNull(), pageSize = any())).thenReturn(
				ItemPage(
					items = listOf(
						ItemSummary(
							id = "existing",
							name = "Lamp",
							description = null,
							quantity = 1,
							location = null,
						),
					),
					page = 1,
					pageSize = 100,
					total = 1,
				),
			)

			val tool = InsertItemTool(client)
			val result = tool.execute(
				buildJsonObject {
					put("name", "Lamp")
					put("location", "home")
				},
			)

			val message = (result.content.first() as TextContent).text ?: error("Expected text content")
			assertEquals("An item named \"Lamp\" already exists.", message)
			verify(client, never()).createItem(any(), any(), anyOrNull())
		}

	@Test
	fun `returns error when location path is ambiguous`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "home",
						name = "Home",
						type = "location",
						children = listOf(
							TreeItem(
								id = "closet-upper",
								name = "Closet",
								type = "location",
							),
							TreeItem(
								id = "closet-lower",
								name = "Closet",
								type = "location",
							),
						),
					),
				),
			)

			val tool = InsertItemTool(client)
			val result = tool.execute(
				buildJsonObject {
					put("name", "Shoes")
					put("location", "Home/Closet")
				},
			)

			val message = (result.content.first() as TextContent).text ?: error("Expected text content")
			assertTrue(message.startsWith("Location path is ambiguous."))
		}
}
