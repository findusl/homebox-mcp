
# Homebox MCP

**Homebox MCP** is the adapter layer between your AI agent and your self-hosted Homebox.
It exposes **MCP-native capabilities** — not just tool calls — so the agent can **read context (resources), use reusable workflows (prompts), and perform actions (tools)** to keep your inventory up to date.

MCP (Model Context Protocol) standardizes how AI apps connect to external systems via **Resources, Prompts, and Tools**; this repo implements those for Homebox. ([Model Context Protocol][1])

---

## What this repo is (high level)

* An **MCP server** that:

  * Publishes **Resources** (read-only context like locations, items, logs) for the agent to browse or attach into requests. ([Model Context Protocol][2])
  * Provides **Prompts** (templated workflows) to guide consistent, low-friction inventory interactions such as “intake an item” or “move an item.” ([Model Context Protocol][3])
  * Offers **Tools** (actions) for **search, insert, update, and relocate** items in Homebox. ([Model Context Protocol][4])
* A thin **Homebox client** (auth + API shaping) hidden behind the MCP surface.
* JSON schemas and behavior notes aimed at **LLM reliability**.

> Why MCP? You can plug this server into MCP-aware clients (e.g., Claude Desktop or your own agent runner) and immediately gain a consistent, discoverable capability surface. ([Anthropic][5])

---

## Capabilities: designed “the MCP way”

### 1) Resources (read-only context)

Expose data the agent can **browse and reference** before calling tools:

* **`homebox://locations`** — list of canonical locations (id, name, hierarchy/path).
* **`homebox://items?location={id}`** — items in a location (id, name, qty, notes, tags).
* **`homebox://item/{id}`** — detail view for a specific item.

These are **addressable URIs** with deterministic content (text or JSON). Agents can pull them into context or attach them to tool calls, improving grounding and reducing hallucination. ([Model Context Protocol][2])

---

### 2) Prompts (reusable workflows)

Ship opinionated, parameterized flows as **Prompts** the client can surface like commands:

* **`inventory-intake`** — guides the model through: read `homebox://locations`, confirm location, check existing items (via tool), then propose create/update.
* **`move-item`** — resolve a target location (using resources), validate item id, then call the mover tool.
* **`quantity-adjust`** — validate units/integers, check recent activity, then update.

Prompts act as **templated message sequences** with arguments and can embed resource content. This creates consistent behavior across models and sessions. ([Model Context Protocol][3])

---

### 3) Tools (actions)

The repo exposes a small, **LLM-friendly** tool surface for mutations. Tools are deliberately minimal because **context lives in Resources and workflow scaffolding lives in Prompts**:

* **`search_items`** — find items by name/location/filters (used before upserts/updates).
* **`upsert_item`** — create or merge into an existing item (implements read-before-write).
* **`update_item`** — change fields (quantity, notes, tags).
* **`move_item`** — relocate an item to a new location id.
* **`resolve_location`** — map a free-text location string to a known location id (uses resource index).

> Tools are the MCP mechanism for executing side-effecting operations; resources/prompts keep the tools simple and deterministic to call. ([Model Context Protocol][4])

---

## How these pieces work together

A typical agent flow for “I have three air mattresses on the corridor shelf”:

1. The agent **reads** `homebox://locations` (resource) to ground “corridor shelf.” ([Model Context Protocol][2])
2. It runs the **`inventory-intake` prompt**, which tells it to:

   * pull `homebox://items?location={corridor_id}` (resource),
   * attempt `search_items` (tool), then
   * decide between `upsert_item` or `update_item` (tool). ([Model Context Protocol][3])

---

## Configuration

* `HOMEBOX_BASE_URL` — your Homebox endpoint
* `HOMEBOX_API_TOKEN` — API token

---

## Roadmap

* **Resource templates** (parameterized URIs, e.g., `homebox://items?name={q}`) for faster client-side binding. ([Medium][8])
* **Attachments-first prompts** (auto-inject locations/items resources into prompt flows). ([Model Context Protocol][3])
* **Batch tools** (bulk move/upsert).
* **Fine-grained activity feeds** (per location/item resources).
* Optional normalization service (post-MVP).

---

## Further reading

* MCP overview and “USB-C for AI apps” analogy. ([Model Context Protocol][1])
* Spec: **Resources, Prompts, Tools** — core server features. ([Model Context Protocol][6])
* Launch post & motivation. ([Anthropic][5])
* Concept guides and tutorials on using resources/prompts effectively. ([modelcontextprotocol.info][9])

---

**Bottom line:** This MCP server doesn’t just “wrap a REST API.” It **exposes your Homebox as first-class MCP Resources, Prompts, and Tools**, so agents can *read real context*, *follow consistent workflows*, and *perform precise actions* — the way MCP is designed to be used. ([Model Context Protocol][6])

[1]: https://modelcontextprotocol.io/?utm_source=chatgpt.com "What is the Model Context Protocol (MCP)? - Model Context ..."
[2]: https://modelcontextprotocol.io/docs/concepts/resources?utm_source=chatgpt.com "Resources"
[3]: https://modelcontextprotocol.io/docs/concepts/prompts?utm_source=chatgpt.com "Prompts"
[4]: https://modelcontextprotocol.io/docs/concepts/tools?utm_source=chatgpt.com "Tools"
[5]: https://www.anthropic.com/news/model-context-protocol?utm_source=chatgpt.com "Introducing the Model Context Protocol"
[6]: https://modelcontextprotocol.io/specification/latest?utm_source=chatgpt.com "Specification"
[7]: https://www.descope.com/learn/post/mcp?utm_source=chatgpt.com "What Is the Model Context Protocol (MCP) and How It Works"
[8]: https://medium.com/%40cstroliadavis/building-mcp-servers-315917582ad1?utm_source=chatgpt.com "Building MCP Servers: Part 2 — Extending Resources with ..."
[9]: https://modelcontextprotocol.info/docs/concepts/resources/?utm_source=chatgpt.com "Resources - Model Context Protocol （MCP）"
