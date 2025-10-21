package com.homebox.mcp

import kotlin.uuid.ExperimentalUuidApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalUuidApi::class)
class LocationResolverTest {
	private val tree = listOf(
		TreeItem(
			id = TestConstants.TEST_ID_1.toString(),
			name = "Home",
			type = TreeItemType.LOCATION,
			children = listOf(
				TreeItem(
					id = TestConstants.TEST_ID_2.toString(),
					name = "Garage",
					type = TreeItemType.LOCATION,
					children = listOf(
						TreeItem(
							id = TestConstants.TEST_ID_3.toString(),
							name = "Shelf A",
							type = TreeItemType.LOCATION,
						),
					),
				),
				TreeItem(
					id = TestConstants.TEST_ID_4.toString(),
					name = "Attic",
					type = TreeItemType.LOCATION,
				),
				TreeItem(
					id = TestConstants.TEST_ID_5.toString(),
					name = "Old Lamp",
					type = TreeItemType.ITEM,
				),
			),
		),
	)

	private val resolver = LocationResolver(tree)

	@Test
	fun `resolve matches location by id`() {
		val result = resolver.resolve(TestConstants.TEST_ID_4.toString())

		assertNotNull(result)
		assertEquals(TestConstants.TEST_ID_4.toString(), result!!.id)
		assertEquals(listOf("Home", "Attic"), result.path)
	}

	@Test
	fun `resolve matches location path ignoring case`() {
		val result = resolver.resolve("home/garage/shelf a")

		assertNotNull(result)
		assertEquals(TestConstants.TEST_ID_3.toString(), result!!.id)
	}

	@Test
	fun `resolve returns null when intermediate path segment missing`() {
		val result = resolver.resolve("Home/Shelf A")

		assertNull(result)
	}

	@Test
	fun `resolve ignores non-location nodes`() {
		val result = resolver.resolve("Home/Old Lamp")

		assertNull(result)
	}
}
