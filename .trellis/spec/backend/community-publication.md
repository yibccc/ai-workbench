# Member Community Publications

## 1. Scope / Trigger

Use this contract when changing community/profile/publishing controllers, V17 tables, material composition, author projections or moderation. Community publishes independent snapshots; it never makes the private work-record/report APIs public. Related file behavior belongs to [Private Attachments](private-attachments.md).

## 2. Signatures

- `GET /api/community/posts/page?type=&authorId=&page=0&size=5` returns `PageResponse<PostCard>` in first-publication descending order.
- `GET /api/community/posts/{id}` returns the currently visible `PostDetail`; `GET /api/community/authors/{id}` returns `{id,nickname,bio}`.
- `GET/PUT /api/me/community/profile` reads/updates the caller's profile; update is `{nickname?,bio?,version}`.
- `GET /api/me/posts/page?status=&page=&size=` and `GET /api/me/posts/{id}` return owner-only projections.
- `POST /api/me/posts {type,businessDate?,title?,summary?,bodyMarkdown?}` creates a private draft.
- `PUT /api/me/posts/{id} {version,type,businessDate?,title?,summary?,bodyMarkdown?,attachmentIds}` saves only a private draft.
- `POST /api/me/posts/{id}/publish` accepts that complete inspected input plus `requestId` and `visibility:"MEMBERS"`; returns `{postId,version,revisionId,revisionNo,publishedAt,firstPublishedAt}`.
- `POST /api/me/posts/{id}/withdraw {version}` returns `{postId,status,version}`.
- `POST /api/admin/community/posts/{id}/hide {expectedRevisionId,reason}` returns `{postId,status:"HIDDEN"}`; the admin does not receive the author's private aggregate version.
- `GET /api/me/community/sources/page?date=YYYY-MM-DD&page=0&size=5`; `POST /api/me/community/share-drafts {date,selections:[{recordId,fields}],includeFocus}` composes a private DAILY draft.

V17 owns `community_posts`, `community_post_drafts`, `community_post_revisions`, `community_public_profiles` and `community_moderation_audit`. Keep applied migrations immutable. Current code follows Controller → Service interface → ServiceImpl → Mapper/XML.

## 3. Contracts

- Obtain owner exclusively through `CurrentUser`; ADMIN has no private-workspace bypass. Public joins select only `PUBLISHED` plus the same post/owner's `current_revision_id`; private `source_selection` and account username/auth/session fields never enter public DTOs.
- Post type is immutable `DAILY | MOMENT | BLOG`. DAILY requires `businessDate`; other types have no business date. To change type, explicitly save the previous private draft and create another post.
- Mutable draft and immutable revision each store title, BLOG summary, body, business date/type and private provenance. Title/summary/body limits are 200/500/100000 Java String characters; draft text can be unfinished, while publication requires nonblank body and DAILY/BLOG title. Nickname/bio limits are 40/500; missing nickname displays `未设置昵称`, never login username. Hide reason is nonblank and at most 1000 characters.
- First publish sets `first_published_at` once. Updates/republication after withdrawal preserve it and do not reorder the public feed. Every publication retains its immutable revision; readers do not have a historical-revision endpoint.
- Save changes only the draft. Publish locks the owner post, normalizes the complete submitted payload, checks an existing `requestId` receipt before the live version, then validates all fields/files and writes draft, revision, references and current pointer in one transaction. Fingerprint includes expected version, scope, type/date/title/summary/body and first-occurrence ordered unique attachment IDs. Never recompute it from a later draft.
- The revision persists the exact successful `result_version`. Replaying the same fingerprint returns the original result, not the current post version; it never reapplies lifecycle state after withdrawal/hide. Different payload on the same key is 409. Community time values use microsecond precision, matching PostgreSQL and the original/replayed JSON.
- `DRAFT/PUBLISHED/WITHDRAWN` may publish; `HIDDEN` cannot publish or restore. Private maintenance does not bypass hiding. Admin hide locks the same post, validates the public `expectedRevisionId`, and records actor/reason/audit with the state change atomically. There is no restore API.
- Public/owner multi-query projections use one `REPEATABLE_READ` snapshot so a pointer switch cannot combine an old body with new/empty attachment metadata. Stream authorization rules remain in the attachment contract.
- Material queries reuse `WorkRecordMapper.dailyPresentation`: the existing same-owner/task/day completion+focus merge occurs before pagination. Selected-ID revalidation is unpaged and uses the same projection, owner and Shanghai `[start,nextStart)` boundaries. Foreign/missing IDs fail without partial draft creation; a no-longer-selectable own source conflicts.
- Selected fields are `CONTENT`, `COMPLETION_RESULT`, `PROGRESS`; project/time/notes/private associations are not automatically copied. Only explicitly opted-in selected raw-focus UUIDs contribute to focus totals; absorbed focus is not another completion. Sources are private frozen provenance with no FK that prevents original work-record deletion. Same-day multiple DAILY posts are allowed.
- Reuse `PageQueries`/zero-based paging and clear PageHelper before DTO-associated queries. Protect successful/error community responses with `no-store, private` and `nosniff`. New publications add no public STOMP topic, interaction counter, notification, anonymous scope or legacy-route compatibility.

## 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Anonymous/expired read | 401, no protected title/body/metadata |
| Valid user/ADMIN requests another author's private UUID | 404 before disclosing state/version or writing |
| Missing/invalid write CSRF; USER moderation | 403, no mutation |
| Invalid type/date/text/scope or missing required query | Safe 400 ProblemDetail; only MEMBERS is accepted |
| Stale write, key/payload mismatch, hidden publication or obsolete moderation revision | 409, no silent overwrite |
| Public post withdrawn/hidden | 404 and excluded from public pages/author feed |
| New profile initial insert races | One success and one 409, not duplicate rows |

## 5. Good / Base / Bad Cases

- Good: preview newer local body/summary and publish that exact tuple; modifying the original source or saving another private draft does not alter reader version one.
- Base: an empty private draft, no files and unset nickname are valid. Explicitly publishing a MOMENT may omit its title.
- Bad: serializing AccountResponse as the public author, replacing a public body on Save, publishing a stale last-saved body, granting the live version to an old receipt, or performing file binding after publication commits.

## 6. Tests Required

- Real PostgreSQL migrations: empty schema and owned V16 upgrade, same-owner/type/current-pointer FK constraints, unchanged prior checksums and original source deletion.
- Real HTTP/Cookie/Redis: A/B/ADMIN/anonymous, CSRF, safe public fields, profile versions, scope rejection and refusal without partial writes.
- Publish/save/hide concurrency, same-key/lost-response replay, unchanged first timestamp and fixed result_version; inject failure after an earlier SQL write and verify audit/draft/revision/pointer/version rollback.
- Multi-page/selected source projection, Shanghai boundaries, merged completion once, selected raw-focus union and public-field/privacy defaults.
- Original auth, private STOMP, task/focus completion, records and report-source suites, plus browser publication flows. Do not call text-only or fixture checks attachment acceptance.

## 7. Wrong vs Correct

Wrong: `publish(id, version)` copies whichever saved draft happens to be present, then binds attachments in a separate transaction.

Correct: submit the inspected tuple; the same `@Transactional` method locks the owner post, checks the durable receipt, validates READY file references, inserts the immutable snapshot/references and changes the current pointer. Save remains a separate private operation.
