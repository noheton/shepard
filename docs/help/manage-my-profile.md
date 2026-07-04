---
title: Manage your profile
stage: deployed
last-stage-change: 2026-06-22
---

# Manage your profile

Your profile lets you set your researcher identity (ORCID, display name),
upload an avatar, control which features appear in the UI, and manage
credentials for scripts and integrations.

Go to **Your name → Profile** in the top-right menu, or navigate to `/me`.

---

## Set your display name

1. Open your profile (top-right menu → **Profile** → **Profile** tab).
2. In the **Identity** section, type a name in the **Display name** field.
3. Click **Save identity**.

Your display name appears in audit trails ("Created by …"), provenance records,
and the page header. Leave it blank to fall back to your first + last name from
your account.

---

## Add your ORCID

An [ORCID](https://orcid.org/) iD uniquely identifies you across institutions.
Shepard uses it as the author identifier in RO-Crate exports and provenance
records — important for FAIR data compliance.

1. Open your profile → **Profile** tab → **Identity** section.
2. Paste your 16-digit ORCID (format `0000-0000-0000-000X`) into the **ORCID**
   field. Shepard validates the ISO 7064 checksum and shows an error if it is
   malformed.
3. Click **Save identity**.

Once saved, an ORCID badge can appear on your avatar (toggle in
**Display settings → Show ORCID badge on my avatar**). Your recent
publications and keywords from your public ORCID record appear below
the identity form.

Don't have an ORCID yet? Register for free at [orcid.org](https://orcid.org/).

---

## Upload an avatar

1. Open your profile → **Profile** tab.
2. Click **Upload avatar** above your current avatar (or initials placeholder).
3. Choose a JPEG, PNG, GIF, or WebP image — shepard stores it and serves it
   as your avatar across the UI.
4. To remove it, click **Remove**.

---

## Switch advanced mode on or off

**Advanced mode** shows container management, low-level data views, and
other power-user surfaces that are hidden by default.

Open your profile → **Profile** tab → **Display settings** → toggle
**Advanced mode**. The change takes effect immediately without a page reload.

---

## Other profile sections

| Tab | What it does |
|---|---|
| **API Keys** | Mint personal access tokens for scripts and automation. See [API access](api-access.md). |
| **MCP** | Connect Claude or another MCP client to your shepard data over SSE. See [API access](api-access.md). |
| **Subscriptions** | Choose which containers send you activity notifications. |
| **Git Credentials** | Store per-provider personal access tokens for private git repositories. See [Link a git repository](link-git-repo.md). |
| **Templates** | Browse all ShepardTemplates available on this instance. |

---

## Troubleshooting

| Symptom | What to check |
|---|---|
| "Not a valid ORCID" error | The checksum digit is wrong — copy the identifier from [orcid.org](https://orcid.org/) directly. |
| Display name not appearing in audit trails | Make sure you clicked **Save identity** after typing the name. |
| Avatar not updating after upload | Hard-refresh the page (Ctrl+Shift+R / Cmd+Shift+R); the image is cached for performance. |
| ORCID badge not showing | Enable the **Show ORCID badge on my avatar** toggle in Display settings, and make sure your ORCID is saved. |

---

## See also

- [API access — calling Shepard programmatically](api-access.md) — API keys + MCP setup
- [Link a git repository](link-git-repo.md) — git credentials for private repos
- [Get notified](get-notified.md) — how notifications work
- [User profile reference](../reference/user-profile.md) — full endpoint + field reference
