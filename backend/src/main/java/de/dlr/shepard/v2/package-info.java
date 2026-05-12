/**
 * Reserved namespace for {@code /v2/...} JAX-RS resources. Per
 * {@code CLAUDE.md}'s API-version policy + {@code aidocs/47 §2}:
 * every resource in this package (or sub-packages) MUST carry a
 * {@code @Path} value starting with {@code /v2/}; resources outside
 * MUST NOT. Fenced by
 * {@code de.dlr.shepard.architecture.V2NamespaceTest}.
 *
 * <p>The legacy upstream surface stays frozen at
 * {@code /shepard/api/...}; this {@code /v2/} shelf is for additive
 * features. {@code aidocs/25} L2d will eventually formalise the
 * split with native {@code appId}-based identifiers.
 *
 * <p>A0 (instance-admin role) is the first tenant of this package —
 * {@code de.dlr.shepard.v2.admin.*} hosts the admin REST surface
 * gated by {@code @RolesAllowed("instance-admin")}.
 */
package de.dlr.shepard.v2;
