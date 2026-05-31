package de.dlr.shepard.v2.notifications.transport.io;

import java.util.List;

/**
 * NTF1-BACKEND-LIST — response envelope for
 * {@code GET /v2/admin/notifications/transports}.
 *
 * <p>Single-field envelope ({@code items}) rather than a bare JSON
 * array so future top-level metadata (pagination, totals, filters
 * applied) can be added without a breaking shape change.
 */
public record NotificationTransportListIO(List<NotificationTransportReadIO> items) {}
