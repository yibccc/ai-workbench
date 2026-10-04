import Markdown from 'react-markdown'

/** Private interview content shares the pinned renderer's text/URL policy. */
export function SafeMarkdown({ text }: { text: string }) {
  return <div className="safe-markdown"><Markdown skipHtml urlTransform={url => /^https?:\/\//i.test(url) ? url : ''} components={{
    a: ({ href, children }) => href && /^https?:\/\//i.test(href) ? <a href={href} target="_blank" rel="noopener noreferrer">{children}</a> : <span>{children}</span>,
    img: ({ alt }) => <span>[外链图片不加载{alt ? `：${alt}` : ''}]</span>,
  }}>{text}</Markdown></div>
}
