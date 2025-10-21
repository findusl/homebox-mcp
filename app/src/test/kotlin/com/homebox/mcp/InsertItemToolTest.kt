package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextContent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class InsertItemToolTest {
	private val client: HomeboxClient = mock()
	private val tool = InsertItemTool(client)

	@Test
	fun `inputSchema exposes expected name and parameter structure`() {
		val schema = tool.inputSchema
		assertEquals("object", schema.type)
		assertTrue(schema.required!!.contains("name"))
		assertTrue(schema.required!!.contains("location"))

		schema.assertHasParameter("name", "string")
		schema.assertHasParameter("location", "string")
		schema.assertHasParameter("description", "string")
		schema.assertHasParameter("quantity", "integer")
	}

	@Test
	fun `returns error when name missing`() =
		runTest {
			val result = tool.execute(
				buildJsonObject {
					put("location", JsonPrimitive("Home/Kitchen"))
				},
			)
			val text = (result.content.first() as TextContent).text.orEmpty()
			assertContains(text, "name")
			verify(client, never()).listItems(anyOrNull(), anyOrNull<List<Uuid>>(), any())
		}

	@Test
	fun `returns error when location missing`() =
		runTest {
			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
				},
			)
			val text = (result.content.first() as TextContent).text.orEmpty()
			assertContains(text, "location")
			verify(client, never()).listItems(anyOrNull(), anyOrNull<List<Uuid>>(), any())
		}

	@Test
	fun `returns error when quantity is not positive`() =
		runTest {
			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("Home/Kitchen"))
					put("quantity", JsonPrimitive(0))
				},
			)

			val text = (result.content.first() as TextContent).text.orEmpty()
			assertEquals("Quantity must be a positive integer.", text)
			verify(client, never()).listItems(anyOrNull(), anyOrNull<List<Uuid>>(), any())
			verify(client, never()).createItem(any(), any<Uuid>(), anyOrNull())
		}

	@Test
	fun `returns error when duplicate name exists`() =
		runTest {
			whenever(client.listItems(query = eq("Hammer"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50)))
				.thenReturn(
					ItemPage(
						items = listOf(
							ItemSummary(
								id = TestConstants.TEST_ID_1,
								name = "HAMMER",
								description = null,
								quantity = 1,
								location = null,
							),
						),
						page = 1,
						pageSize = 50,
						total = 1,
					),
				)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("Home/Garage"))
				},
			)

			val text = (result.content.first() as TextContent).text.orEmpty()
			assertContains(text, "already exists")
			verify(client).listItems(query = eq("Hammer"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50))
			verify(client, never()).getLocationTree()
			verify(client, never()).createItem(any(), any<Uuid>(), anyOrNull())
		}

	@Test
	fun `returns error when location cannot be resolved`() =
		runTest {
			whenever(client.listItems(query = eq("Hammer"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50)))
				.thenReturn(emptyItemPage())
			whenever(client.getLocationTree()).thenReturn(emptyList())

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("Unknown"))
				},
			)

			val text = (result.content.first() as TextContent).text.orEmpty()
			assertEquals("Location 'Unknown' was not found.", text)
			verify(client).listItems(query = eq("Hammer"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50))
			verify(client).getLocationTree()
			verify(client, never()).createItem(any(), any<Uuid>(), anyOrNull())
		}

	@Test
	fun `creates item with default quantity when not provided`() =
		runTest {
			whenever(client.listItems(query = eq("Hammer"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50)))
				.thenReturn(emptyItemPage())
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = TestConstants.TEST_ID_1,
						name = "Home",
						type = TreeItemType.LOCATION,
						children = listOf(
							TreeItem(
								id = TestConstants.TEST_ID_2,
								name = "Garage",
								type = TreeItemType.LOCATION,
							),
						),
					),
				),
			)

			whenever(
				client.createItem(
					name = eq("Hammer"),
					locationId = eq(TestConstants.TEST_ID_2),
					description = anyOrNull(),
				),
			).thenReturn(
				ItemSummary(
					id = TestConstants.TEST_ID_3,
					name = "Hammer",
					description = null,
					quantity = 1,
					location = null,
				),
			)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Hammer"))
					put("location", JsonPrimitive("home/garage"))
				},
			)

			val text = (result.content.first() as TextContent).text.orEmpty()
			assertContains(text, "Created item \"Hammer\"")
			assertContains(text, "Home / Garage")
			assertContains(text, "Quantity defaults to 1")

			verify(client).createItem(
				name = eq("Hammer"),
				locationId = eq(TestConstants.TEST_ID_2),
				description = anyOrNull(),
			)
			verify(client, never()).updateItemQuantity(any(), any())
		}

	@Test
	fun `updates quantity when provided`() =
		runTest {
			whenever(client.listItems(query = eq("Screwdriver"), locationIds = anyOrNull<List<Uuid>>(), pageSize = eq(50)))
				.thenReturn(emptyItemPage())
			whenever(client.getLocationTree()).thenReturn(
				listOf(TreeItem(id = TestConstants.TEST_ID_1, name = "Drawer", type = TreeItemType.LOCATION)),
			)
			whenever(
				client.createItem(
					name = eq("Screwdriver"),
					locationId = eq(TestConstants.TEST_ID_1),
					description = anyOrNull(),
				),
			).thenReturn(
				ItemSummary(
					id = TestConstants.TEST_ID_2,
					name = "Screwdriver",
					description = null,
					quantity = 1,
					location = null,
				),
			)

			val result = tool.execute(
				buildJsonObject {
					put("name", JsonPrimitive("Screwdriver"))
					put("location", JsonPrimitive("drawer"))
					put("quantity", JsonPrimitive(3))
					put("description", JsonPrimitive("Flathead"))
				},
			)

			val text = (result.content.first() as TextContent).text.orEmpty()
			assertContains(text, "Quantity set to 3")

			verify(client).updateItemQuantity(TestConstants.TEST_ID_2, 3)
		}

	private fun emptyItemPage(): ItemPage = ItemPage(items = emptyList(), page = 1, pageSize = 50, total = 0)
}
