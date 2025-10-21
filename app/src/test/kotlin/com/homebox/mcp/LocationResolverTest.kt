package com.homebox.mcp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocationResolverTest {
	private val tree = listOf(
		TreeItem(
			id = "root",
			name = "Home",
			type = "location",
			children = listOf(
				TreeItem(
					id = "garage",
					name = "Garage",
					type = "location",
					children = listOf(
						TreeItem(
							id = "shelf-a",
							name = "Shelf A",
							type = "location",
						),
					),
				),
				TreeItem(
					id = "attic",
					name = "Attic",
					type = "location",
				),
				TreeItem(
					id = "item-1",
					name = "Old Lamp",
					type = "item",
				),
			),
		),
	)

	private val resolver = LocationResolver(tree)

	@Test
	fun `resolve matches location by id`() {
		val result = resolver.resolve("attic")

		assertNotNull(result)
		assertEquals("attic", result!!.id)
		assertEquals(listOf("Home", "Attic"), result.path)
	}

	@Test
	fun `resolve matches location path ignoring case`() {
		val result = resolver.resolve("home/garage/shelf a")

		assertNotNull(result)
		assertEquals("shelf-a", result!!.id)
	}

	@Test
	fun `resolve trims whitespace around path segments`() {
		val result = resolver.resolve("  Home /  Garage / Shelf A  ")

		assertNotNull(result)
		assertEquals(listOf("Home", "Garage", "Shelf A"), result!!.path)
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
