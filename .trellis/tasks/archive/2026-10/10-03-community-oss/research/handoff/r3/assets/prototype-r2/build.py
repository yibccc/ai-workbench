"""Build the dependency-free single-file UI prototype from the readable sources."""
from pathlib import Path
import hashlib
root = Path(__file__).resolve().parent
css = (root/'src/styles.css').read_text(encoding='utf-8')
js = '\n'.join((root/p).read_text(encoding='utf-8') for p in ['src/fixtures.js','src/app.js'])
page = '''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"><meta name="color-scheme" content="light"><meta name="referrer" content="no-referrer"><meta name="description" content="工作台站内广场交互原型；虚构数据，不连接后端或 OSS。"><title>站内广场 · 工作台交互原型 R2</title><style>''' + css + '''</style></head><body><div id="app"></div><div id="modal-root"></div><div id="toast" class="toast" role="status" aria-live="polite" hidden></div><noscript>此交互原型需要 JavaScript。请勿输入真实个人资料，所有操作仅保存在当前浏览器内存。</noscript><script>''' + js.replace('</script','<\\/script') + '''</script></body></html>'''
(root/'index.html').write_text(page,encoding='utf-8')
print('Built index.html:',len(page.encode('utf-8')),'bytes; SHA256',hashlib.sha256(page.encode()).hexdigest())
