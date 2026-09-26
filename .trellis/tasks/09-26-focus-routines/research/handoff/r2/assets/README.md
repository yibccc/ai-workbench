# 独立专注页 · UI参考代码与PNG预览

交接 ID：`WB-20260926-focus-routines` · revision：2 · 日期：2026-09-26。

## 1. 材料性质与来源

用户在r1后明确要求“专注新开一页吧，不和今日工作挤”。本包把计时、重复规则和专注汇总移到独立业务页，工作记录正文只保留原输入与记录列表。具体布局、图标、颜色及辅助层尺寸是UI建议，不是像素级批准。

`focus-prototype.html` 为自包含HTML/CSS/JavaScript建议UI代码；不是已接入React的补丁。目标仓库 `yibccc/ai-workbench`，固定基线 `master` @ `efa1962cc99057e5a4f7ec7247768c45107ed85b`。参考已读 `navigation.ts`、`AppShell.tsx`、`RecordsPage.tsx`、App账号级组合和前端规范；颜色继承r1已读的styles.css根变量。准确读取范围在本包HANDOFF来源表。

拟接入 `frontend/src/features/focus/FocusPage.tsx` 与账号级Workspace/顶栏；这些位置的新增标记以solution第8节为准。不得把此独立HTML的shell替换真实AppShell，也不重做其他四页。独立页面是已确认产品要求；原型页内“专注计时 / 重复任务”标签和顶栏紧凑入口的精确排版是建议。

## 2. 原型能力与明确限制

原型可演示五页导航、任务带入、开始/暂停/继续/结束的静态状态、记录草稿在当前页面内保留、紧凑顶栏返回、微休息提示和重复规则辅助抽屉。示例用户、项目、任务、日期与进展均为合成数据。

**不是真实计时器。** 没有业务API、数据库、浏览器持久化、真实提示音或持续倒计时。开始只显示本段初始数字；`专注中`预览使用固定的17:18净计时/00:15休息/07:42剩余样本。结束按钮不会追加记录或改变95分钟/4段的合成汇总。刷新后不能从原型证明状态持久化，跨日、恢复、互斥、所有权、报告生成和重复任务生成均未实现。

原型的默认落点是专注页，方便审阅；正式产品默认仍为工作记录。记录、任务、报告和项目只保留本次讨论需要的最小入口与示例，不是旧工作区的完整实现。生产支持浏览器、音频误差、时长范围、任务筛选和恢复阈值须按正式本地设计验证，不能从HTML表单或静态控件推导新合同。

HTML内置限制外部请求的CSP，没有外部依赖、字体下载、图片请求或持久化写入。`render_preview.py`只读取相邻HTML，在明确指定的空目录输出截图/检查日志，不安装依赖、不访问仓库、数据库或付费模型。不附带任何字体文件。

## 3. 交付图片

| 文件 | 实际像素 | 展示状态 |
|---|---|---|
| `overview-desktop.png` | 1440 × 1200 | 独立专注页准备开始；目标、时长、任务、今日汇总和微休息设置。 |
| `focus-desktop.png` | 1440 × 1200 | 独立专注页进行中；完整计时区域，不是覆盖工作记录的抽屉。 |
| `microbreak-desktop.png` | 1440 × 1200 | 专注页之上的全局15秒提示；跳过/关闭，不声称检测闭眼。 |
| `repeat-desktop.png` | 1440 × 1200 | 专注页内重复规则入口与编辑辅助抽屉。 |
| `focus-mobile.png` | 390 × 1000 | 手机独立页、五项导航、计时和暂停/结束操作；下方任务/设置继续滚动可见。 |
| `records-desktop.png` | 1440 × 1200 | 原工作记录正文无计时/汇总大卡；投入仍在列表；顶栏仅有紧凑会话入口。 |

同名图片的含义以r2为准：尤其 `overview-desktop.png` 从r1的“工作记录混排概览”改为“独立专注页准备开始”。六张均由本revision HTML重新渲染，未把r1图片作为新布局证据。

状态入口可用 `?view=overview`、`focus`、`break`、`repeat`、`records`、`recovery`；导航用 `#records`、`#tasks`、`#focus`、`#reports`、`#projects`。已有hash优先于示例query。通过经审查的HTML或授权静态服务可查看；程序渲染使用 `page.set_content` 加载同一HTML，再调用原型 `preview(view)`。

## 4. 本轮真实沙箱局部检查

验证状态：**沙箱局部已验证，仅为r2独立HTML的渲染、若干按钮与DOM状态。不是任何工程AC通过结果。**

环境：Linux-6.18.44-x86_64-with-glibc2.41；Python 3.13.5；Python Playwright 1.57.0；Chromium 144.0.7559.96；deviceScaleFactor=1。中文使用沙箱已有字体回退；不分发字体，本地字体差异可能影响换行。

实际执行命令（这是本次沙箱位置，不是用户本地仓库路径）：

```text
python /mnt/data/WB-20260926-focus-routines-r2/assets/render_preview.py --output-dir /mnt/data/_focus_r2_work/validated --browser-path /usr/bin/chromium
```

结果：退出码0。生成6张PNG及 `prototype-checks.json`；同一脚本/同一HTML产物已放入本包。

自动观察范围：六种截图状态无JavaScript `pageerror`；文档宽度等于视口宽度，打开的辅助层没有横向溢出；专注进行中/准备开始均无 `focus-dialog` 计时抽屉；截图中的主要开始或暂停/结束按钮在指定截图视口内。工作记录正文没有统计大卡或计时面板，紧凑会话入口位于main外。另检查1280、1024、768、390、320像素宽的专注/记录页面宽度，无文档级横向溢出。

局部流程：待办带入专注准备页但不自动计时；明确开始显示静态状态；暂停/继续切标题；进入记录页与顶栏返回；记录草稿在同一DOM生命周期内保留；有会话时选择另一任务不替换目标；重复规则层打开及Escape关闭不结束会话；结束预览；微休息跳过返回专注静态状态。

`prototype-checks.json`为上述检查日志，不是Trellis task/context文件。图像和局部流程不验证净计时算法、后台响铃、实际可听音量、持久化恢复、并发互斥、账号隔离、真实API/数据库、应用默认路由或真实分页。

**未执行**目标仓库构建/lint/E2E、PostgreSQL/Redis测试、真实音频、真实手机睡眠、多标签互斥、模型调用、发布或回退。本轮的测试对象仅是这份独立原型；本地工作区执行状态未知。

## 5. 经授权后的复现

本地先只读审查材料。使用已有的Python/Playwright/浏览器环境；脚本不会自动安装依赖。指定包外的新空目录，避免改变冻结revision；输出目录非空会拒绝覆盖。

```text
python assets/render_preview.py --output-dir <新的空预览目录> [--browser-path <已有浏览器可执行文件>]
```

交付四份主文档、原型代码及PNG共同定义参考材料。requirements是AC唯一定义，acceptance是未来工程验证计划，solution含候选接入位置和需核实项；本附件不能替代本地最终规划批准。
