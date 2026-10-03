import { useCallback, useEffect, useRef, useState } from 'react'
import { createPost, fetchOwnPost, publishPost, recoverAttachments, cleanupAttachments, savePost, uploadAttachment, type AttachmentMaintenance, type DraftInput, type OwnerPost, type PublishInput, type PublishResult } from '../../api/publishing'
import { fetchOwnProfile, type AttachmentInfo, type PostType, type PublicAuthor } from '../../api/community'
import type { Account } from '../../api/auth'
import { ApiError } from '../../api/http'
import type { ViewId } from '../../components/layout/routes'
import { Dialog } from '../../components/Dialog'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { localDate } from '../../utils/date'
import { PublicationDocument } from '../community/PublicationDocument'
import { Unavailable } from '../community/CommunityPages'
import { Button, CommunityIcon, Heading, LoadState, Notice, Stepper } from '../community/ui'
import { bytesLabel, errorMessage, isAborted, typeIcons, typeLabels } from '../community/model'

export type NavigationGuard = (next: ViewId) => Promise<boolean>
type LeaveChoice = { target?: ViewId; type?: PostType; resolve: (accepted: boolean) => void }
type UploadAttempt = { postId: string; expectedVersion: number; requestId: string }
type UploadEntry = {
  key: string; file: File; state: 'UPLOADING' | 'FAILED' | 'PENDING'; error?: string
  attempt?: UploadAttempt; receipt?: { attachment: AttachmentInfo; version: number }
  resolution?: 'UNKNOWN' | 'ACTIVE' | 'FAILED' | 'VERSION_CONFLICT'
}
const blank = (type: PostType): DraftInput => ({ type, businessDate: type === 'DAILY' ? localDate(new Date()) : null, title: '', summary: '', bodyMarkdown: '', attachmentIds: [] })
const serial = (input: DraftInput) => JSON.stringify(input)
const fromPost = (post: OwnerPost): DraftInput => ({ type: post.type, businessDate: post.draft.businessDate, title: post.draft.title, summary: post.draft.summary, bodyMarkdown: post.draft.bodyMarkdown, attachmentIds: post.draft.attachmentIds })
const samePostId = (left?: string, right?: string) => !!left && !!right && left.toLowerCase() === right.toLowerCase()

