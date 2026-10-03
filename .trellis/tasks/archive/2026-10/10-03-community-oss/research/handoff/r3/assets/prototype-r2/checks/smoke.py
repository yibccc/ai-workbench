"""Local prototype-only browser checks and screenshot capture. No project/backend tests."""
from pathlib import Path
import json, os, time
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1]
HTML=(ROOT/'index.html').read_text(encoding='utf-8')
OUT=ROOT/'previews'
OUT.mkdir(exist_ok=True)
results=[]
errors=[]
remote=[]

def record(name, fn):
    try:
        fn();results.append({'name':name,'status':'PASS'})
    except Exception as exc:
        results.append({'name':name,'status':'FAIL','detail':str(exc)})

def assert_true(v,message='Assertion failed'):
    assert v,message

with sync_playwright() as pw:
    browser=pw.chromium.launch(executable_path=os.environ.get('CHROMIUM_PATH','/usr/bin/chromium'),headless=True,args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':1440,'height':1040},device_scale_factor=1,locale='zh-CN',timezone_id='Asia/Shanghai')
    def fresh(route='/community'):
        global page
        size=page.viewport_size
        page.close()
        page=browser.new_page(viewport=size,device_scale_factor=1,locale='zh-CN',timezone_id='Asia/Shanghai')
        page.on('pageerror',lambda e: errors.append(str(e)))
        page.on('dialog',lambda d:d.accept())
        page.on('request',lambda r: remote.append(r.url) if r.url.startswith(('http://','https://')) else None)
        # The runtime's managed Chromium blocks file/localhost navigation. Render generated
        # HTML directly in an isolated page; no remote URL or real app is opened.
        page.set_content(HTML,wait_until='load')
        page.evaluate('(route)=>{location.hash=route;}',route)
        page.wait_for_timeout(120)
    def nav(route):
        page.evaluate('(r)=>prototypeDemo.navigation(r,true)',route);page.wait_for_timeout(80)
    def shot(name,full=True):
        page.wait_for_timeout(120);page.evaluate("document.getElementById('toast').hidden=true");page.screenshot(path=str(OUT/name),full_page=full)
    fresh()
    record('广场初始展示三类已发布内容，未显示草稿',lambda: assert_true(page.locator('[data-post-id]').filter(has=page.locator('h2')).count()>=2 and page.locator('article.post-card').count()==3))
    shot('01-community-desktop.png')
    record('博客分类只展示博客',lambda: (page.get_by_role('button',name='博客',exact=True).click(),assert_true(page.locator('article.post-card').count()==1)))
    page.get_by_role('button',name='全部',exact=True).click()
    page.get_by_role('button',name='分享今天',exact=True).click()
    record('今日素材默认不勾选',lambda: assert_true(page.locator('.source-row input:checked').count()==0 and page.locator('[data-action="generate-daily"]').is_disabled()))
    page.locator('[data-source-id="r1"]').check()
    page.locator('[data-source-id="r2"]').check()
    page.locator('[data-source-id="r3"]').check()
    shot('02-source-selection-desktop.png')
    page.get_by_role('button',name='下一页',exact=True).click()
    page.locator('[data-source-id="r6"]').check()
    page.get_by_role('button',name='上一页',exact=True).click()
    record('跨页选择素材保持选中',lambda: assert_true(page.locator('[data-source-id="r1"]').is_checked() and page.evaluate('prototypeDemo.state.selected.size')==4))
    page.locator('[data-action="generate-daily"]').click()
    page.wait_for_timeout(100)
    record('草稿仅带入选中素材，不带私有项目名或未选记录',lambda: assert_true('个人学习' not in page.locator('#post-body').input_value() and '整理一份下次' in page.locator('#post-body').input_value() and '复杂说明' not in page.locator('#post-body').input_value()))
    # New post publishing and read-side visibility.
    new_id=page.evaluate('prototypeDemo.state.route.split("/")[2]')
    page.locator('[data-action="preview"]:visible').first.click()
    page.wait_for_timeout(80)
    record('发布预览默认需要明确确认',lambda: assert_true(page.locator('[data-action="publish"]:visible').is_disabled()))
    page.locator('[data-publish-consent]:visible').check()
    page.locator('[data-action="publish"]:visible').click()
    page.wait_for_timeout(430)
    record('明确发布后形成发布版本',lambda: assert_true(page.evaluate('(id)=>prototypeDemo.getPost(id).status',new_id)=='published'))
    page.select_option('#demo-identity','b')
    record('切换读者后可读取新发布内容',lambda: assert_true(page.locator('.article-card').count()==1))
    nav('/editor/'+new_id)
    record('读者不可进入他人草稿编辑器',lambda: assert_true(page.locator('#post-body').count()==0 and '内容暂不可访问' in page.locator('main').inner_text()))
    page.select_option('#demo-identity','a')
    nav('/editor/'+new_id)
    old_title=page.evaluate('(id)=>prototypeDemo.getPost(id).published.title',new_id)
    page.fill('#post-title','这是尚未发布的修改稿')
    page.locator('[data-action="save"]:visible').first.click()
    nav('/post/'+new_id)
    record('保存修改草稿不修改读者发布版本',lambda: assert_true(page.locator('.article-card h1').inner_text()==old_title))
    nav('/mine')
    page.locator('[data-action="withdraw"][data-post-id="'+new_id+'"]').click()
    page.get_by_role('button',name='确认撤回',exact=True).click()
    page.select_option('#demo-identity','b')
    nav('/post/'+new_id)
    record('撤回后原详情地址不显示正文与附件',lambda: assert_true(page.locator('.article-card').count()==0 and page.locator('[data-action="download"]').count()==0))
    # Reset in-memory fixtures for independent screenshots.
    fresh('/editor/draft-1')
    shot('03-editor-desktop.png')
    page.locator('[data-action="preview"]:visible').click()
    page.wait_for_timeout(100)
    shot('04-publish-preview-desktop.png')
    record('PDF与MD只有下载按钮，没有预览入口或iframe',lambda: assert_true(page.locator('iframe,object,embed').count()==0 and page.locator('[data-action="download"]').count()==2))
    nav('/post/blog-1')
    shot('05-article-desktop.png')
    page.locator('[data-action="download"]').first.click()
    record('下载按钮明确反馈是模拟且没有文件传输',lambda: assert_true('未传输实际' in page.locator('#toast').inner_text()))
    page.locator('[data-action="zoom-image"]').first.click()
    record('图片大图对话框可打开、Escape关闭',lambda: (assert_true(page.get_by_role('dialog').count()==1),page.keyboard.press('Escape'),assert_true(page.get_by_role('dialog').count()==0)))
    nav('/mine');shot('06-my-posts-desktop.png')
    page.locator('[data-action="withdraw"][data-post-id="daily-1"]').click();shot('07-withdraw-confirm-desktop.png',False);page.keyboard.press('Escape')
    nav('/profile/b');shot('08-author-profile-desktop.png')
    nav('/post/blog-1');page.select_option('#demo-identity','anon');shot('09-login-gate-desktop.png')
    record('未登录页面不显示原文章题名或附件',lambda: assert_true('从记录到复盘' not in page.locator('main').inner_text() and page.locator('.file-row').count()==0))
    page.locator('[data-action="login"][data-value="b"]').click()
    record('模拟登录后返回原目标详情',lambda: assert_true(page.locator('.article-card').count()==1 and page.locator('.article-card h1').inner_text().startswith('从记录到复盘')))
    # Local input and quota checks. Only UI model, not backend/OSS/security validation.
    fresh('/editor/draft-1')
    page.fill('#post-title','还没保存的标题')
    page.locator('.nav-list [data-nav="/mine"]').click()
    record('页面导航提醒未保存修改',lambda: assert_true(page.get_by_role('dialog').count()==1 and '还有未保存' in page.get_by_role('dialog').inner_text()))
    page.get_by_role('button',name='继续编辑',exact=True).click()
    page.fill('#post-body','<script>window.__executed=true</script>\n\n[恶意链接](javascript:alert(1))')
    page.locator('[data-action="preview"]:visible').click()
    record('原型简化Markdown转义脚本文本和不安全链接',lambda: assert_true(page.evaluate('window.__executed') is None and page.locator('.prose a[href^="javascript:"]').count()==0))
    fresh('/editor/draft-1')
    page.set_input_files('#file-picker',{'name':'not-allowed.gif','mimeType':'image/gif','buffer':b'GIF89a'})
    page.wait_for_timeout(100)
    record('不支持GIF扩展名时显示本地错误',lambda: assert_true('只支持' in page.locator('.upload-alert').inner_text()))
    page.set_input_files('#file-picker',{'name':'fake.png','mimeType':'image/png','buffer':b'not a png'})
    page.wait_for_timeout(180)
    record('伪造PNG扩展名的基本文件头校验失败',lambda: assert_true('校验失败' in page.locator('.upload-alert').inner_text()))
    page.set_input_files('#file-picker',{'name':'large.md','mimeType':'text/markdown','buffer':b'a'*(1024*1024+1)})
    page.wait_for_timeout(100)
    record('MD上限多1字节拒绝',lambda: assert_true('超过MD 1 MiB' in page.locator('.upload-alert').inner_text()))
    page.set_input_files('#file-picker',{'name':'boundary.md','mimeType':'text/markdown','buffer':b'a'*(1024*1024)})
    page.wait_for_timeout(400)
    record('MD恰好1MiB在本地模型可用',lambda: assert_true(page.evaluate('prototypeDemo.getPost("draft-1").draft.attachments.find(f=>f.name==="boundary.md").status')=='ready'))
    record('单篇50MiB允许、超过1字节拒绝、11个拒绝',lambda: assert_true(page.evaluate('(()=>{const q=prototypeDemo.quotaError;return q([{size:50*1024*1024}])===""&&q([{size:50*1024*1024+1}])!==""&&q(Array.from({length:11},()=>({size:1})))!==""})()')))
    fresh('/editor/draft-1')
    page.locator('[data-action="demo-tools"]').click();page.locator('[data-action="demo-upload"]').click()
    shot('10-upload-error-desktop.png')
    record('附件未就绪阻止进入发布确认',lambda: (page.locator('[data-action="preview"]:visible').click(),assert_true('仍有附件未就绪' in page.locator('#toast').inner_text())))
    page.locator('[data-action="retry-file"]').click()
    record('模拟重试不重复增加附件条目',lambda: assert_true(page.locator('.attachment-list .file-row').count()==3 and page.evaluate('prototypeDemo.getPost("draft-1").draft.attachments[0].status')=='ready'))
    nav('/community');page.locator('[data-action="demo-tools"]').click();page.locator('[data-action="demo-error"]').click()
    record('加载错误与登录失效分开呈现',lambda: assert_true(page.locator('.login-card').count()==0 and '暂时没有加载' in page.locator('main').inner_text()))
    page.locator('[data-action="feed-retry"]').click()
    record('错误状态重试恢复列表',lambda: assert_true(page.locator('article.post-card').count()==3))
    # Geometry checks at common and narrow viewports; real viewport screenshots, not fixed-bar stitched views.
    for width,height in [(1440,1040),(1024,768),(768,1024),(390,844),(320,740)]:
        page.set_viewport_size({'width':width,'height':height})
        for route in ['/community','/sources','/editor/draft-1','/post/blog-1','/mine','/profile/b']:
            fresh(route)
            record(f'无水平溢出 {width}×{height} {route}',lambda: assert_true(page.evaluate('document.documentElement.scrollWidth <= innerWidth + 1')))
    page.set_viewport_size({'width':390,'height':844})
    fresh();shot('11-community-mobile.png',False)
    fresh('/sources');page.locator('[data-source-id="r1"]').check();page.locator('[data-source-id="r2"]').check();shot('12-source-selection-mobile.png',False)
    fresh('/editor/draft-1');shot('13-editor-mobile.png',False)
    page.locator('.attachments').scroll_into_view_if_needed();page.evaluate('window.scrollBy(0, -60)');shot('14-attachments-mobile.png',False)
    fresh('/post/blog-1');shot('15-article-mobile.png',False)
    page.locator('.article-files').scroll_into_view_if_needed();page.evaluate('window.scrollTo(0, document.body.scrollHeight)');shot('16-downloads-mobile.png',False)
    fresh('/mine');shot('17-my-posts-mobile.png',False)
    record('无浏览器JS异常',lambda: assert_true(not errors,str(errors)))
    record('未发起外部HTTP请求',lambda: assert_true(not remote,str(remote)))
    browser.close()
report={'scope':'仅独立静态交互原型；不是仓库、后端、会话安全或 OSS 验证','browser':'Chromium via Playwright page.set_content; inline HTML only','results':results,'page_errors':errors,'external_http_requests':remote}
(ROOT/'checks/results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'passed':sum(r['status']=='PASS' for r in results),'failed':[r for r in results if r['status']=='FAIL'],'screenshots':len(list(OUT.glob('*.png'))),'page_errors':errors},ensure_ascii=False,indent=2))
if any(r['status']=='FAIL' for r in results):raise SystemExit(1)
