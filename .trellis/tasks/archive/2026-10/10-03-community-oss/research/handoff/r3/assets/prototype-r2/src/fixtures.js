/* All identities, writing, records and files below are synthetic prototype fixtures. */
'use strict';
const DEMO_DATE = '2026-10-03';
const MiB = 1024 * 1024;
const LIMITS = Object.freeze({ image: 5 * MiB, pdf: 20 * MiB, md: MiB, count: 10, total: 50 * MiB });
const TYPES = Object.freeze({ daily: '今日分享', note: '动态', blog: '博客' });
const AUTHORS = {
  a: { name: '林序', initial: '林', color: '', bio: '把每天的一点进展，留成彼此可见的收获。' },
  b: { name: '周禾', initial: '周', color: 'green', bio: '记录学习、设计与实践。把想清楚的事情写下来。' },
  c: { name: '陈予', initial: '陈', color: 'gold', bio: '练习观察，也练习把小事做完。' }
};
const RECORDS = [
  { id: 'r1', date: DEMO_DATE, type: '手工记录', title: '整理阅读笔记，补全三个容易混淆的概念', text: '用自己的话重写一遍，再留一个例子。', project: '个人学习', time: '09:20', task: null },
  { id: 'r2', date: DEMO_DATE, type: '任务完成', title: '完成一个交互小练习，写下需要改进的地方', text: '补上空状态与按钮反馈，让操作更连贯。', project: '练习计划', time: '11:35', task: 't1' },
  { id: 'r3', date: DEMO_DATE, type: '专注投入', title: '为交互小练习投入了 50 分钟', text: '只记录投入，不重复算作一次任务完成。', project: '练习计划', time: '11:40', minutes: 50, task: 't1' },
  { id: 'r4', date: DEMO_DATE, type: '手工记录', title: '试着把一段复杂说明写得更容易理解', text: '删掉术语，换成一个简单的具体例子。', project: '表达练习', time: '14:10', task: null },
  { id: 'r5', date: DEMO_DATE, type: '手工记录', title: '记录一次没成功的尝试和下次的改法', text: '先把步骤缩小，再验证最关键的假设。', project: '个人学习', time: '15:30', task: null },
  { id: 'r6', date: DEMO_DATE, type: '任务完成', title: '整理一份下次可以复用的复盘清单', text: '只留下有用的问题，不追求写得很长。', project: '习惯养成', time: '16:20', task: 't2' },
  { id: 'r7', date: '2026-10-02', type: '手工记录', title: '读完一章书，记录三个值得回看的问题', text: '把问题放在笔记最前面。', project: '个人学习', time: '10:10', task: null },
  { id: 'r8', date: '2026-10-02', type: '专注投入', title: '为阅读笔记投入了 25 分钟', text: '一次有起点和终点的安静练习。', project: '个人学习', time: '11:00', minutes: 25, task: null }
];
const SAMPLE_FILES = [
  { id: 'sample-image', name: '复盘思路.png', kind: 'image', size: 1677722, status: 'ready', demo: true },
  { id: 'sample-pdf', name: '复盘笔记.pdf', kind: 'pdf', size: 2202010, status: 'ready', demo: true },
  { id: 'sample-md', name: '每周复盘清单.md', kind: 'md', size: 8192, status: 'ready', demo: true }
];
const BLOG_BODY = '记录工作的时候，我常常只留下“做了什么”。过几天再翻看，却忘了为什么这样做，又有哪些经验可以复用。\n\n## 不只记录结果，也留下判断\n我开始给每条记录补充一个很小的问题：这次做对了什么？遇到的阻碍是什么？下次可以走哪一步？\n\n> 复盘不需要把一天重新写一遍，而是留下下一次能用上的经验。\n\n## 从三句话开始\n- 今天推进了什么，留下具体的结果。\n- 哪个判断最有帮助，记下原因。\n- 下一次从哪里继续，写清最小一步。\n\n记录可以很短。先让它对未来的自己有用，再选择适合分享的部分。\n\n![复盘思路](attachment:sample-image)';
const clone = value => JSON.parse(JSON.stringify(value));
const makeDraft = (type, extra = {}) => ({ type, title: '', summary: '', body: '', attachments: [], date: DEMO_DATE, ...extra });
function seedPosts() {
  const daily = makeDraft('daily', { title: '今天，把一个小目标认真做完了', summary: '给自己留下一份轻量的进展记录。不需要很长，能看见一步步向前就很好。', body: '## 今天的进展\n- 整理阅读笔记，补全三个容易混淆的概念。\n- 完成一个交互小练习，写下需要改进的地方。\n\n## 一点收获\n把任务拆小，再留一个明确的结束点，比一直等到有整块时间更有用。', selectedIds: ['r1', 'r2'] });
  const blog = makeDraft('blog', { title: '从记录到复盘：让每一天留下可复用的经验', summary: '与其写一份流水账，不如留下当时的判断、遇到的阻碍，以及下一次可以更好的地方。', body: BLOG_BODY, attachments: clone(SAMPLE_FILES) });
  const note = makeDraft('note', { body: '今天的小发现：开始前把“做到哪一步就结束”写下来，比给自己安排一个模糊的大任务更容易行动。\n\n给明天的自己留一个明确的起点。' });
  const draft = makeDraft('blog', { title: '把工作记录写成一篇有用的复盘', summary: '从一条记录开始，整理出可以再次使用的方法。', body: BLOG_BODY, attachments: clone(SAMPLE_FILES).map(f => ({ ...f, id: 'draft-' + f.id })) });
  draft.body = draft.body.replace('attachment:sample-image', 'attachment:draft-sample-image');
  const withdrawn = makeDraft('note', { title: '一份还想再整理的小结', body: '把事情做小一些，把经验留下来。' });
  return [
    { id: 'daily-1', author: 'a', status: 'published', draft: clone(daily), saved: clone(daily), published: clone(daily), version: 1, time: '17:40', order: 30, hasEdits: false },
    { id: 'blog-1', author: 'b', status: 'published', draft: clone(blog), saved: clone(blog), published: clone(blog), version: 1, time: '16:20', order: 20, hasEdits: false },
    { id: 'note-1', author: 'c', status: 'published', draft: clone(note), saved: clone(note), published: clone(note), version: 1, time: '14:05', order: 10, hasEdits: false },
    { id: 'draft-1', author: 'a', status: 'draft', draft: clone(draft), saved: clone(draft), published: null, version: 0, time: '16:50', order: 5, hasEdits: false },
    { id: 'withdrawn-1', author: 'a', status: 'withdrawn', draft: clone(withdrawn), saved: clone(withdrawn), published: clone(withdrawn), version: 1, time: '昨天', order: 1, hasEdits: false }
  ];
}
