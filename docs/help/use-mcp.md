---
title: Use AI assistants with Shepard (MCP tools)
description: Connect Claude, LobeChat, or any MCP-compatible AI assistant to Shepard and explore your research data in natural language
permalink: /help/use-mcp/
layout: default
audience: user
---
# Use AI assistants with Shepard (MCP tools)

Shepard exposes a **Model Context Protocol (MCP) server** that lets you connect
an AI assistant — such as Claude Desktop, Claude Code, or LobeChat — directly to
your collections and data. Once connected, the assistant can read data objects,
browse timeseries channels, add semantic annotations, create lab journal entries,
and validate data against templates, all through conversation.

You stay in control: the assistant only accesses data you can already see, using
your own credentials.

---

## Before you start

You need:

- A Shepard account with at least **Viewer** access to one collection
- An API key (see below)
- A MCP-compatible AI client (Claude Desktop, Claude Code, LobeChat, LangChain, …)

---

## Step 1 — Generate an API key

1. Open your profile by clicking your name in the top-right corner.
2. Choose **API keys → Generate new key**.
3. Give the key a name (e.g. "Claude Desktop") and click **Create**.
4. Copy the key immediately — it is only shown once.

The key is a signed token that carries the same read/write permissions as your
user account. Treat it like a password.

---

## Step 2 — Connect your AI client

### Claude Desktop

Add a `shepard` server to `~/.claude.json` (macOS/Linux) or
`%APPDATA%\Claude\claude.json` (Windows):

```json
{
  "mcpServers": {
    "shepard": {
      "type": "sse",
      "url": "https://<your-shepard-host>/v2/mcp/sse",
      "headers": {
        "Authorization": "Bearer <your-api-key>"
      }
    }
  }
}
```

Restart Claude Desktop. A green **shepard** badge appears in the tool bar when
the connection is live.

### Claude Code (CLI)

```bash
claude mcp add shepard \
  --transport sse \
  --url "https://<your-shepard-host>/v2/mcp/sse" \
  --header "Authorization: Bearer <your-api-key>"
```

Run `/mcp` inside a session to confirm the tools are loaded.

### LobeChat

In **Settings → Plugin Store → Add custom plugin**, enter:

- **Name:** Shepard
- **SSE URL:** `https://<your-shepard-host>/v2/mcp/sse`
- **Authorization header:** `Bearer <your-api-key>`

---

## What you can ask

Once connected, talk to the AI in plain language. Here are common starting points:

### Find data

> "Show me all collections I have access to."

> "List the DataObjects in the MFFD Process Chain collection."

> "Find data objects related to turbopump vibration."

### Explore timeseries

> "List the sensor channels for DataObject TR-004."

> "What is the peak vibration on the turbopump RMS channel between t=8s and t=12s?"

> "Plot the channel statistics for the hotfire run — min, max, mean."

### Annotate data

> "Add a semantic annotation to TR-004 marking the propellant as LOX/LH2."

> "Tag this DataObject with the MFFD consolidation-force predicate, value 18.3 N."

The assistant records its annotations with `sourceMode="ai"` so the provenance
trail clearly shows which values were set by the AI and which by a human.

### Write a lab journal entry

> "Write a journal note on TR-004 summarising the turbopump anomaly findings."

> "Update the journal entry we discussed with the repair outcome."

### Validate data

> "Check whether the AFP layup DataObject conforms to the MFFD-AFP template."

---

## Permissions

The assistant sees exactly what you can see. It cannot:

- Access collections you do not have **Read** permission on
- Write to collections you do not have **Write** permission on
- Read other users' API keys or credentials

All annotations and journal entries created through the AI appear in the audit
trail with your user name and `sourceMode="ai"`, satisfying the EU AI Act Article 50
machine-readable disclosure requirement.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Green badge does not appear in Claude Desktop | Wrong URL or key | Double-check the host and that the key was copied without trailing whitespace |
| `401 Unauthorized` in tool responses | Expired or revoked key | Regenerate the key under **Profile → API keys** |
| `403 Forbidden` on a specific tool call | You lack Write access to that collection | Ask the collection owner to grant you Write permission |
| The assistant says it cannot find a DataObject | Wrong `appId` or you do not have Read access | Copy the `appId` from the URL bar on the DataObject detail page |
| Tool list is empty | MCP connection not established | Restart the AI client and check the connection badge |

---

## See also

- [MCP tools reference](/reference/mcp-tools/) — full tool catalogue with parameters and examples
- [Annotating data](/help/annotating-data/) — manual annotation via the UI
- [Query with SPARQL](/help/query-with-sparql/) — programmatic annotation queries
- [API access](/help/api-access/) — using the REST API directly
