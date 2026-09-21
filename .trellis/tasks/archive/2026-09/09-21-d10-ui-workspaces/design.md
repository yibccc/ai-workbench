# 四工作区接入设计

- AppShell承担导航/页头；App保留项目索引与跨区刷新协调；RecordsPage/TasksPanel/ReportsPage/ProjectsPanel承载独立流程。
- RetainedView首次访问才挂载，切走hidden保留状态；不使用任务组件key刷新。切换回来恢复状态，后台完成可经刷新revision同步。
- RecordForm与TaskEditorDialog复用原生dialog基础、共享确认弹窗；桌面侧抽屉/移动全屏，焦点、Esc及未保存确认一致。
- 使用原生单行ProjectPicker，保留当前已加载全项目集合和归档关联编辑语义；必要API扩展应先以真实>50项目测试证明缺口。
- 8个API文件和realtime.ts与当前源码文本一致，优先保持后端和请求合同；前端分页hook与报告编辑存在最新修复，导入时逐项审查。
- 压缩包受控解压到忽略的.local-runtime参考目录，核对路径避免越界；正式src通过可审阅补丁接入，禁止引入preview/mock-api和演示数据横幅。
- 已有后端三层/PageHelper/WS/报告来源快照/日报软删除继续保留。Docker镜像构建与Vite引用路径随前端重构验证。
