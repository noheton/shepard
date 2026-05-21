# shepard-plugin-mcp — Quick Start

Connect an AI agent (Claude, GPT-4, etc.) to your Shepard instance in 5 minutes.

## For Claude Users (claude.ai)

1. Open **Settings → Integrations → Add MCP server**
2. Enter the SSE URL: `https://shepard.nuclide.systems/mcp/sse`
3. Set your Shepard API token as the bearer token
4. Ask: _"What collections are in Shepard?"_

## For Claude Code (CLI)

Add to your `~/.claude.json` or project settings:

```json
{
  "mcpServers": {
    "shepard": {
      "transport": "sse",
      "url": "https://shepard.nuclide.systems/mcp/sse",
      "headers": {
        "Authorization": "Bearer <your-shepard-api-token>"
      }
    }
  }
}
```

## Typical Questions You Can Ask

- _"List all collections in Shepard"_
- _"Show me the data objects in the LUMEN campaign"_
- _"What happened after TR-004? Show me the successor chain"_
- _"What sensor channels are available in TR-004's timeseries container?"_
- _"Plot the vibration and thrust data for TR-004 side by side"_ (Claude fetches via compare_channels)
- _"What are the semantic annotations on TR-004?"_
- _"Summarise the structured data (run parameters) for TR-006"_

## Getting a Shepard API Token

1. Log in to Shepard
2. Go to **Profile → API Keys → Create new key**
3. Copy the key — you won't see it again
