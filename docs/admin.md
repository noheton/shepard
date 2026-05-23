---
layout: default
title: Admin (moved)
description: Admin landing — moved to /admin/ subtree.
stage: deployed
last-stage-change: 2026-05-23
audience: admin
permalink: /admin
redirect_to: /admin/
---

> **Moved.** The admin guide is now an indexed subtree at
> [**/admin/**]({{ '/admin/' | relative_url }}). This stub will be removed in v6.1.

The previous single-page admin guide has been split per
`feedback_three_audience_docs.md` into:

- [Admin landing]({{ '/admin/' | relative_url }})
- [System requirements]({{ '/admin/system-requirements/' | relative_url }})
- [Install]({{ '/admin/install/' | relative_url }})
- [Configuration]({{ '/admin/config/' | relative_url }})
- [Upgrade]({{ '/admin/upgrade/' | relative_url }})
- [Backup and restore]({{ '/admin/backup/' | relative_url }})
- [Storage substrate]({{ '/admin/storage/' | relative_url }})
- [Authentication]({{ '/admin/auth/' | relative_url }})
- [Observability]({{ '/admin/observability/' | relative_url }})
- [Security]({{ '/admin/security/' | relative_url }})

<script>
// Belt-and-braces client-side redirect for legacy inbound links.
window.location.replace("{{ '/admin/' | relative_url }}");
</script>

<meta http-equiv="refresh" content="0; url={{ '/admin/' | relative_url }}">
