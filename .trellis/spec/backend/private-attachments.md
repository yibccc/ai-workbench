# Private RustFS Attachments

## 1. Scope / Trigger

Use this contract for V18, attachment upload/read/recovery/cleanup, storage adapters, file validation, native/Docker initialization and proxy limits. Files belong to one owner and post. Publication references follow [Member Community Publications](community-publication.md); browser behavior follows [Community UI](../frontend/community-publishing.md).

## 2. Signatures

- `POST /api/me/posts/{postId}/attachments` is one multipart file with exactly `file`, `expectedVersion`, `requestId`; successful upload returns `{attachment:AttachmentInfo,version}`.
- `AttachmentInfo={id,kind,fileName,contentType,size,state,safeFailureCode}`; kind is IMAGE/PDF/MD. Public projections contain READY metadata only, no key/bucket/provider URL or credentials.
- `GET/HEAD /api/me/posts/{postId}/attachments/{id}` reads the author's READY resource; `GET/HEAD /api/community/posts/{postId}/attachments/{id}` requires membership and its current visible publication reference.
- `POST .../attachments/recover` and `POST .../attachments/cleanup` return `{version,results:[{attachmentId,state,safeFailureCode}]}`; clients inspect every result, not just HTTP200.
- `ObjectStorage.put(String key,Path validatedFile,long size,String contentType,String sha256)`, `open(key)->StoredObject{stream(),size(),close()}`, `delete(key)`.
- `python scripts/local/initialize-storage.py --env-file <ignored-file> --endpoint <explicit-local-endpoint> --bucket <explicit-bucket> --app-identity-env WORKBENCH_STORAGE_ACCESS_KEY` initializes and verifies a private RustFS identity/bucket. `--app-identity-env` is a variable name, never the credential value. Explicit `--update-policy` only rebinds the designated identity from a known minimal policy to its own canonical policy.

V18 owns `community_attachments`, `community_draft_attachments`, `community_revision_attachments`. Composite post/owner/reference FKs and immutable object keys protect ownership/history.

## 3. Contracts

### Formats, exact bytes and validation

| Type | Extensions | Inclusive maximum |
|---|---|---:|
| IMAGE | jpg/jpeg/png/webp | 5,242,880 bytes |
| PDF | pdf | 20,971,520 bytes |
| MD | md | 1,048,576 bytes |

The current draft plus active reservations may contain at most ten distinct IDs and 52,428,800 bytes. Duplicate body references count once; distinct objects count separately. Retained history does not consume current-draft quota but always retains its objects. Empty MD is valid UTF-8; no unapproved minimum, pixel/frame/page limit, user-wide quota, conversion or automatic day-based cleanup is added.

Transport and business limits differ: Servlet file=21MiB/request=22MiB, Nginx upload-only request limit=22m; the other API ingress retains its existing limit. `AttachmentController` checks the owner before accessing lazy multipart parts; Spring Security authenticates/validates CSRF before successful business processing. Do not use bound MultipartFile parameters to claim owner checking precedes parsing.

Stage to a bounded private temporary file, count/hash actual bytes and sanitize display filename. Do not trust client MIME/Content-Length or read an entire attachment into byte[]. Real JPEG/PNG validation includes safely sampled decode/PNG CRC; WebP checks the complete RIFF/chunk/frame/encoding-header structure and decodes safely allocatable frames. TwelveMonkeys3.15.2's 2048 expansion guard and VP8L/ALPH full raster allocations are not format grammar or a product pixel quota: the high-compression structural branch has explicitly limited bitstream proof. PDFBox3.0.8 strict file-backed xref/catalog/page-tree validation is separate from encrypted outer-envelope validation; password/PublicKey/ObjStm files are not silently disallowed or accepted merely because a password exception occurred. MD uses strict UTF-8/BOM and refuses malformed text/NUL/binary controls. None of these checks certifies malware absence.

