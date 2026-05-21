---
title: Connect an LLM endpoint
weight: 91
---

# Connect an LLM endpoint

This page walks you through connecting Shepard to an OpenAI-compatible
LLM endpoint in five minutes using the admin panel.

## Prerequisites

- You have the `instance-admin` role.
- `shepard-plugin-ai` is deployed (it ships with the default `docker-compose.yml`).
- You have an OpenAI API key or a locally-running endpoint (Ollama, LiteLLM proxy,
  SAIA/GWDG, etc.).

## Step 1 — Open the AI capability config

Navigate to **Admin → AI capabilities** in the left sidebar, or go directly to
`https://<your-instance>/admin/ai`.

You'll see a list of all capability slots (`TEXT`, `FAST_TEXT`, `IMAGE_GEN`, …).
Most will show as **Disabled**.

## Step 2 — Configure the TEXT slot

Click **TEXT** → **Edit**.

| Field | What to enter |
|---|---|
| Endpoint URL | `https://api.openai.com/v1` (OpenAI), or `http://localhost:11434/v1` (Ollama), or your SAIA/GWDG URL |
| Model | `gpt-4o-mini`, `gpt-4o`, `ollama/llama3.2`, or whatever your endpoint supports |
| API Key | Your API key (stored encrypted; never shown again in full) |
| Enabled | Toggle on |

Click **Save**.

## Step 3 — Verify with a wiki-write

If `shepard-plugin-wiki-writer` is also deployed, go to any DataObject and
click **Generate journal entry** (visible in the DataObject action menu).
The system calls the TEXT slot and writes a Markdown lab journal entry.

If the call succeeds, the AI is connected. If it returns 503, re-check the
endpoint URL and model name in the admin panel.

## Connecting Ollama (local)

```bash
ollama serve               # starts on http://localhost:11434
ollama pull llama3.2       # download a model
```

Set endpoint URL `http://localhost:11434/v1`, model `llama3.2`, leave API key blank.

## Connecting GWDG / SAIA (DLR)

Use the SAIA endpoint URL provided by GWDG (check the SAIA portal for your
team's URL). Set the API key to your SAIA token. Model name is the SAIA model
identifier (e.g. `meta-llama-3.1-70b-instruct`).

## Setting admin guardrails

To prepend institution-wide instructions to every prompt from every plugin,
set **Guardrails prefix** in the TEXT slot config. Example:

```
You are an assistant for DLR ZLP research data management.
Do not generate content unrelated to the research dataset described.
```

This prefix is injected in the system role before any plugin-specific prompt,
so it cannot be overridden by user input.