export function PublishingEditor({ account, postId, initialType = 'MOMENT', routePreview, navigate, registerGuard }: {
  account: Account; postId?: string; initialType?: PostType; routePreview: boolean; navigate: (next: ViewId) => void; registerGuard: (guard: NavigationGuard | null) => void
}) {
  const [input, setInput] = useState<DraftInput>(() => blank(initialType)); const inputRef = useRef(input)
  const [saved, setSaved] = useState(() => serial(blank(initialType))); const savedRef = useRef(saved)
  const [post, setPost] = useState<OwnerPost | null>(null); const postRef = useRef(post)
  const routeContext = useRef(postId ? `post:${postId}` : `new:${initialType}`)
  const [attachments, setAttachments] = useState<AttachmentInfo[]>([]); const [uploads, setUploads] = useState<UploadEntry[]>([])
  const [maintenance, setMaintenance] = useState<{ operation: 'recover' | 'cleanup'; result: AttachmentMaintenance } | null>(null)
  const [loading, setLoading] = useState(!!postId); const [error, setError] = useState<string | null>(null); const [notFound, setNotFound] = useState(false)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [versionConflict, setVersionConflict] = useState(false)
  const [previewLocal, setPreviewLocal] = useState(false); const [consent, setConsent] = useState(false); const [busy, setBusy] = useState(0)
  const [leave, setLeave] = useState<LeaveChoice | null>(null); const leaveRef = useRef<LeaveChoice | null>(null)
  const [author, setAuthor] = useState<PublicAuthor>({ id: account.id, nickname: '未设置昵称', bio: '' })
  const queue = useRef<Promise<unknown>>(Promise.resolve()); const mounted = useRef(true); const locked = useRef(false); const picker = useRef<HTMLInputElement>(null); const textarea = useRef<HTMLTextAreaElement>(null)
  const busyRef = useRef(0)
  const publishAttempt = useRef<{ payload: PublishInput; result?: PublishResult } | null>(null)
  const dirty = serial(input) !== saved; const preview = routePreview || previewLocal
  useBeforeUnload(dirty)
  useEffect(() => { inputRef.current = input }, [input])
  useEffect(() => { savedRef.current = saved }, [saved])
  useEffect(() => {
    mounted.current = true
    const controller = new AbortController()
    void fetchOwnProfile(controller.signal).then(profile => setAuthor({ id: account.id, nickname: profile.nickname, bio: profile.bio })).catch(() => undefined)
    return () => { mounted.current = false; controller.abort(); leaveRef.current?.resolve(false) }
  }, [account.id])
  useEffect(() => {
    if (!postId && routeContext.current !== `new:${initialType}`) {
      routeContext.current = `new:${initialType}`
      const draft = blank(initialType)
      postRef.current = null; inputRef.current = draft; savedRef.current = serial(draft); publishAttempt.current = null
      void Promise.resolve().then(() => { setPost(null); setInput(draft); setSaved(serial(draft)); setAttachments([]); setUploads([]); setPreviewLocal(false); setConsent(false); setError(null); setNotFound(false); setLoading(false); setVersionConflict(false) })
      return
    }
    routeContext.current = postId ? `post:${postId}` : `new:${initialType}`
    if (!postId || samePostId(postRef.current?.postId, postId)) return
    const controller = new AbortController()
    void Promise.resolve().then(() => { if (!controller.signal.aborted) { setError(null); setNotFound(false); setVersionConflict(false) } })
    void fetchOwnPost(postId, controller.signal).then(next => {
      if (controller.signal.aborted) return
      postRef.current = next; publishAttempt.current = null; setPost(next); const draft = fromPost(next); inputRef.current = draft; savedRef.current = serial(draft); setInput(draft); setSaved(serial(draft)); setAttachments(next.attachments); setPreviewLocal(false); setConsent(false); setLoading(false)
    }).catch((caught: unknown) => { if (!controller.signal.aborted) { setError(errorMessage(caught)); setNotFound((caught as { status?: number }).status === 404); setLoading(false) } })
    return () => controller.abort()
  }, [postId, initialType, loadAttempt])
  const enqueue = useCallback(<T,>(operation: () => Promise<T>): Promise<T> => {
    busyRef.current++; setBusy(value => value + 1)
    const result = queue.current.then(() => { if (!mounted.current) throw new DOMException('编辑器已关闭', 'AbortError'); return operation() })
    queue.current = result.catch(() => undefined)
    return result.finally(() => { busyRef.current--; if (mounted.current) setBusy(value => value - 1) })
  }, [])
  const adopt = (next: OwnerPost) => { postRef.current = next; if (mounted.current) setPost(next) }
  const acknowledge = (next: OwnerPost) => {
    adopt(next); setAttachments(next.attachments)
    const snapshot = serial(fromPost(next)); savedRef.current = snapshot; setSaved(snapshot)
    publishAttempt.current = null; setConsent(false)
  }
  const ensurePost = async () => {
    if (postRef.current) return postRef.current
    const current = { ...inputRef.current, attachmentIds: [] }; const { attachmentIds: omitted, ...creation } = current; void omitted
    const next = await createPost(creation)
    if (!mounted.current) throw new DOMException('编辑器已关闭', 'AbortError')
    adopt(next); savedRef.current = serial(current); setSaved(serial(current)); setAttachments(next.attachments); return next
  }
  const saveSnapshot = async (snapshot: DraftInput) => {
    const current = await ensurePost()
    const result = await savePost(current.postId, { ...snapshot, version: current.version })
    if (!mounted.current) return
    publishAttempt.current = null
    adopt({ ...postRef.current!, version: result.version, draft: { ...postRef.current!.draft, ...snapshot, savedAt: result.savedAt } }); savedRef.current = serial(snapshot); setSaved(serial(snapshot)); setError(null)
  }
  const save = async (adoptRoute = true) => {
    if (locked.current) return false
    if (versionConflict) { setError('稿件版本已变化，请先读取当前版本，检查保留的输入后再明确重试。'); return false }
    locked.current = true; setError(null); const snapshot = structuredClone(inputRef.current)
    try { await enqueue(() => saveSnapshot(snapshot)); if (adoptRoute && !postId && postRef.current) navigate(`/publishing/posts/${postRef.current.postId}`); return true } catch (caught) { if (mounted.current && !isAborted(caught)) { if (caught instanceof ApiError && caught.status === 409) setVersionConflict(true); setError(errorMessage(caught)) }; return false } finally { locked.current = false }
  }
  const answerLeave = async (choice: 'continue' | 'discard' | 'save') => {
    const request = leaveRef.current; if (!request || locked.current) return
    if (choice === 'save' && !await save(false)) return
    leaveRef.current = null; setLeave(null); request.resolve(choice !== 'continue')
    if (choice !== 'continue' && request.type) navigate(request.type === 'DAILY' ? '/publishing/sources' : `/publishing/new/${request.type}`)
  }
  useEffect(() => {
    registerGuard(async next => {
      const id = postRef.current?.postId
      if (samePostId(id, /^\/publishing\/posts\/([^/]+)(?:\/preview)?$/.exec(next)?.[1])) return true
      if (busyRef.current) { setError('请求仍在处理中，请完成后再离开编辑器。'); return false }
      if (serial(inputRef.current) === savedRef.current) return true
      if (leaveRef.current) return false
      return new Promise<boolean>(resolve => { const choice = { target: next, resolve }; leaveRef.current = choice; setLeave(choice) })
    })
    return () => registerGuard(null)
  }, [dirty, registerGuard])
  const switchType = (type: PostType) => {
    if (type === input.type || leaveRef.current || busyRef.current) return
    const choice = { type, resolve: () => undefined }; leaveRef.current = choice; setLeave(choice)
  }
  const edit = (changes: Partial<DraftInput>) => { const next = { ...inputRef.current, ...changes }; inputRef.current = next; setInput(next); setConsent(false); publishAttempt.current = null }
  const syncVersion = async () => {
    const id = postRef.current?.postId; if (!id || busyRef.current) return
    setError(null)
    try { await enqueue(async () => { const next = await fetchOwnPost(id); if (mounted.current) { acknowledge(next); setVersionConflict(false) } }) }
    catch (caught) { if (mounted.current && !isAborted(caught)) setError(errorMessage(caught)) }
  }
  const uploadFile = (entry: UploadEntry) => enqueue(async () => {
    try {
      const current = await ensurePost()
      const attempt = entry.attempt ?? { postId: current.postId, expectedVersion: current.version, requestId: entry.key }
      setUploads(previous => previous.map(item => item.key === entry.key ? { ...item, attempt } : item))
      const result = entry.receipt?.attachment.state === 'READY' ? entry.receipt : await uploadAttachment(attempt.postId, entry.file, attempt.expectedVersion, attempt.requestId)
      if (!mounted.current) return
      setUploads(previous => previous.map(item => item.key === entry.key ? { ...item, receipt: result } : item))
      // A receipt identifies this file, but its version/ref membership may already be historical.
      const latest = await fetchOwnPost(attempt.postId)
      if (!mounted.current) return
      acknowledge(latest)
      const attachment = latest.attachments.find(item => item.id === result.attachment.id)
      if (attachment?.state === 'UPLOADING') {
        setUploads(previous => previous.map(item => item.key === entry.key ? { ...item, state: 'PENDING', resolution: 'ACTIVE', error: '原请求仍在上传。可查询结果；不会重复上传或占用额度。' } : item)); return
      }
      if (attachment?.state === 'FAILED') {
        setUploads(previous => previous.map(item => item.key === entry.key ? { ...item, state: 'FAILED', resolution: 'FAILED', error: attachment.safeFailureCode || '上传未完成，请重试。' } : item)); return
      }
      if (attachment?.state !== 'READY') throw new Error('原上传结果尚未确认，请查询原请求结果。')
      if (latest.draft.attachmentIds.includes(attachment.id)) {
        const next = { ...inputRef.current, attachmentIds: [...new Set([...inputRef.current.attachmentIds, attachment.id])] }
        inputRef.current = next; setInput(next)
      } else setError('原上传已完成，但当前草稿已移除该附件。未重新绑定或覆盖当前草稿，请检查后继续。')
      setUploads(previous => previous.filter(item => item.key !== entry.key))
    } catch (caught) {
      if (!mounted.current || isAborted(caught)) return
      // Only these terminal responses with a durable result version prove FAILED.
      const failed = caught instanceof ApiError && caught.currentVersion !== undefined && ['STORAGE_UNAVAILABLE', 'UPLOAD_CONFIRMATION_FAILED', 'UPLOAD_CANCELLED', 'UPLOAD_EXPIRED'].includes(caught.code ?? '')
      const conflict = caught instanceof ApiError && caught.code === 'VERSION_CONFLICT'
      if (conflict) setVersionConflict(true)
      if (failed && postRef.current) {
        const latest = await fetchOwnPost(postRef.current.postId).catch(() => null)
        if (!mounted.current) return
        if (latest) acknowledge(latest)
      }
      setUploads(previous => previous.map(item => item.key === entry.key ? { ...item, state: 'FAILED', resolution: failed ? 'FAILED' : conflict ? 'VERSION_CONFLICT' : 'UNKNOWN', error: errorMessage(caught) } : item))
    }
  })
  const addFiles = (files: File[]) => {
    const errors: string[] = []
    for (const file of files) {
      const extension = file.name.split('.').pop()?.toLowerCase()
      const limit = extension && ['jpg', 'jpeg', 'png', 'webp'].includes(extension) ? 5242880 : extension === 'pdf' ? 20971520 : extension === 'md' ? 1048576 : 0
      if (!limit) { errors.push(`${file.name}：只支持 JPG、PNG、WebP、PDF、MD。`); continue }
      if (file.size > limit) { errors.push(`${file.name}：超过单文件大小上限。`); continue }
      const entry: UploadEntry = { key: crypto.randomUUID(), file, state: 'UPLOADING' }; setUploads(previous => [...previous, entry]); void uploadFile(entry).catch((caught: unknown) => { if (!isAborted(caught)) setError(errorMessage(caught)) })
    }
    if (errors.length) setError(errors.join(' '))
  }
  const retryUpload = (entry: UploadEntry) => {
    if (busyRef.current || (entry.resolution === 'VERSION_CONFLICT' && versionConflict)) return
    const failed = entry.resolution === 'FAILED'
    // Unknown/active requests repeat the complete original payload. A confirmed
    // version rejection had no reservation, so the user may retry after a refresh.
    const attempt = entry.resolution === 'VERSION_CONFLICT' && entry.attempt && postRef.current ? { ...entry.attempt, expectedVersion: postRef.current.version } : entry.attempt
    const retry: UploadEntry = { ...entry, key: failed ? crypto.randomUUID() : entry.key, attempt: failed ? undefined : attempt, receipt: failed ? undefined : entry.receipt, state: 'UPLOADING', resolution: undefined, error: undefined }
    setUploads(previous => previous.map(item => item.key === entry.key ? retry : item)); void uploadFile(retry).catch((caught: unknown) => { if (!isAborted(caught)) setError(errorMessage(caught)) })
  }
  const remove = (attachment: AttachmentInfo) => {
    if (busyRef.current) return
    const removeImage = (body: string) => body.replace(new RegExp(`!\\[[^\\]]*\\]\\(attachment:${attachment.id}\\)`, 'g'), '')
    edit({ attachmentIds: inputRef.current.attachmentIds.filter(id => id !== attachment.id), bodyMarkdown: removeImage(inputRef.current.bodyMarkdown) })
    // Removing a reference is an explicit versioned write; never save newer text incidentally.
    const server = JSON.parse(savedRef.current) as DraftInput
    const snapshot = { ...server, attachmentIds: server.attachmentIds.filter(id => id !== attachment.id), bodyMarkdown: removeImage(server.bodyMarkdown) }
    void enqueue(() => saveSnapshot(snapshot)).catch((caught: unknown) => { if (mounted.current && !isAborted(caught)) { if (caught instanceof ApiError && caught.status === 409) setVersionConflict(true); setError(errorMessage(caught)) } })
  }
  const insert = (text: string) => {
    const element = textarea.current; const start = element?.selectionStart ?? input.bodyMarkdown.length; const end = element?.selectionEnd ?? start
    edit({ bodyMarkdown: `${input.bodyMarkdown.slice(0, start)}${text}${input.bodyMarkdown.slice(end)}` })
    requestAnimationFrame(() => { element?.focus(); element?.setSelectionRange(start + text.length, start + text.length) })
  }
  const selected = attachments.filter(attachment => input.attachmentIds.includes(attachment.id)); const total = selected.reduce((sum, attachment) => sum + attachment.size, 0)
  const validation = versionConflict ? '稿件版本已变化，请读取当前版本，检查后明确重试。' : !input.bodyMarkdown.trim() ? '请先填写正文。' : input.type !== 'MOMENT' && !input.title.trim() ? '请先填写标题。' : input.attachmentIds.some(id => !attachments.some(attachment => attachment.id === id && attachment.state === 'READY')) || uploads.length ? '仍有附件未就绪，请重试或移除后再发布。' : input.attachmentIds.length > 10 || total > 52428800 ? '每篇最多 10 个附件，合计不超过 50 MiB。' : null
  const publish = async () => {
    if (!consent || locked.current || busyRef.current || validation || post?.status === 'HIDDEN') return
    locked.current = true; setError(null)
    const snapshot = structuredClone(inputRef.current)
    try {
      await enqueue(async () => {
        const current = await ensurePost()
        const payload = publishAttempt.current?.payload ?? { ...snapshot, version: current.version, requestId: crypto.randomUUID(), visibility: 'MEMBERS' as const }
        const attempt: { payload: PublishInput; result?: PublishResult } = { payload }
        publishAttempt.current = attempt
        const result = await publishPost(current.postId, payload)
        attempt.result = result
        if (!mounted.current) return
        // Replayed receipts describe the original command, never a current write token or lifecycle.
        const authoritative = await fetchOwnPost(current.postId)
        if (!mounted.current) return
        acknowledge(authoritative)
        if (authoritative.version !== result.version) { setError('本次发布命令已成功，稿件随后发生了变化。已读取当前版本，保留本地输入，请检查后继续。'); return }
        // A successful receipt is for this snapshot. Later input stays in the editor.
        if (serial(inputRef.current) === serial(snapshot)) navigate(`/community/posts/${current.postId}`)
        else setError('已发布确认的版本。你在请求期间新增的修改仍留在私有编辑器中。')
      })
    } catch (caught) { if (mounted.current && !isAborted(caught)) { if (caught instanceof ApiError && caught.status === 409) setVersionConflict(true); setError(errorMessage(caught)) } } finally { locked.current = false }
  }
  const showPreview = () => { setConsent(false); if (postRef.current) navigate(`/publishing/posts/${postRef.current.postId}/preview`); else setPreviewLocal(true) }
  const backToEditor = () => { setPreviewLocal(false); setConsent(false); if (postRef.current) navigate(`/publishing/posts/${postRef.current.postId}`) }
  const attachmentMaintenance = async (operation: 'recover' | 'cleanup') => {
    if (!postRef.current || busyRef.current) return
    try { await enqueue(async () => {
      const id = postRef.current!.postId; const result = await (operation === 'recover' ? recoverAttachments(id) : cleanupAttachments(id))
      if (!mounted.current) return
      setMaintenance({ operation, result }); publishAttempt.current = null; setConsent(false)
      try { const latest = await fetchOwnPost(id); if (mounted.current) acknowledge(latest) }
      catch (caught) { if (!isAborted(caught)) setError(`附件处理已提交，但列表刷新失败：${errorMessage(caught)}`) }
    }) }
    catch (caught) { if (!isAborted(caught)) setError(errorMessage(caught)) }
  }
  const settings = (confirm: boolean) => <div className="card side-card"><h3>{confirm ? '确认发布范围' : '发布设置'}</h3><p className="flex settings-scope"><CommunityIcon name="archive" />本站所有登录用户</p><p className="settings-description">不对匿名访客开放。只有本次确认的正文与附件会被分享。</p><div className="rule" /><h3>发布前，再检查一下</h3><ul className="check-list"><li><CommunityIcon name="check" />正文只包含愿意分享的内容</li><li><CommunityIcon name="check" />图片与文件没有敏感信息</li><li><CommunityIcon name="check" />所有附件已就绪，并符合额度</li></ul>{confirm && <label className="check-label check-confirm"><input type="checkbox" checked={consent} onChange={event => setConsent(event.target.checked)} /><span>我已检查正文、图片与附件，确认可供本站登录成员查看。</span></label>}<div className="publish-actions">{confirm ? <><Button className="full" disabled={!consent || !!busy || !!validation || post?.status === 'HIDDEN'} onClick={() => void publish()}>{busy ? '正在发布…' : post?.status === 'PUBLISHED' ? '确认发布更新' : '确认发布到广场'}</Button><Button className="secondary full" onClick={backToEditor}>返回修改</Button></> : <><Button className="full" onClick={showPreview}><CommunityIcon name="eye" />预览并发布</Button><Button className="secondary full" disabled={!!busy} onClick={() => void save()}>{busy ? '正在处理…' : '保存草稿'}</Button></>}</div><div className="hint-box">{post?.status === 'PUBLISHED' ? '保存只更新你的私有草稿。读者仍看到上次发布的版本，直到你明确发布更新。' : '草稿不会出现在广场。发布之后仍可编辑更新或撤回；他人已下载的副本无法收回。'}</div>{confirm && validation && <Notice kind="warning">{validation}</Notice>}{post?.status === 'HIDDEN' && <Notice kind="error">内容已下架，不能再次发布。{post.moderation?.reason}</Notice>}</div>
  if (notFound) return <Unavailable />
  if (postId && !samePostId(post?.postId, postId)) return <LoadState loading={!error} error={error} retry={() => { setError(null); setNotFound(false); setLoadAttempt(value => value + 1) }} />
  if (loading) return <LoadState loading error={null} retry={() => undefined} />
  return <><a className="back-link" href={preview ? post ? `#/publishing/posts/${post.postId}` : undefined : '#/publishing'} onClick={preview ? event => { event.preventDefault(); backToEditor() } : undefined}><CommunityIcon name="arrow-left" />{preview ? '返回修改' : '返回我的发布'}</a>{error && <div className="editor-error"><Notice kind="error">{error}</Notice>{post && <Button className="ghost sm" disabled={!!busy} onClick={() => void syncVersion()}>读取当前版本，保留输入</Button>}</div>}{preview ? <><div className="preview-banner"><div><strong><CommunityIcon name="eye" />这是读者将看到的内容</strong><p>正文与附件一起发布。原始记录、项目关联和未发布修改不会开放。</p></div><span className="badge purple">预览 · 尚未发布</span></div><div className="columns reading-layout"><section><PublicationDocument value={input} attachments={selected} author={author} context={{ postId: post?.postId ?? '', owner: true }} preview /><div className="preview-confirm-mobile">{settings(true)}</div></section><aside className="aside-sticky preview-aside">{settings(true)}</aside></div></> : <><Heading eyebrow="PUBLISHING" title={post?.status === 'PUBLISHED' ? '继续打磨这份分享' : '写下你的分享'} description="保存的是私有草稿，发布才会让站内成员看见。" extra={<span className="draft-pill">{post?.status === 'PUBLISHED' ? '修改稿 · 尚未公开' : '草稿 · 仅自己可见'}</span>} />{input.type === 'DAILY' && <Stepper active={1} />}{post?.status === 'PUBLISHED' && <div className="edit-change-banner"><Notice>当前读者看到发布版本 v{post.currentPublished?.revisionNo}。保存草稿不会修改已发布正文或附件。</Notice></div>}<div className="columns"><section className="card editor-card"><div className="type-tabs tabs">{(['DAILY', 'MOMENT', 'BLOG'] as const).map(type => <button key={type} type="button" className={`tab ${input.type === type ? 'active' : ''}`} aria-pressed={input.type === type} disabled={!!busy} onClick={() => switchType(type)}><CommunityIcon name={typeIcons[type]} />{typeLabels[type]}</button>)}</div>{input.type === 'DAILY' && <div className="daily-editor-notice"><Notice>{input.businessDate} 的进展 · 来自已选择的私有素材，可自由删改。</Notice></div>}<label className="field-label" htmlFor="post-title">标题{input.type === 'MOMENT' ? '（可选）' : ''}</label><input id="post-title" className="editor-title" maxLength={200} value={input.title} placeholder={input.type === 'MOMENT' ? '给这条动态起个标题，也可以留空' : '为这份分享写一个标题'} autoComplete="off" onChange={event => edit({ title: event.target.value })} />{input.type === 'BLOG' && <><label className="field-label" htmlFor="post-summary">摘要（可选）</label><input className="editor-summary" id="post-summary" maxLength={500} value={input.summary} placeholder="用一两句话告诉读者，这篇文章讲什么。" onChange={event => edit({ summary: event.target.value })} /></>}<div className="toolbar" role="toolbar" aria-label="正文编辑工具"><button type="button" className="icon-btn" aria-label="插入二级标题" onClick={() => insert('\n## 小标题\n')}>H₂</button><button type="button" className="icon-btn" aria-label="加粗" onClick={() => insert('**重点**')}><b>B</b></button><button type="button" className="icon-btn" aria-label="插入列表" onClick={() => insert('\n- 一条进展\n')}><CommunityIcon name="checklist" /></button><button type="button" className="icon-btn" aria-label="插入代码块" onClick={() => insert('\n```\n代码\n```\n')}><CommunityIcon name="code" /></button><button type="button" className="icon-btn" aria-label="添加附件" onClick={() => picker.current?.click()}><CommunityIcon name="paperclip" /></button><small>Markdown 编辑</small></div><label className="sr-only" htmlFor="post-body">正文</label><textarea ref={textarea} id="post-body" className="editor-body" maxLength={100000} value={input.bodyMarkdown} placeholder="从一个具体的收获开始……" onChange={event => edit({ bodyMarkdown: event.target.value })} /><div className="editor-status"><span>{dirty ? '有未保存修改' : post ? '已保存私有草稿' : '新草稿 · 尚未保存'}</span><span>{input.bodyMarkdown.length} 字符</span></div><section className="attachments"><div className="spread"><h3>图片与附件</h3><span className="badge">仅你可见</span></div><div className="attachment-list">{selected.map(attachment => <div className="file-row" key={attachment.id} data-attachment-id={attachment.id}><div className={`file-icon ${attachment.kind.toLowerCase()}`}>{attachment.kind === 'IMAGE' ? <CommunityIcon name="image" /> : attachment.kind}</div><div className="file-info"><span className="file-name">{attachment.fileName}</span><div className={`file-meta ${attachment.state === 'FAILED' ? 'error' : ''}`}>{bytesLabel(attachment.size)} · {attachment.state === 'READY' ? '可用' : attachment.state === 'UPLOADING' ? '正在上传…' : `未就绪 ${attachment.safeFailureCode ?? ''}`}</div></div><div className="file-actions">{attachment.kind === 'IMAGE' && attachment.state === 'READY' && <Button className="ghost sm" onClick={() => insert(`\n![${attachment.fileName.replace(/[[\]]/g, '')}](attachment:${attachment.id})\n`)}>插入</Button>}<button type="button" className="icon-btn" aria-label={`移除 ${attachment.fileName}`} disabled={!!busy} onClick={() => remove(attachment)}><CommunityIcon name="close" /></button></div></div>)}{uploads.map(entry => <div className="file-row" key={entry.key}><div className="file-icon"><CommunityIcon name="plus" /></div><div className="file-info"><span className="file-name">{entry.file.name}</span><div className={`file-meta ${entry.state === 'FAILED' ? 'error' : ''}`}>{bytesLabel(entry.file.size)} · {entry.state === 'UPLOADING' ? '正在上传…' : entry.state === 'PENDING' ? entry.error : `上传失败：${entry.error}`}</div></div><div className="file-actions">{entry.state !== 'UPLOADING' && <><Button className="ghost sm" disabled={!!busy || (entry.resolution === 'VERSION_CONFLICT' && versionConflict)} onClick={() => retryUpload(entry)}>{entry.state === 'PENDING' ? '查询上传结果' : '重试'}</Button><Button className="ghost sm" disabled={!!busy} aria-label={`移除 ${entry.file.name}`} onClick={() => setUploads(previous => previous.filter(item => item.key !== entry.key))}>移除</Button></>}</div></div>)}</div><input ref={picker} className="sr-only" type="file" multiple accept=".jpg,.jpeg,.png,.webp,.pdf,.md" aria-label="选择图片、PDF 或 MD 文件" onChange={event => { addFiles(Array.from(event.target.files ?? [])); event.target.value = '' }} /><button type="button" className="dropzone" onClick={() => picker.current?.click()} onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); addFiles(Array.from(event.dataTransfer.files)) }}><span className="flex"><CommunityIcon name="upload" />选择图片、PDF 或 MD 文件</span><small>JPG / PNG / WebP ≤ 5 MiB · PDF ≤ 20 MiB · MD ≤ 1 MiB</small></button><div className="quota"><div className="quota-line"><span>{input.attachmentIds.length} / 10 个附件</span><span>{bytesLabel(total)} / 50 MiB</span></div><div className="progress" role="progressbar" aria-label="附件总容量" aria-valuenow={total} aria-valuemin={0} aria-valuemax={52428800}><div style={{ width: `${Math.min(100, total / 52428800 * 100)}%` }} /></div></div><p className="upload-notes">文件由服务端验证并保存到私有存储。上传不会自动保存正在编辑的正文。移除仅更新私有附件引用；正文仍需手动保存。</p>{post && <div className="attachment-maintenance"><Button className="ghost sm" disabled={!!busy} onClick={() => void attachmentMaintenance('recover')}>恢复过期上传</Button><Button className="ghost sm" disabled={!!busy} onClick={() => void attachmentMaintenance('cleanup')}>清理无引用附件</Button></div>}{maintenance && <div className="maintenance-results" role="status">{maintenance.result.results.length ? maintenance.result.results.map(result => <p key={result.attachmentId}>{attachments.find(item => item.id === result.attachmentId)?.fileName ?? "附件"}：{result.state === "DELETED" ? "已清理" : result.state === "DELETE_FAILED" ? "清理失败，请重试" : maintenance.operation === "recover" && result.state === "FAILED" ? "过期上传已释放，可重新选择文件" : "附件处理未完成，请重试"}</p>) : <p>{maintenance.operation === "recover" ? "没有需要恢复的过期上传。" : "没有可清理的无引用附件。"}</p>}</div>}</section></section><aside className="aside-sticky editor-aside">{settings(false)}<div className="card side-card"><h3>附件跟随本次发布</h3><p>图片可插在正文中。<br />PDF 与 MD 仅提供下载。</p><p>移除或替换附件，不会提前修改已发布版本。</p></div></aside></div><div className="mobile-publish"><Button className="secondary" disabled={!!busy} onClick={() => void save()}>保存草稿</Button><Button onClick={showPreview}><CommunityIcon name="eye" />预览并发布</Button></div></>}{leave && <Dialog className="community-dialog" titleId="editor-leave-title" busy={!!busy} onClose={() => void answerLeave('continue')}><div className="dialog"><div className="eyebrow">WORKBENCH / WRITING</div><h2 id="editor-leave-title">{leave.type ? '新建另一种内容？' : '还有未保存的修改'}</h2><p>{leave.type ? '当前修改保存为私有草稿，再打开新的编辑器。现有文章的类型保持不变。' : '你可以先保存私有草稿再离开。保存不会更新读者看到的发布版本。'}</p>{error && <Notice kind="error">{error}</Notice>}<div className="dialog-actions"><Button className="secondary" data-dialog-autofocus disabled={!!busy} onClick={() => void answerLeave('continue')}>继续编辑</Button>{!leave.type && <Button className="ghost" disabled={!!busy} onClick={() => void answerLeave('discard')}>放弃修改</Button>}<Button disabled={!!busy} onClick={() => void answerLeave('save')}>{busy ? '正在保存…' : leave.type ? '保存并新建' : '保存并离开'}</Button></div></div></Dialog>}</>
}
