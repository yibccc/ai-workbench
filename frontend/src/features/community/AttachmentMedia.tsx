import { useEffect, useRef, useState } from 'react'
import type { AttachmentInfo } from '../../api/community'
import { readCommunityAttachment } from '../../api/community'
import { readOwnAttachment } from '../../api/publishing'
import { Dialog } from '../../components/Dialog'
import { Button, CommunityIcon } from './ui'
import { bytesLabel, errorMessage, isAborted } from './model'

export type AttachmentContext = { postId: string; owner: boolean }
const read = (context: AttachmentContext, id: string, signal: AbortSignal) => (context.owner ? readOwnAttachment : readCommunityAttachment)(context.postId, id, signal)

export function AttachmentImage({ attachment, context, alt, onOpen }: { attachment: AttachmentInfo; context: AttachmentContext; alt?: string; onOpen?: (attachment: AttachmentInfo) => void }) {
  const [result, setResult] = useState<{ key: string; url?: string; error?: string } | null>(null)
  const [attempt, setAttempt] = useState(0)
  const key = `${context.owner}:${context.postId}:${attachment.id}`
  useEffect(() => {
    const controller = new AbortController(); let url: string | null = null
    void read({ postId: context.postId, owner: context.owner }, attachment.id, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      url = URL.createObjectURL(blob); setResult({ key, url })
    }).catch((caught: unknown) => { if (!controller.signal.aborted && !isAborted(caught)) setResult({ key, error: errorMessage(caught) }) })
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url) }
  }, [context.postId, context.owner, attachment.id, key, attempt])
  const current = result?.key === key ? result : null
  if (current?.error) return <div className="image-error" role="alert">图片读取失败：{current.error} <Button className="ghost sm" onClick={() => setAttempt(value => value + 1)}>重试图片</Button></div>
  if (!current?.url) return <span className="image-loading" role="status">正在加载图片…</span>
  const picture = <img src={current.url} alt={alt ?? attachment.fileName} />
  return onOpen ? <button type="button" className="article-image" onClick={() => onOpen(attachment)} aria-label={`查看大图 ${attachment.fileName}`}>{picture}<span className="image-caption">{attachment.fileName} · 点击查看大图</span></button> : picture
}

export function ImageDialog({ attachment, context, onClose }: { attachment: AttachmentInfo; context: AttachmentContext; onClose: () => void }) { return <Dialog className="community-dialog" titleId="community-image-title" busy={false} onClose={onClose}><div className="dialog wide"><div className="close-row"><div className="eyebrow">WORKBENCH / IMAGE</div><Button className="ghost" data-dialog-autofocus onClick={onClose} aria-label="关闭大图"><CommunityIcon name="close" /></Button></div><h2 id="community-image-title">{attachment.fileName}</h2><div className="image-dialog-content"><AttachmentImage attachment={attachment} context={context} /></div></div></Dialog> }

export function AttachmentDownload({ attachment, context }: { attachment: AttachmentInfo; context: AttachmentContext }) {
  const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null)
  const controller = useRef<AbortController | null>(null)
  const pending = useRef(false)
  useEffect(() => { const active = new AbortController(); controller.current = active; return () => { active.abort(); if (controller.current === active) controller.current = null } }, [])
  const download = async () => {
    const active = controller.current
    if (pending.current || !active || active.signal.aborted) return
    pending.current = true; setBusy(true); setError(null)
    let url: string | null = null
    try {
      const blob = await read(context, attachment.id, active.signal)
      if (active.signal.aborted) return
      url = URL.createObjectURL(blob)
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = attachment.fileName; anchor.click()
    } catch (caught) { if (!active.signal.aborted && !isAborted(caught)) setError(errorMessage(caught)) }
    finally { pending.current = false; if (url) URL.revokeObjectURL(url); if (!active.signal.aborted) setBusy(false) }
  }
  return <div><div className="file-row"><div className={`file-icon ${attachment.kind.toLowerCase()}`}>{attachment.kind}</div><div className="file-info"><span className="file-name">{attachment.fileName}</span><div className="file-meta">{bytesLabel(attachment.size)} · 可用</div></div><div className="file-actions"><Button className="ghost sm" disabled={busy} onClick={() => void download()} aria-label={`下载 ${attachment.fileName}`}><CommunityIcon name="download" />{busy ? '正在下载…' : '下载'}</Button></div></div>{error && <p className="download-error" role="alert">下载失败：{error}。请重试，正文仍可阅读。</p>}</div>
}
