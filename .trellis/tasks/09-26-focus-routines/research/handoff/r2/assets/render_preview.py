"""Render the r2 standalone UI reference, not the ai-workbench application.

Requires an already-prepared Python/Playwright/browser environment. Reads only the
adjacent HTML and writes to an explicitly chosen, empty directory. No dependency
installation, repository change, business request, clock validation or DB access.
"""
from __future__ import annotations

import argparse
import importlib.metadata
import json
import platform
from pathlib import Path
from playwright.sync_api import sync_playwright


def render(output: Path, browser_path: str | None) -> dict:
    source = Path(__file__).resolve().parent / 'focus-prototype.html'
    if not source.is_file():
        raise FileNotFoundError(source)
    if browser_path and not Path(browser_path).is_file():
        raise FileNotFoundError(browser_path)
    if output.exists() and any(output.iterdir()):
        raise FileExistsError(f'Use a new empty output directory: {output}')
    output.mkdir(parents=True, exist_ok=True)
    html = source.read_text(encoding='utf-8')
    images = [
        ('overview-desktop', 'overview', (1440, 1200), 'focus'),
        ('focus-desktop', 'focus', (1440, 1200), 'focus'),
        ('microbreak-desktop', 'break', (1440, 1200), 'focus'),
        ('repeat-desktop', 'repeat', (1440, 1200), 'focus'),
        ('focus-mobile', 'focus', (390, 1000), 'focus'),
        ('records-desktop', 'records', (1440, 1200), 'records'),
    ]
    image_checks, responsive_checks, flows, errors = [], [], [], []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=browser_path, headless=True)
        browser_version = browser.version
        for name, view, size, workspace in images:
            page = browser.new_page(viewport={'width': size[0], 'height': size[1]}, device_scale_factor=1)
            page.on('pageerror', lambda error: errors.append(str(error)))
            page.set_content(html, wait_until='load')
            page.evaluate('preview', view)
            page.evaluate('document.fonts.ready')
            assert page.locator(f'#nav-{workspace}').get_attribute('aria-current') == 'page'
            assert page.locator('.primary-nav a').count() == 5
            assert page.locator('#focus-dialog').count() == 0
            info = page.evaluate('''() => ({
                viewportWidth: innerWidth,
                documentWidth: document.documentElement.scrollWidth,
                documentHeight: document.documentElement.scrollHeight,
                openDialog: document.querySelector('dialog[open]')?.id || null,
                dialogOverflow: Array.from(document.querySelectorAll('dialog[open]')).some(d => d.scrollWidth > d.clientWidth),
                workspace: model.page,
                miniVisible: !document.querySelector('#mini').hidden
            })''')
            assert info['documentWidth'] == size[0], (name, info)
            assert not info['dialogOverflow'], (name, info)
            if view in ('focus', 'overview'):
                assert not info['openDialog'], info
                assert not info['miniVisible'], info
                selector = '#focus-pause' if view == 'focus' else '#start-button'
                box = page.locator(selector).bounding_box()
                assert box and box['y'] + box['height'] <= size[1], (name, box)
            if view == 'records':
                assert page.locator('#records-page .stats-strip, #records-page .timer-card').count() == 0
                assert page.locator('#mini').is_visible()
                assert page.locator('main #mini').count() == 0
            if view == 'break':
                assert info['openDialog'] == 'break-dialog'
            if view == 'repeat':
                assert info['openDialog'] == 'repeat-dialog'
                assert page.locator('#routines-tab').is_visible()
            page.screenshot(path=str(output / f'{name}.png'))
            image_checks.append({'image':f'{name}.png','viewport':list(size),**info})
            page.close()

        # Checks only this demo's in-memory navigation and UI transitions.
        page = browser.new_page(viewport={'width':1440,'height':1200})
        page.on('pageerror', lambda error: errors.append(str(error)))
        page.set_content(html, wait_until='load')
        page.locator('#nav-tasks').click()
        page.locator('#tasks-page [data-task="梳理待确认问题"]').click()
        assert page.locator('#focus-page').is_visible()
        assert page.locator('#focus-title-input').input_value() == '梳理待确认问题'
        assert page.evaluate('model.running') is False
        page.locator('#start-button').click()
        assert page.locator('#timer-state').inner_text() == '正在专注'
        assert page.locator('dialog[open]').count() == 0
        page.locator('#focus-pause').click()
        assert page.locator('#timer-state').inner_text() == '专注已暂停'
        page.locator('#focus-pause').click()
        assert page.locator('#timer-state').inner_text() == '正在专注'
        page.locator('#nav-records').click()
        page.locator('#record-draft').fill('合成草稿：切页不丢失')
        assert page.locator('#mini').is_visible()
        page.locator('#mini [data-act=back-focus]').click()
        assert page.locator('#mini').is_hidden()
        page.locator('#nav-records').click()
        assert page.locator('#record-draft').input_value() == '合成草稿：切页不丢失'
        page.locator('#nav-tasks').click()
        page.locator('#tasks-page [data-task="阅读接口文档"]').click()
        assert page.locator('#running-title').inner_text() == '梳理待确认问题'
        page.locator('#tab-routines').click()
        page.locator('#routines-tab [data-act=repeat]').first.click()
        assert page.locator('#repeat-dialog').is_visible()
        page.keyboard.press('Escape')
        assert page.locator('#repeat-dialog').is_hidden()
        assert page.evaluate('model.running') is True
        page.locator('#tab-timer').click()
        page.locator('#finish-button').click()
        assert page.locator('#finish-dialog').is_visible()
        assert page.evaluate('model.running') is False
        flows.append('任务带入 → 独立页确认开始 → 暂停/继续 → 切记录页/顶栏返回 → 草稿保留 → 不替换现有目标 → 规则抽屉开关 → 结束预览')
        page.evaluate("preview('break')")
        page.locator('#break-dialog [data-act=skip-break]').click()
        assert page.locator('#break-dialog').is_hidden()
        assert page.locator('#focus-page').is_visible()
        assert page.evaluate('model.phase') == 'focus'
        flows.append('全局微休息预览 → 跳过 → 返回同一独立页静态状态')
        for width in (1280,1024,768,390,320):
            page.set_viewport_size({'width':width,'height':1000})
            for view in ('focus','records'):
                page.evaluate('preview',view)
                info = page.evaluate('''() => ({width:innerWidth, documentWidth:document.documentElement.scrollWidth,
                    workspace:model.page, navigationCount:document.querySelectorAll('.primary-nav a').length})''')
                assert info['width'] == info['documentWidth'], info
                assert info['navigationCount'] == 5
                responsive_checks.append(info)
        page.close()
        browser.close()
    assert not errors, errors
    return {
        'artifactRevision':2,
        'os':platform.platform(),
        'python':platform.python_version(),
        'playwright':importlib.metadata.version('playwright'),
        'chromium':browser_version,
        'imageChecks':image_checks,
        'responsiveChecks':responsive_checks,
        'prototypeFlows':flows,
        'pageErrors':errors,
        'productionTestsExecuted':False,
        'scope':'Standalone HTML rendering/navigation only. No live timer, audio, persistence, database, isolation or production E2E validation.'
    }


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir',required=True,type=Path)
    parser.add_argument('--browser-path',default=None)
    args=parser.parse_args()
    result=render(args.output_dir,args.browser_path)
    (args.output_dir/'prototype-checks.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
