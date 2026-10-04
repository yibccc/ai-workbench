export const countCodePoints = (text: string) => Array.from(text).length
export const RESUME_LIMIT = 20_000
export const JD_LIMIT = 10_000
export const ANSWER_LIMIT = 5_000
export const ORIGINAL_BYTES_LIMIT = 1_048_576
export const isAbort = (error: unknown) => error instanceof DOMException && error.name === 'AbortError'
export const failureMessage = (error: unknown) => error instanceof Error ? error.message : '请求失败，请重试'

/** The immutable File remains separate from the final edited Markdown. */
export async function readResumeFile(file: File): Promise<string> {
  if (!/\.md$/i.test(file.name)) throw new Error('只支持 .md 文件')
  if (file.size > ORIGINAL_BYTES_LIMIT) throw new Error('原文件超过 1 MiB，请选择较小的 .md 文件')
  let text: string
  try { text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()) }
  catch { throw new Error('文件必须是有效 UTF-8 Markdown') }
  if (countCodePoints(text) > RESUME_LIMIT) throw new Error('原文件正文超过 20,000 字符，当前编辑内容已保留')
  return text
}
