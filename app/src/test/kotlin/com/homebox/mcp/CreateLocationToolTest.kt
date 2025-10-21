package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.TextContent
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class CreateLocationToolTest {
	private val client: HomeboxClient = mock()
	private val tool = CreateLocationTool(client)

	@Test
	fun `inputSchema exposes expected name and parameter structure`() {
		val schema = tool.inputSchema
		assertEquals("object", schema.type)
		assertTrue(schema.required!!.contains("path"))

		schema.assertHasParameter("path", "string")
		schema.assertHasParameter("description", "string")
	}

	@Test
	fun `returns error when path is missing`() =
		runTest {
			val result = tool.execute(buildJsonObject {})
			val text = (result.content.first() as TextContent).text ?: ""

			assertEquals("Path is required to create a location.", text)
			verify(client, never()).getLocationTree()
		}

	@Test
	fun `reuses existing locations with case insensitive match`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(TreeItem(id = "1", name = "Home", type = "location")),
			)

			val result = tool.execute(
				buildJsonObject {
					put("path", JsonPrimitive("  home  "))
				},
			)

			val text = (result.content.first() as TextContent).text ?: ""
			assertTrue(text.contains("already exists"))
			assertTrue(text.contains("Location \"Home\""))
			verify(client).getLocationTree()
			verify(client, never()).createLocation(any(), anyOrNull(), anyOrNull())
		}

	@Test
	fun `creates missing path segments and reuses parents`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "root",
						name = "Home",
						type = "location",
					),
				),
			)

			val storageSummary = LocationSummary(id = "storage-id", name = "Storage")
			val shelfSummary = LocationSummary(id = "shelf-id", name = "Shelf A")

			whenever(client.createLocation(name = "storage", parentId = "root", description = null))
				.thenReturn(storageSummary)
			whenever(
				client.createLocation(
					name = "Shelf A",
					parentId = storageSummary.id,
					description = "Deep shelf",
				),
			).thenReturn(shelfSummary)

			val result = tool.execute(
				buildJsonObject {
					put("path", JsonPrimitive("Home / storage / Shelf A"))
					put("description", JsonPrimitive("Deep shelf"))
				},
			)

			val text = (result.content.first() as TextContent).text ?: ""
			assertTrue(text.contains("Created locations: Storage ; Shelf A."))
			assertTrue(text.contains("Full path: Home / storage / Shelf A"))

			inOrder(client).apply {
				verify(client).getLocationTree()
				verify(client).createLocation(name = "storage", parentId = "root", description = null)
				verify(client).createLocation(name = "Shelf A", parentId = storageSummary.id, description = "Deep shelf")
			}
		}

	@Test
	fun `avoids creating duplicate sibling locations`() =
		runTest {
			whenever(client.getLocationTree()).thenReturn(
				listOf(
					TreeItem(
						id = "root",
						name = "Home",
						type = "location",
						children = listOf(
							TreeItem(
								id = "storage-id",
								name = "Storage",
								type = "location",
							),
						),
					),
				),
			)

			val result = tool.execute(
				buildJsonObject {
					put("path", JsonPrimitive("Home/Storage"))
				},
			)

			val text = (result.content.first() as TextContent).text ?: ""

			assertTrue(text.contains("already exists"))
			verify(client).getLocationTree()
			verify(client, never()).createLocation(any(), anyOrNull(), anyOrNull())
		}
}
