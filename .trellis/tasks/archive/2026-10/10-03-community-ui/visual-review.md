# 主会话正式R2视觉审查

审查日期：2026-10-03 Asia/Shanghai。主会话实际通过view_image打开原型与正式PNG逐页对照，不只接受file-count或代理结论。原型输入保持原字节；正式证据在 `validation/screenshots/` 同名PNG/JSON。

桌面同1440×1040viewport，原图full-page高度随内容而变；正式应用使用既有100dvh内滚动，截图明确inner scrollTop，不重绘/裁拼成“像素一致”。移动同390×844。自然字体fallback、真实正文长度/日期/账号以及真实认证控件差异允许；没有复制demo用户、状态菜单/工具、演示专注计时或虚构附件。

| R2参考 | 实际正式图/补充 | 主会话观察 |
|---|---|---|
| 01 community desktop | 01-community-desktop；populated-feed-desktop及bottom | 初图为空不能证明有内容卡片，另用真3类型+private draft补。218 sidebar/68 topbar、主feed/270aside、composer/tabs/三类card/checklist/thumbnail/footer分组一致 |
| 02 source selection desktop | 02-source-selection-desktop | private material/date/stepper/主体+所选aside；真正两页所选字段替fixture，默认未选与日期守卫验证，布局分组保持 |
| 03 editor desktop | 03-editor-desktop；saved-editor-desktop-top | 新建本地和已存UUID两状态明确区分；backlink、title/summary/toolbar/body/status、file section及侧settings沿原模板 |
| 04 publish preview desktop | 04-preview-desktop | reader preview+确认范围/checklist/按钮，保存与发布独立；真实latest summary与body，不把preview冒称已发布reader |
| 05 article desktop | 05-detail-desktop | 真当前revision URL/HTTP确认后截；reader主卡、作者/title/body、右栏/TOC和footer保持；safe Markdown差异是业务安全规则 |
| 06 my posts desktop | 06-my-publications-desktop | 状态tabs/私有draft与当前publication区别、edit/view/withdraw及profile入口；实际条数替fixture数量 |
| 07 withdraw confirm desktop | 07-withdraw-dialog-desktop | 沿共享native Dialog、R2 theme；居中取消/破坏操作、focus/inert规则保持 |
| 08 author desktop | 08-author-desktop | 独立public nickname/bio hero与visible feed，未展示登录用户名/私有统计 |
| 09 login gate desktop | 09-login-desktop | 曾用1280图，已在1440×1040真实匿名target重捕；username初focus/password eye/真login回target；未用演示身份按钮 |
| 10 upload error desktop | 10-upload-error-desktop | 实际malformed PNG返回与保留正文，failed row/retry/remove/dropzone/quota位置；JSON记scroll241 |
| 11 community mobile | 11-community-mobile；populated-feed-mobile及bottom | 初empty另保留；真有内容card与checklist/博客缩略图/页尾可达，60topbar/四入口bottom和主宽一致，无文档横向溢出 |
| 12 sources mobile | 12-source-selection-mobile | date/card/private notice/page footer、跨页selected内容及field checkbox可达；数据长度及真实字段与样例不同 |
| 13 editor mobile | 13-editor-mobile（最后补图） | 真已保存UUID顶部scroll0，与参考backlink/heading/type/title/summary/toolbar/editor相同顺序；底save/preview始终高于bottom nav |
| 14 attachments mobile | 14-attachments-mobile（最后补图） | 真PNG/PDF/MD三READY、三行/dropzone/quota/说明与recover/cleanup，scroll733；非只两附件的早期图，保留历史失败证据 |
| 15 article mobile | 15-detail-mobile | type/visibility、title/author/summary/body主卡顺序，当前projection/scroll0及长body内滚动 |
| 16 downloads mobile | 16-downloads-mobile（最后补图） | 真当前image+PDF/MD两download行、copy/footer，scroll503；中文文件名与两原SHA已实际下载验证，没有PDF/MD preview/import |
| 17 my posts mobile | 17-my-publications-mobile | 标题/创建与profile、status-tabs/cards/操作/页尾分组可达；真实一条状态不造三条demo记录 |

主会话还实际打开源码已有但无单独原PNG的extra-compose-desktop/mobile、extra-preview-mobile、extra-image-lightbox-mobile、extra-empty-sources/empty-feed与明确fixture-http403/503截图，确认template grouping/移动确认操作/Modal/空与retry布局。其余loading/unavailable/dirty/type/copy等状态由原源码映射、20态FIXTURE_ONLY测量和真实12业务场景共同覆盖；夹具只证明客户端状态呈现，不冒称后端故障或对象权限。

最后补图专用具名用例 `real R2 populated feed...` 禁重试session52767 exit0，1PASS26.3s。真实PG/Redis/RustFS：private稿publicGET404；DAILY/MOMENT/BLOG公开；BLOG缩略图经过鉴权Blob，三个draft files READY；两download原字节/SHA与中文名一致。唯一第一次driver失败是390宽断言正确被隐藏的desktop aside，改为回1440再检查，产品/布局未改。原始诊断保留ignored visual-run-1，成功在visual-run-2-pass；不抹掉旧失败、不重绘原图。

结论：AC-22工程视觉审核PASS，覆盖17参考与源状态及真对应内容；实际用户人工视觉/声音/持续使用仍未进行。范围内未发现需要用户另行批准的主要布局改变。
