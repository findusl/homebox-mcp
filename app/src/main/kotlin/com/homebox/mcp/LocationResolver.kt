package com.homebox.mcp

class LocationResolver(tree: List<TreeItem>) {
	private val locations: List<ResolvedLocation> = buildList {
		tree.forEach { traverse(it, emptyList()) }
	}

	private fun MutableList<ResolvedLocation>.traverse(node: TreeItem, path: List<String>) {
		if (node.type != TreeItemType.LOCATION) {
			return
		}

		val currentPath = path + node.name
		add(ResolvedLocation(id = node.id, path = currentPath))
		node.children.forEach { traverse(it, currentPath) }
	}

	fun resolve(rawLocation: String): ResolvedLocation? {
		val normalized = rawLocation.trim()
		if (normalized.isEmpty()) {
			return null
		}

		locations.firstOrNull { it.id == normalized }?.let { return it }

		val segments = normalized
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }

		if (segments.isEmpty()) {
			return null
		}

		return locations.firstOrNull { candidate ->
			if (candidate.path.size != segments.size) {
				return@firstOrNull false
			}

			candidate.path.zip(segments).all { (actual, expected) ->
				actual.equals(expected, ignoreCase = true)
			}
		}
	}

	data class ResolvedLocation(val id: String, val path: List<String>)
}
