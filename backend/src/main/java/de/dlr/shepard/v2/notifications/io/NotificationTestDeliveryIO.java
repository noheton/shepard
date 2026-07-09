package de.dlr.shepard.v2.notifications.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Response from {@code POST /v2/admin/notifications/transports/{appId}/test}. */
@Schema(description = "Result of a test notification delivery attempt for a configured transport.")
public record NotificationTestDeliveryIO(String status, String transport) {}
