package com.homebox.mcp

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val UUID_REGEX = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

@OptIn(ExperimentalUuidApi::class)
class LocationResolver(private val tree: List<TreeItem>) {
	fun resolve(normalized: String): ResolvedLocation? {
		if (normalized.length == 36 && UUID_REGEX.matches(normalized)) {
			return resolveById(Uuid.parse(normalized), tree)
		}

		val segments = normalized
			.split('/')
			.map { it.trim() }
			.filter { it.isNotEmpty() }

		if (segments.isEmpty()) {
			return null
		}

		val id = resolve(segments, tree) ?: return null

		return ResolvedLocation(id, segments)
	}

	fun resolveMultiplePossible(normalized: String): List<ResolvedLocation> {
		val single = resolve(normalized)
		if (single != null) {
			return listOf(single)
		} else if (normalized.length == 36 && UUID_REGEX.matches(normalized)) {
			return listOf()
		} else if (normalized.contains('/')) {
			return listOf()
		}

		return resolveByName(normalized, tree, listOf())
	}

	private fun resolve(segments: List<String>, currentNodes: List<TreeItem>): Uuid? {
		val node = currentNodes
			.filter { it.type == TreeItemType.LOCATION }
			.find { it.name.equals(segments[0], ignoreCase = true) } ?: return null

		if (segments.size == 1) {
			return node.id
		}

		return resolve(segments.subList(1, segments.size), node.children)
	}

	private fun resolveByName(
		query: String,
		nodes: List<TreeItem>,
		path: List<String>,
	): List<ResolvedLocation> =
		nodes
			.filter { it.type == TreeItemType.LOCATION }
			.flatMap { node ->
				val currentPath = path + node.name
				val foundChildren = resolveByName(query, node.children, currentPath)
				if (node.name.equals(query, ignoreCase = true)) {
					foundChildren + ResolvedLocation(node.id, currentPath)
				} else {
					foundChildren
				}
			}

	private fun resolveById(id: Uuid, nodes: List<TreeItem>): ResolvedLocation? {
		nodes
			.filter { it.type == TreeItemType.LOCATION }
			.forEach { node ->
				if (node.id == id) {
					return ResolvedLocation(node.id, listOf(node.name))
				}
				val foundChild = resolveById(id, node.children)
				if (foundChild != null) {
					return ResolvedLocation(foundChild.id, listOf(node.name) + foundChild.path)
				}
			}
		return null
	}

	data class ResolvedLocation(val id: Uuid, val path: List<String>)
}