The trusted Java validation worker has actual process heap/direct/metaspace/time budgets, forced termination plus verified exit, and one private scratch directory cleaned after exit. Remove DB/model/storage/root/AWS environment from that child. Both flat tests and Boot PropertiesLauncher/nested-library execution require real evidence; Future timeout alone is insufficient.

### Durable states, transactions and retries

- States are UPLOADING/READY/FAILED/DELETING/DELETE_FAILED/DELETED. A failed upload retains its immutable key/creation time and original upload receipt; the current operation token/deadline may rotate during cleanup. Retry after confirmed FAILED uses a new requestId/ID/key, not overwriting the failed row or an old publication object.
- All short reserve/finalize/release/bind/recover/delete-mark operations lock the owner post, then attachment rows in a consistent order. External put/delete/streaming do not hold a database transaction.
- Reserve checks the same request ID plus actual file hash/length before the old aggregate version. Successful response loss replays the original receipt; UPLOADING replay does not initiate another put. Different content/key parameters conflict. Binding uses only same-owner/post READY IDs and exact current quota inside the existing save/publish transaction.
- Persist attempt token/deadline. Finalization requires the same active UPLOADING token/reservation; another tab's removal, recovery or DELETING mark prevents late READY/rebinding. Failure releases quota and preserves original input/reference/history plus a traceable cleanup key. DB-confirmation failure cannot report READY.
- Author-triggered recover releases expired reservations and records FAILED, without an automatic retention-day rule. Cleanup rechecks all draft/current/retained-history references and active reservations, marks DELETING under the same locks, then deletes outside the transaction. Delete failure is retryable. Keep tombstones/keys for uncertain late put and preserve the original FAILED upload receipt even after cleanup.
- Owner/public multi-query reads use one database snapshot. A reader can only obtain the current PUBLISHED revision's READY file. Withdraw/hide/reference replacement blocks every new read, including HEAD/conditions/range; already authorized streams/copied bytes are not recalled.

### Storage, headers and environment

RustFS1.0.0 is the only current adapter: AWS2.55.10/Apache5, known-length Path PUT, forced path style, nonchunked body and Base64 of raw SHA256 bytes. Verify server bytes/hash/length and wrong-checksum refusal; no protocol/old-SDK/unsigned fallback.

All reads authorize before opening/emitting metadata; use bounded synchronous copy and close/abort provider input on completion/disconnect. IMAGE is inline with verified MIME; PDF/MD are Content-Disposition attachment with safe UTF-8 filename. Return `no-store, private`, `nosniff`, `Accept-Ranges:none`; authorized Range/conditional requests return full200, not304/206. Never redirect/sign a browser cloud URL.

Current backend keys are `WORKBENCH_STORAGE_{PROVIDER,ENDPOINT,REGION,BUCKET,ACCESS_KEY,SECRET_KEY,PATH_STYLE,CONNECT_TIMEOUT,READ_TIMEOUT,API_CALL_ATTEMPT_TIMEOUT,API_CALL_TIMEOUT,RESERVATION_DURATION}`. Require rustfs/pathstyle, well-formed endpoint without credentials/query/fragment, positive connect/read timeouts, attempt≥read, total≥attempt, reservation>total. Native endpoint is localhost9000; Compose uses rustfs9000. Root `RUSTFS_ACCESS_KEY/SECRET_KEY` is only for the RustFS service/explicit initializer, not application SDK/native backend/frontend.

Initialize a private bucket with no anonymous policy/ACL. Application identity has only the selected bucket's `community/attachments/*` and `interview/resumes/*` Put/Get/Delete, no ListBucket, and explicit `Deny admin:*` (RustFS1.0 self-management can use deny-only semantics). The canonical policy name is derived from bucket plus the designated application identity, avoiding expansion of a shared policy for another account. Explicit maintenance accepts only recognized minimal policy shape, creates/reuses that identity's canonical policy and rebinds only it; an existing shared policy stays unchanged. Unknown permissions/bindings fail closed, passwords never rotate. Prove real current-object anonymous GET/HEAD, both prefixes, and forbidden outside-prefix/ListBucket/bucket/admin actions. Resume ownership/current/original cleanup follows [Private Current Resumes](./private-resumes.md), not community post APIs.

