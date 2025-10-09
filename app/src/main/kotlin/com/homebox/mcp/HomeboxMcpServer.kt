package com.homebox.mcp

import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions

fun createHomeboxServer(client: HomeboxClient): Server {
	val tool = ListLocationsTool(client)

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
		addTool(tool.name, tool.description, tool.inputSchema) { request ->
			tool.execute(request.arguments)
		}
	}
}
