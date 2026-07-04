---
title: Link a Git repository to your data
description: How to attach a Git repository reference to a DataObject — loose link, tracked preview, or a permanently pinned version
permalink: /help/link-git-repo/
layout: default
audience: user
---
# Link a Git repository to your data

**What this is for.** Connecting the analysis scripts, configuration files, or
source code stored in a Git repository directly to the DataObject that contains
the measurement data those scripts produced. Shepard records the link in the
provenance trail, so anyone who reads your data later can see exactly which code
version was used.

**Before you start.** You need *write* access on the Collection and the target
DataObject. For private repositories you also need to store a Personal Access
Token (PAT) in your profile — see the "Private repositories" section below.

---

## Choose a link mode

| Mode | Best for | Upstream check performed? |
|------|----------|--------------------------|
| **Loose link** | A quick URL bookmark — the repo is public or the link is just for reference | No |
| **Tracked** | You want to preview a file from the repo inside Shepard and be told when the branch moves on | Yes (PT5M cache) |
| **Pinned snapshot** | You need to prove *exactly* which commit produced this dataset — for audits or publications | Yes, once at creation |

Start with **Loose link** if you are unsure; you can change to the other modes later.

---

## Add a loose link (most common)

1. Open the **DataObject** detail page for the experiment run or process step
   you want to document.
2. In the **References** panel, click **Add reference → Git repository**.
3. Paste the full repository URL into the **Repository URL** field
   (e.g. `https://gitlab.dlr.de/mygroup/analysis-scripts`).
4. Optionally fill in a **branch or tag** (leave blank to point at the
   default branch) and a **path** within the repository.
5. Leave the mode as **Loose link**, enter a short **name** for the reference,
   and click **Save**.

The reference appears in the References panel as a clickable repository link.

---

## Track a file and see its content (Tracked mode)

Tracked mode fetches a file from the repository and shows its content in a
read-only preview pane inside Shepard. Shepard also records the current commit
SHA so you can see whether the upstream file has changed since you last checked.

1. Follow steps 1–4 above, but enter the **path** to the specific file you want
   to preview (e.g. `notebooks/analysis.ipynb` or `config/parameters.yaml`).
2. Select **Tracked** as the mode.
3. Click **Save**.

To view the file content: open the reference's detail card in the References
panel and click **Preview**. Shepard fetches the file from the git host and
displays it inline. Click **Check for updates** to see whether the branch has
moved since the last preview.

> **Private repositories:** the preview only works if you have stored a PAT for
> the matching git host in your profile. See the next section.

---

## Pin a specific version (Pinned snapshot mode)

Use this when you want to permanently record that this dataset was produced by
a particular commit. Shepard resolves the branch or tag to a commit SHA at save
time and then freezes it — the SHA cannot change afterwards. When you export
the DataObject as an RO-Crate or a Regulatory Evidence Pack, the pinned SHA
appears as a reproducible `schema:SoftwareSourceCode` link.

1. Fill in the repository URL, branch (or tag), and optionally a path.
2. Select **Pinned snapshot** as the mode.
3. Click **Save**.

Shepard immediately resolves the branch to the current commit SHA. If the
repository is private and you have no stored PAT, the save fails with a 422
error — store a PAT first (see below), then try again.

> **The SHA is frozen after save.** If you need to update to a newer commit,
> delete the reference and create a new one. The old reference's SHA stays in
> the provenance trail, giving you a complete version history.

---

## Private repositories — store a Personal Access Token

Shepard encrypts and stores one PAT per git host in your user profile. You only
need to do this once per host.

1. Click your avatar in the top-right corner and open **Profile (⚙)**.
2. Scroll to the **Git credentials** section.
3. Click **+ Add credential**.
4. Enter the **host** exactly as it appears in your repository URL
   (e.g. `gitlab.dlr.de`, `github.com`).
5. Enter your **username** and the **Personal Access Token** from your git host.
   - GitLab: **User Settings → Access Tokens** (scope: `read_api`).
   - GitHub: **Settings → Developer settings → Personal access tokens → Fine-grained** (repo read).
6. Give the credential a friendly name (e.g. "DLR GitLab"), then click **Save**.

Shepard never returns the token value after storage. To rotate it, open the
credential and click **Replace PAT**.

---

## Edit or delete a git reference

- To change the URL, branch, or path: hover the reference row in the References
  panel and click the **pencil** icon. (The mode cannot be changed after creation.)
- To remove the reference entirely: hover the row and click the **bin** icon,
  then confirm.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| "Git repository" not in the Add reference menu | Git plugin disabled | Ask your admin to enable `shepard-plugin-git` |
| Preview shows "no credential for this host" | No PAT stored for this git host | Add a credential in your profile (see above) |
| "Pinned snapshot" save fails with 422 | Branch/tag resolution failed — possibly a private repo with no PAT | Store a PAT first, then retry |
| Preview content is stale | 5-minute server cache | Click **Check for updates** to force a refresh |
| "Unsupported host" message | Gitea / self-hosted GitHub Enterprise not configured | Ask your admin to add the host to `shepard.git.adapter.gitea.hosts` or `shepard.git.adapter.github.hosts` |

---

## See also

- [Link datasets](/help/link-datasets/) — connect DataObjects with Predecessor/Successor links
- [Work with notebooks](/help/work-with-notebooks/) — open `.ipynb` files from a DataObject in JupyterHub
- [Provenance tracing](/help/provenance-tracing/) — explore the full lineage of your data
- [Git references reference](/reference/git-references/) — entity model, all REST endpoints, RO-Crate integration
