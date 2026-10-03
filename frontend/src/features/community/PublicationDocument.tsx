import { useState } from 'react'
import Markdown from 'react-markdown'
import type { AttachmentInfo, PostDetail, PublicAuthor } from '../../api/community'
import type { DraftInput } from '../../api/publishing'
import { AttachmentDownload, AttachmentImage, ImageDialog, type AttachmentContext } from './AttachmentMedia'
import { Author, CommunityIcon } from './ui'
import { typeLabels, dateLabel } from './model'

export function PublicationDocument({ value, attachments, author, context, published, preview = false, footer }: {
  value: DraftInput | PostDetail; attachments: AttachmentInfo[]; author: PublicAuthor; context: AttachmentContext
  published?: { publishedAt: string; revisionNo: number }; preview?: boolean; footer?: React.ReactNode
}) {
  const [openImage, setOpenImage] = useState<AttachmentInfo | null>(null)
  const ready = attachments.filter(attachment => attachment.state === 'READY')
  const permitted = new Map(ready.filter(attachment => attachment.kind === 'IMAGE').map(attachment => [attachment.id, attachment]))
  const inlineIds = new Set([...value.bodyMarkdown.matchAll(/!\[[^\]]*\]\(attachment:([0-9a-f-]+)\)/gi)].map(match => match[1]))
  const safeUrl = (url: string) => {
    if (/^https?:\/\//i.test(url)) return url
    const id = /^attachment:([0-9a-f-]{36})$/i.exec(url)?.[1]
    return id && permitted.has(id) ? url : ''
  }
  return <><article className="card article-card"><div className="spread"><span className="badge purple">{typeLabels[value.type]}</span><span className="badge"><CommunityIcon name="archive" />{preview ? '发布后站内可见' : '站内可见'}</span></div><h1>{value.title || '一点想法，留给今天'}</h1><Author author={author} subtitle={preview ? '作者预览 · 尚未发布' : published ? `${dateLabel(published.publishedAt)} · 发布版本 v${published.revisionNo}` : '站内成员'} />{value.summary && <p className="article-summary">{value.summary}</p>}{value.type === 'DAILY' && <div className="subtle daily-date"><CommunityIcon name="calendar" /> {value.businessDate} 的进展</div>}<div className="prose"><Markdown skipHtml urlTransform={safeUrl} components={{
      a: ({ href, children }) => href && /^https?:\/\//i.test(href) ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
      img: ({ src, alt }) => {
        const id = typeof src === 'string' ? /^attachment:([0-9a-f-]{36})$/i.exec(src)?.[1] : undefined
        const attachment = id ? permitted.get(id) : undefined
        return attachment ? <AttachmentImage attachment={attachment} alt={alt} context={context} onOpen={setOpenImage} /> : <span className="muted">[图片已移除、未就绪或不允许外链图片]</span>
      },
      p: ({ children }) => <div className="markdown-paragraph">{children}</div>,
    }}>{value.bodyMarkdown}</Markdown></div>{ready.filter(attachment => attachment.kind === 'IMAGE' && !inlineIds.has(attachment.id)).map(attachment => <AttachmentImage key={attachment.id} attachment={attachment} context={context} onOpen={setOpenImage} />)}{ready.some(attachment => attachment.kind !== 'IMAGE') && <section className="article-files"><h3>随文附件 <span className="muted">{ready.filter(attachment => attachment.kind !== 'IMAGE').length} 个</span></h3><p className="subtle">PDF 和 MD 仅提供下载，不做站内预览。</p><div className="attachment-list">{ready.filter(attachment => attachment.kind !== 'IMAGE').map(attachment => <AttachmentDownload key={attachment.id} attachment={attachment} context={context} />)}</div></section>}<div className="article-bottom"><span className="subtle">仅分享作者确认的内容，不开放原始工作记录。</span>{footer ?? <span className="badge">读者视角预览</span>}</div></article>{openImage && <ImageDialog attachment={openImage} context={context} onClose={() => setOpenImage(null)} />}</>
}
