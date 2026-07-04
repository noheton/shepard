---
title: Get notified about activity
description: How to read and manage your in-app notifications in Shepard
permalink: /help/get-notified/
layout: default
audience: user
---
# Get notified about activity

Shepard sends **in-app notifications** for events you care about — collection
activity, data imports completing, reviews requested, and more. A bell icon in
the top navigation bar shows when new notifications arrive.

---

## Open the notification panel

Click the **bell icon** (`mdi-bell-outline`) in the top navigation bar. A
panel slides in from the right showing your most recent notifications.

An unread count badge appears on the bell when you have unread messages.

---

## Read a notification

Each notification shows:

| Part | Description |
|------|-------------|
| **Icon** | Category colour and icon (info, success, warning, error) |
| **Title** | Short summary of the event |
| **Body** | Detail text (truncated; scroll to read all) |
| **Time** | How long ago the notification was created |
| **Go** button | Opens the related entity (only shown when an action URL is available) |

Click **Read** on a notification to mark it as read. Click the **✕** button to
dismiss and remove it from the panel.

Click **Mark all read** at the top of the panel to mark every notification as
read at once.

---

## What generates notifications

Notifications are sent by Shepard's backend when:

- A background job (data import, snapshot, migration) completes or fails
- Another user requests a review on a DataObject you contribute to
- An admin sends a system-wide broadcast via **Admin → Notifications**
- An AI annotation run finishes on a collection you own

The full set of notification categories grows as features are added. Your
administrator can send a smoke-test notification from **Admin → Notifications**
to confirm the in-app transport is working.

---

## Email and Matrix notifications

SMTP (email) and Matrix transport are under development and not yet available
to end users. When they ship, your administrator will configure them and you
will be able to choose per-category delivery preferences in **Me → Notification
preferences**.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| Bell never shows a badge | No events have generated notifications for your account | Ask your administrator to send a test notification via **Admin → Notifications → Send test** |
| Notifications panel is empty | All notifications have been dismissed | Dismissed notifications are removed permanently; they do not reappear |
| "Go" button missing | The notification's event does not link to a specific entity | Normal — broadcast and system notifications have no action URL |

---

## Related help

- [Monitor collection activity](/help/monitor-collection-activity/) — the activity feed inside a collection
- [Admin activity log](/help/admin-activity-log/) — admin-level audit of all instance activity
