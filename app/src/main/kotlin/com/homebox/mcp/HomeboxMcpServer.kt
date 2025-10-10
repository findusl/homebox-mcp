package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions

fun createHomeboxServer(client: HomeboxClient): Server {
	val listTool = ListLocationsTool(client)
	val createTool = CreateLocationTool(client)

	return Server(
		Implementation(
			name = "homebox-mcp",
			version = "0.1.0",
		),
		ServerOptions(
			capabilities = ServerCapabilities(
				tools = ServerCapabilities.Tools(listChanged = null),
			),
		),
	).apply {
		addTool(listTool.name, listTool.description, listTool.inputSchema) { request ->
			listTool.execute(request.arguments)
		}
		addTool(createTool.name, createTool.description, createTool.inputSchema) { request ->
			createTool.execute(request.arguments)
		}
	}
}
