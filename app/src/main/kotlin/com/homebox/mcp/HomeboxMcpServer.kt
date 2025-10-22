package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions

fun createHomeboxServer(client: HomeboxClient): Server {
	val locationsResource = LocationsResource(client)
	val createTool = CreateLocationTool(client)
	val insertItemTool = InsertItemTool(client)

	return Server(
		Implementation(
			name = "homebox-mcp",
			version = "0.1.0",
		),
		ServerOptions(
			capabilities = ServerCapabilities(
				tools = ServerCapabilities.Tools(listChanged = null),
				resources = ServerCapabilities.Resources(subscribe = null, listChanged = null),
			),
		),
	).apply {
		addResource(
			locationsResource.uri,
			locationsResource.name,
			locationsResource.description,
			locationsResource.mimeType,
		) { _ ->
			locationsResource.read()
		}
		addTool(createTool.name, createTool.description, createTool.inputSchema) { request ->
			createTool.execute(request.arguments)
		}
		addTool(insertItemTool.name, insertItemTool.description, insertItemTool.inputSchema) { request ->
			insertItemTool.execute(request.arguments)
		}
	}
}