DB stores key/verified type/length/SHA, not vendor/bucket/endpoints/signatures. A future Aliyun adapter can preserve ObjectStorage, keys and HTTP/DB contracts; it still needs actual SDK/resource tests and same-key byte/hash migration before configuration cutover. This task does not implement cloud API, dual-provider fallback or automatic data copying.

## 4. Validation & Error Matrix

| Condition | Result |
|---|---|
| Anonymous read; foreign/private or invisible file | 401 / 404 with no filename/size/bytes |
| Wrong/multiple multipart parts or invalid fields | 400 `UPLOAD_REQUEST_INVALID` |
| Actual file above its maximum | 413 `ATTACHMENT_TOO_LARGE`, no reservation |
| Invalid format/text | 400 `ATTACHMENT_FORMAT_INVALID`, no READY |
| Parser process/resource failure | 503 `ATTACHMENT_VALIDATION_UNAVAILABLE`; do not invent subtype bans |
| Stale version / changed request / collection quota | 409 `VERSION_CONFLICT` / `UPLOAD_REQUEST_CONFLICT` / `ATTACHMENT_QUOTA` |
| Bind non-READY/invalid reference | 409 / safe404 or400, no partial draft/revision |
| Put/confirm/temporary IO unavailable | Curated STORAGE_UNAVAILABLE/UPLOAD_CONFIRMATION_FAILED/UPLOAD_TEMPORARY_IO, safe currentVersion where documented |
| Mixed cleanup/recovery results | Per-ID state and safeFailureCode; no false all-success toast |

## 5. Good / Base / Bad Cases

- Good: F1 stays readable to members while F2 is private; explicit publish switches references; F1 remains author-history-readable and cannot be cleaned.
- Base: zero-byte MD, repeated same-ID Markdown image use and an empty current file set are valid; old historical objects do not consume the new draft's quota.
- Bad: filename-based object keys, browser pre-signed URLs, an old READY receipt granting a live version, deleting after a nonlocked reference check, or losing an old failed key on retry.

## 6. Tests Required

- Real Servlet/Redis/PostgreSQL A/B/ADMIN/anonymous/CSRF, GET+HEAD+conditions+Range/ref replacement and complete bytes/headers without redirect.
- Valid exact/+1 files; 20MiBPDF+20MiBPDF+5MiBPNG+5MiBPNG=50MiB; eleventh ID, duplicate ID, historical quota, real concurrent reservations/replay and direct SQL final counts.
- Durable failed put/confirmation, delayed put after removal/cleanup, deleting binding conflict, crash/expired reservation recovery and original FAILED receipt after cleanup. Separately kill an actual JVM at a committed UPLOADING synchronization point, restart and verify body/refs/quota/tombstone.
- Ordinary/encrypted/PublicKey PDF, animated/high-compression WebP, malformed/truncated containers, strict UTF8/BOM/emptyMD and original SHA under bounded worker/fatJAR.
- Actual RustFS restricted identity, no unsigned read, prefix/admin refusal, Put/Get/hash/Delete and incorrect checksum rejection; Nginx→packaged JVM→RustFS exact limits/overhead; no secrets in frontend/validator/browser-verification children.

## 7. Wrong vs Correct

Wrong: after response loss, send a fresh upload key; after deleting an editor row, immediately delete its storage object; after finalize, unconditionally add it back to the draft.

Correct: replay the original file/key/expectedVersion, consume its durable receipt, re-read the current owner state, and bind only under the same short post transaction. Deletion requires all-reference checks and a durable deletion token, while late finalization is fenced out.
