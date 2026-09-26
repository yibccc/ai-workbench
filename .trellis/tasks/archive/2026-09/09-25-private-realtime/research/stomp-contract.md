# STOMP wire contract (implemented)

## Client connection

- Open a native WebSocket at `/ws/events` using the same origin and cookie jar as the authenticated HTTP client. The HTTP handshake requires a live `WORKBENCH_SESSION`, an `XSRF-TOKEN` cookie, and an exact `Origin` listed in `WORKBENCH_WS_ALLOWED_ORIGINS` (the Spring property is `workbench.ws.allowed-origins`). Missing or foreign origins are rejected; no SockJS endpoint is registered.
- Fetch `GET /api/auth/csrf` before login/CONNECT. Send its token in the **native STOMP CONNECT header** `X-XSRF-TOKEN`. The header is case-sensitive in the current guard. Do not put identity or CSRF in the URL. The server derives `WorkbenchPrincipal` and HTTP Session ID from the authenticated handshake and verifies the CSRF header again on CONNECT.
- The only allowed client subscription is exactly `/user/queue/workbench-events`. Direct `/queue/**`, another `/user/**`, wildcard destinations, and client `SEND` frames are denied and the socket closed. `UNSUBSCRIBE` and `DISCONNECT` are permitted. Business writes use HTTP.
- A browser should use a matching frontend/backend deployment version; the old raw JSON WebSocket protocol is removed. Reconnect with fresh HTTP authentication and CSRF state, then subscribe again and GET tracked objects. STOMP provides no offline queue.

## Event and delivery

The server sends JSON to each subscribed, currently valid Session of the persisted owner. Shape:

```json
{"eventId":"UUID","kind":"INPUT|REPORT","entityId":"UUID","state":"state string","revision":1,"occurredAt":"ISO instant"}
```

The event is only a refresh signal; it contains no business body. Clients re-GET the object and treat HTTP as authoritative. `revision` increases inside one backend process and may restart after a process restart; use `eventId` for duplicate suppression without rejecting a valid post-restart refresh solely because its revision is lower.

`WorkbenchEventHub.publishAfterCommit(UUID ownerUserId, String kind, UUID entityId, String state)` registers delivery after a successful transaction commit. It enumerates only the owner's local sockets, verifies each Session with `SessionAccess.isLive`, checks that its STOMP session belongs to that user in `SimpUserRegistry`, and targets that single session through `/user/{userId}/queue/workbench-events` with `simpSessionId`. Outbound MESSAGE frames are checked again. A failed notification does not reverse a committed write. This Simple Broker contract supports one backend instance and no offline messages.

Logout and indexed Session revocation call `WorkbenchSocketSessions.closeSession(httpSessionId)` synchronously; Spring Session destroyed events also close connections. Database authentication version and Redis activity checks on inbound frames and delivery cover stale connections when indexed cleanup races with a new Session. Automatic CONNECT, SUBSCRIBE, heartbeat and push reception never touch the explicit active-time marker, so they cannot extend the seven-day deadline.

Successful self-password change retains the current HTTP Session but changes its immutable authentication version. The server closes that Session's old socket immediately; its new WebSocket handshake uses the updated Principal and can resubscribe. Other Sessions are revoked and cannot reconnect without fresh login.

## Verification in this implementation window

`mvn -q '-Dtest=PrivateRealtimeIntegrationTest,WorkbenchEventHubTest,WorkbenchWebSocketConfigTest,WorkbenchStompGuardTest' test` exited 0 on Windows Java 17, using the isolated `d9_live_acceptance` PostgreSQL schema and local Redis. Real STOMP frames verified owner-only delivery across two users and two sessions, logout and indexed revoke closure, preserved current Session reconnect after self-password change, anonymous/missing/foreign Origin handshake rejection, invalid CSRF/destination/SEND rejection, and expiration at the 604800-second boundary after automatic subscription and push. Unit tests verified no notification after transaction rollback. The integration test did not make a paid model call or clear existing business data.

The subsequent `mvn -q '-Dtest=PrivateRealtimeIntegrationTest' test` exited 0 with 7 tests / 0 failures / 0 errors. It additionally exercised actual `InputPersistenceService.createOrGet` and `ReportPersistenceService.prepare` through real PostgreSQL transactions: no event before commit, owner-only event and persisted row after commit, and neither row nor event after rollback. Actual admin HTTP disable, role change and password reset each closed the target's old STOMP socket, blocked subsequent events and HTTP reuse, and allowed a newly authenticated Session to resubscribe after the final reset. Test-created rows/accounts were removed by ID only.

Cross-layer browser reconnect, 15-second HTTP fallback, deployment proxy Origin, and service restart behavior require parent task acceptance evidence.
