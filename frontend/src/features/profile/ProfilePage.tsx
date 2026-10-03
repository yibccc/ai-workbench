import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/http'
import { deleteResume, downloadResume, getResume, importResume, saveResume, type CurrentResume, type ResumeMode, type ResumeSave } from '../../api/resume'
import { useDialog } from '../../components/dialogContext'
import type { RegisterNavigationGuard } from '../../components/layout/navigationGuard'
import { useToast } from '../../components/toastContext'
import { useBeforeUnload } from '../../hooks/useBeforeUnload'
import { countCodePoints, failureMessage, isAbort, readResumeFile, RESUME_LIMIT } from '../interview/text'
import './profile.css'

type SaveAttempt = { payload: ResumeSave; file: File | null; revision: number }
export function ProfilePage({ active, registerGuard, onChanged }: {
  active: boolean; registerGuard: RegisterNavigationGuard; onChanged: () => void
}) {
  const dialog = useDialog(); const { notify, toastRef } = useToast<HTMLDivElement>()
  const [current, setCurrent] = useState<CurrentResume | null>(null)
  const [text, setText] = useState(''); const [saved, setSaved] = useState('')
  const [mode, setMode] = useState<ResumeMode>('PASTE'); const [file, setFile] = useState<File | null>(null)
  const [loading, setLoading] = useState(true); const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null); const [conflict, setConflict] = useState(false)
  const [unknown, setUnknown] = useState(false); const [sourceChanged, setSourceChanged] = useState(false)
  const mounted = useRef(false); const initialized = useRef(false); const locked = useRef(false)
  const revision = useRef(0); const textRef = useRef(text); const fileRef = useRef(file)
  const currentRef = useRef(current); const savedRef = useRef(saved); const attempt = useRef<SaveAttempt | null>(null)
  const editVersion = useRef(0); const sourceChangedRef = useRef(false)
  const readOwner = useRef<AbortController | null>(null); const picker = useRef<HTMLInputElement>(null)
  const urls = useRef(new Set<string>()); const selectOwner = useRef(0)
  const dirty = text !== saved || !!file || sourceChanged
  const count = countCodePoints(text); const invalid = count > RESUME_LIMIT
  useBeforeUnload(dirty || unknown || busy)
  const adopt = useCallback((next: CurrentResume) => { if (next.version < editVersion.current || (currentRef.current && next.version < currentRef.current.version)) return false; currentRef.current = next; setCurrent(next); return true }, [])
  const refresh = useCallback(async () => {
    readOwner.current?.abort(); const controller = new AbortController(); readOwner.current = controller
    setLoading(true)
    try {
      const next = await getResume(controller.signal)
      if (!mounted.current || controller.signal.aborted) return
      if (!adopt(next)) return
      if (!initialized.current || (textRef.current === savedRef.current && !fileRef.current && !sourceChangedRef.current && !attempt.current)) {
        initialized.current = true
        const value = next.markdownText ?? ''; textRef.current = value; savedRef.current = value
        setText(value); setSaved(value); setMode(next.exists ? 'EDIT_CURRENT' : 'PASTE')
        editVersion.current = next.version
      } else if (editVersion.current !== next.version) {
        setConflict(true)
      }
      setError(null)
    } catch (caught) { if (mounted.current && !isAbort(caught)) setError(failureMessage(caught)) }
    finally { if (mounted.current && readOwner.current === controller) setLoading(false) }
  }, [adopt])
  useEffect(() => {
    mounted.current = true
    const activeUrls = urls.current
    return () => {
      mounted.current = false; readOwner.current?.abort()
      attempt.current = null; fileRef.current = null; activeUrls.forEach(url => URL.revokeObjectURL(url)); activeUrls.clear()
    }
  }, [])
  useEffect(() => { let cancelled = false; if (active) void Promise.resolve().then(() => { if (!cancelled) void refresh() }); return () => { cancelled = true; readOwner.current?.abort() } }, [active, refresh])
  useEffect(() => {
    if (!active) return
    registerGuard(async () => {
      if (locked.current || attempt.current) { setError('请先确认当前保存结果，再离开个人中心。'); return false }
      if (!dirty) return true
      return dialog({ title: '离开个人中心？', description: '简历尚未保存。本次账号内切换会保留编辑草稿；刷新或退出账号将丢失未保存内容。', confirmLabel: '保留草稿并离开' })
    })
    return () => registerGuard(null)
  }, [active, dirty, dialog, registerGuard])
  const changeText = (value: string) => { revision.current++; textRef.current = value; setText(value) }
  const reset = () => {
    const next = currentRef.current; const value = next?.markdownText ?? ''
    revision.current++; selectOwner.current++; textRef.current = value; savedRef.current = value; fileRef.current = null
    setText(value); setSaved(value); setFile(null); setMode(next?.exists ? 'EDIT_CURRENT' : 'PASTE'); setSourceChanged(false); setConflict(false); setError(null)
    editVersion.current = next?.version ?? 0; sourceChangedRef.current = false
    if (picker.current) picker.current.value = ''
  }
  const discard = async () => {
    if (locked.current || attempt.current) return
    if (!dirty || await dialog({ title: '放弃简历修改？', description: '未保存的编辑内容和新选择的原件将被移除，恢复最后确证的当前简历。', confirmLabel: '放弃修改', danger: true })) reset()
  }
  const selectFile = async (selected: File) => {
    const owner = ++selectOwner.current; const inputRevision = revision.current
    try {
      const value = await readResumeFile(selected)
      if (!mounted.current || owner !== selectOwner.current) return
      if ((dirty || inputRevision !== revision.current) && !await dialog({ title: '导入并替换编辑草稿？', description: '新文件内容将进入编辑器。只有随后点击保存才会改变当前简历；当前未保存草稿将被替换。', confirmLabel: '导入到编辑器' })) return
      if (!mounted.current || owner !== selectOwner.current || locked.current || attempt.current) return
      revision.current++; textRef.current = value; fileRef.current = selected
      setText(value); setFile(selected); setSourceChanged(true); sourceChangedRef.current = true; setError(null)
    } catch (caught) { if (mounted.current && owner === selectOwner.current) setError(failureMessage(caught)) }
  }
  const pasteMode = async () => {
    if (locked.current || attempt.current) return
    if (!await dialog({ title: '使用粘贴文本替换？', description: '保存后将使用编辑器中的粘贴文本，并移除当前原件关联。历史面试快照保持不变。', confirmLabel: '使用粘贴替换' })) return
    revision.current++; fileRef.current = null; setFile(null); setMode('PASTE'); setSourceChanged(true); sourceChangedRef.current = true
  }
  const save = async () => {
    if (locked.current || !currentRef.current || (!attempt.current && invalid)) return
    locked.current = true; setBusy(true); setError(null)
    const pending = attempt.current ?? { payload: { mode, markdownText: textRef.current, expectedVersion: conflict ? currentRef.current.version : editVersion.current, requestId: crypto.randomUUID() }, file: fileRef.current, revision: revision.current }
    attempt.current = pending
    try {
      if (conflict && !unknown && !await dialog({ title: '按新版本重试保存？', description: '已读取另一个页面保存的当前版本。确认后以保留的本地正文替换这一版本；已有面试快照保持不变。', confirmLabel: '确认并保存' })) { attempt.current = null; return }
      const receipt = pending.file ? await importResume(pending.file, pending.payload) : await saveResume(pending.payload)
      if (!mounted.current) return
      if (receipt.state === 'UPLOADING') { setUnknown(true); setError('原件保存仍待确认，请使用同一请求确认保存结果。'); return }
      if (receipt.state !== 'SUCCEEDED') { attempt.current = null; setUnknown(false); setError(`保存未生效：${receipt.safeFailureCode ?? receipt.state}。原有简历和编辑内容已保留。`); return }
      attempt.current = null; setUnknown(false); setConflict(false)
      savedRef.current = pending.payload.markdownText; setSaved(pending.payload.markdownText)
      editVersion.current = receipt.resultVersion ?? pending.payload.expectedVersion + 1
      if (fileRef.current === pending.file) { fileRef.current = null; setFile(null); setMode('EDIT_CURRENT'); setSourceChanged(false); sourceChangedRef.current = false }
      onChanged(); notify('简历已保存', 'success')
      try {
        const next = await getResume()
        if (!mounted.current) return
        if (!adopt(next)) return
        // A receipt confirms its operation; this read is the current authority.
        if (revision.current === pending.revision) {
          const value = next.markdownText ?? ''; textRef.current = value; savedRef.current = value
          setText(value); setSaved(value); setMode(next.exists ? 'EDIT_CURRENT' : 'PASTE')
          editVersion.current = next.version
        }
      } catch (caught) { if (mounted.current && !isAbort(caught)) setError(`已保存，当前版本刷新失败：${failureMessage(caught)}。请重新读取。`) }
    } catch (caught) {
      if (!mounted.current || isAbort(caught)) return
      if (caught instanceof ApiError && caught.status < 500) {
        attempt.current = null; setUnknown(false)
        if (caught.status === 409) { setConflict(true); const next = await getResume().catch(() => null); if (next && mounted.current) adopt(next) }
        setError(caught.status === 409 ? '当前简历已被另一页面修改。本地编辑已保留，请查看生效版本并确认后重试。' : failureMessage(caught))
      } else { setUnknown(true); setError('保存响应未确认。编辑内容与原请求已保留，请点击“确认保存结果”，会用同一请求标识重放。') }
    } finally { locked.current = false; if (mounted.current) setBusy(false) }
  }
  const remove = async () => {
    const baseline = currentRef.current
    if (!baseline?.exists || locked.current || attempt.current) return
    const deletion = { expectedVersion: baseline.version, requestId: crypto.randomUUID() }
    await dialog({ title: '删除当前简历？', description: '删除当前生效正文与原件关联，仍可进行无简历通用面试。历史面试里的简历文本快照不会删除。', confirmLabel: '删除当前简历', danger: true, onConfirm: async () => {
      if (!mounted.current || locked.current) return
      locked.current = true; setBusy(true)
      try {
        await deleteResume(deletion)
        if (!mounted.current) return
        adopt({ exists: false, version: baseline.version + 1, markdownText: null, sourceKind: null, originalFile: null }); reset(); onChanged(); notify('当前简历已删除', 'success')
        void refresh()
      } finally { locked.current = false; if (mounted.current) setBusy(false) }
    } })
  }
  const download = async () => {
    if (!currentRef.current?.originalFile || locked.current) return
    const metadata = currentRef.current.originalFile
    try {
      const blob = await downloadResume()
      if (!mounted.current) return
      const url = URL.createObjectURL(blob); urls.current.add(url)
      const link = document.createElement('a'); link.href = url; link.download = metadata.fileName; link.click()
      URL.revokeObjectURL(url); urls.current.delete(url)
    } catch (caught) { if (mounted.current && !isAbort(caught)) setError(failureMessage(caught)) }
  }
  return <div className="page profile-workspace" ref={toastRef}>
    <header className="page-header page-head"><div><h1>个人中心</h1><p>每个账号只有一份当前简历；新建带简历面试只能使用这里已保存的版本。</p></div><a className="secondary profile-back" href="#interview">返回 AI 面试</a></header>
    <div className="workspace-scroll" role="region" aria-label="个人简历内容" tabIndex={0}>
      {error && <div className="note danger-note" role="alert">{error}</div>}
      {loading && <p role="status">正在读取当前简历…</p>}
      {!current && !loading && <button className="secondary" onClick={() => void refresh()}>重新读取简历</button>}
      {current && <div className="resume-layout"><section className="card resume-editor">
        <div className="resume-heading"><div><h2>当前简历</h2><div className="tiny muted">Markdown 正文最多 20,000 字符 · 生效版本 {current.version}</div></div><span className={`status ${current.exists ? 'done' : 'waiting'}`}>{current.exists ? '已生效' : '无当前简历'}</span></div>
        <div className="note resume-baseline">生效来源：{current.exists ? current.sourceKind === 'MD_FILE' ? current.originalFile?.fileName ?? '导入原件' : '粘贴文本' : '未设置'} · 已保存正文 {countCodePoints(current.markdownText ?? '')} 字符{current.exists && current.markdownText === '' ? '（空正文）' : ''}</div>
        <label>简历 Markdown<textarea autoFocus rows={14} value={text} onChange={event => changeText(event.target.value)} aria-invalid={invalid} /></label>
        <div className="resume-count"><span className={invalid ? 'input-error' : 'tiny muted'}>{count.toLocaleString('en-US')} / 20,000 字符</span><span className="tiny muted">{dirty ? '有未保存修改' : '与保存内容一致'} · {file ? '导入原件' : mode === 'EDIT_CURRENT' ? '编辑当前' : '粘贴替换'}</span></div>
        {invalid && <p className="input-error" role="alert">正文超过 20,000 字符。全文已保留，请修改后保存。</p>}
        <div className="form-actions"><button className="secondary" disabled={busy || unknown} onClick={() => void discard()}>放弃修改</button><button disabled={busy || (!unknown && invalid)} onClick={() => void save()}>{busy ? '正在保存…' : unknown ? '确认保存结果' : conflict ? '确认版本并重试保存' : '保存为当前简历'}</button></div>
        <div className="note">保存成功后才替换当前简历。已创建面试继续使用各自的历史文本快照，不会改用新版。</div>
      </section><aside className="grid"><section className="card file-card"><h2>导入 .md 文件</h2><p className="muted">仅支持 UTF-8 Markdown，原文件最多 1 MiB；导入后内容进入左侧编辑器，可修改后再保存。</p>
        {file && <div className="file"><strong>{file.name}</strong><span>{file.size.toLocaleString('en-US')} 字节 · 尚未保存的原件</span></div>}
        {current.originalFile && <div className="file"><strong>{current.originalFile.fileName}</strong><span>{current.originalFile.size.toLocaleString('en-US')} 字节 · 已保存原件</span><span>SHA-256：{current.originalFile.sha256}</span><button className="text-button" onClick={() => void download()}>下载当前原件</button></div>}
        <input ref={picker} type="file" accept=".md" className="sr-only" aria-label="选择新的 .md 文件" disabled={busy || unknown} onChange={event => { const selected = event.target.files?.[0]; event.target.value = ''; if (selected) void selectFile(selected) }} />
        <button className="secondary full-button" disabled={busy || unknown} onClick={() => picker.current?.click()}>选择新的 .md 文件</button>
        <button className="secondary full-button" disabled={busy || unknown} onClick={() => void pasteMode()}>使用粘贴替换</button>
      </section><section className="card file-card"><h2 className="input-error">删除当前简历</h2><p className="muted">删除后仍可进行无简历通用面试。历史面试里的简历文本快照不会被删除。</p><button className="danger full-button" disabled={!current.exists || busy || unknown} onClick={() => void remove()}>删除当前简历</button></section>
      <button className="secondary" disabled={busy} onClick={() => void refresh()}>重新读取生效版本</button></aside></div>}
    </div>
  </div>
}
