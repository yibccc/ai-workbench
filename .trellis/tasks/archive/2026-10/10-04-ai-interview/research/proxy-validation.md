# 最终 Nginx → packaged JVM → RustFS 真实验收

2026-10-04，Windows11/Python标准库 + WSL Ubuntu Docker。父已批准的连续执行范围内，使用最终稳定JAR/生产dist和真实 `deploy/nginx.conf`，没有改backend/frontend/scripts/deploy源码，没有Mavenbuild或覆盖共享target，没有下载/安装依赖。

## 资源与来源指纹

- 独立project `wbinterviewproxy20261004`、private network `wbinterviewproxy20261004_default`。新backend在该网络仅alias `backend`，与source网络连接alias仅 `proxy-backend`，没有抢source已有backend alias。
- source数据服务仍 `wbinterview20261004`，PG25432/Redis36379/RustFS29000；验收前后全部 Running=true/Health=healthy。没有stop/recreate其容器、改卷、写test/e2e/native/public旧业务库。
- 创建前明确 `pg_database` 不存在，新DB `wbinterview_proxy_delivery`，schema `d9_interview_proxy_tests_20261004`；新DB非system表0后才挂JAR。真实Flyway V1→V20成功，最终installed_rank末版本20。
- Redis DB11创建应用前dbsize0，独立Session namespace `workbench:proxy:wbinterviewproxy20261004`；随机bootstrap ADMIN，后续A/B均真实ADMIN邀请创建的合成USER。
- backend仅非root `10001:10001`，`-Xms64m -Xmx384m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC`，无published port。唯一入口 `127.0.0.1:29088→Nginx80`，没有扩大应用host端口。
- 凭据仅忽略文件 `.local-runtime/ai-interview-proxy/backend.env` 给Java；RUSTFS/AWS管理keys不存在，DEEPSEEK_API_KEY空。Nginx没有业务凭据env；driver只读自己合成login.json并清除继承DB/AI/storage/root环境，不读应用存储凭据。
- 只读mount最终JAR `/app/workbench.jar`、真实Nginx `/etc/nginx/conf.d/default.conf` 和 `frontend/dist`；没有改配置正文或利用SDK直接公开文件。

| 来源 | SHA256 / cached image ID |
|---|---|
| backend/target/backend-0.0.1-SNAPSHOT.jar | f3af9f4722961af24ece4058ab9b9afdc8b9cb63196508768a6151c2698b7b39 |
| frontend/dist/index.html | a732be69df6abda406e33bb9d6452e08909b21c7d6df1653691530f89f17ed6e |
| deploy/nginx.conf | 2cc4848c9034a0cb30351c312e2420122275fb6d17312e2567e0c428667e8bfa |
| eclipse-temurin:17-jre-jammy | sha256:72e36d8dd5e6aab7ca6f3bcc47b9a6b1dde9b4c7a03536ed081a1bfb98616e15 |
| nginx:1.28-alpine | sha256:dc73b49f5124cf2ee538dfbdfbd121f0b4ccdcb20fea30f3a81bd477c02e2bb5 |
| dist assets/index-5pg24vyl.js | 0245051d15dae5342ab194ba2759c7b210df0ab2dd5691dadb35e5bf8d9a13b3 |
| dist assets/index-CxYz34Se.css | 61de6012579b5c83e48eb76a16b8bc55caf8717751a7f7e37c194d28e21d5c39 |

三个挂载源码指纹在验收前后相同。采用已缓存runtime镜像与只读产物，不声称本轮另执行Dockerfile build或公网TLS/真实云验收。

## 实际执行入口与结果

以下均为忽略目录内该次真实harness，不作为产品运维CLI提交；setup拒绝既有DB/container/network/occupied port，cleanup按冻结ID+project/validation labels核身份。

```powershell
python .local-runtime/ai-interview-proxy/setup.py
python .local-runtime/ai-interview-proxy/verify.py
python .local-runtime/ai-interview-proxy/audit.py
python .local-runtime/ai-interview-proxy/cleanup.py
```

全部 **exit0**。`nginx -t`真实容器内PASS；verify通过**29个真实HTTP cases**，audit另通过**4个当前存在对象匿名RustFS GET/HEAD403**、源SHA不变、环境/挂载/发布端口身份和独立库**interview_ai_jobs=0、capture_inputs=0**。模型key空且未调用AI API，bootstrap/社区创建/文件保存/读取不会触发模型。

| 验收 | 实际HTTP结果 |
|---|---|
| anonymous current、private original GET/HEAD | 401 ProblemDetail，no-store/private；不返回filename或原bytes |
| owner private MD import、独立最终编辑正文 | 200，sourceKind=MD_FILE，原BOM/CRLF/HTML/链接bytes保留，最终正文独立保存；响应无key/provider URL/endpoint |
| owner original GET/HEAD、Range/条件 | full200完整bytes+SHA；HEAD空body与正确length；attachment/nosniff/Accept-Ranges:none/no-store/private，无Location/ETag，未返回206/304 |
| B与ADMIN自己的original GET/HEAD | 404 ProblemDetail，无A的filename/body；ADMIN没有owner绕过 |
| authenticated import missing CSRF | 403；current/version不变 |
| decoded原件与最终正文均20,000 BMP/emoji | 200，全部正文与原bytes完整读回，无截断；emoji原件和final各80,000 UTF8bytes |
| decoded20,001 BMP/emoji | 400 RESUME_TEXT_TOO_LONG，current/version不变 |
| final20,001 BMP | 400 RESUME_TEXT_TOO_LONG，current/version不变 |
| final20,001 emoji | 400 RESUME_REQUEST_INVALID（80,004bytes超过字段运输80k），current/version不变；没有冒称仍是合法20k |
| 原件精确1,048,576bytes + multipart **1,049,106bytes** | 新resume2m location放行到真实packaged validator，后端decoded正文独立20k gate返回400 **RESUME_TEXT_TOO_LONG**；不宣称1MiB原件自动满足正文组合要求 |
| 原件1,048,577bytes + multipart **1,049,107bytes** | 后端413 **ATTACHMENT_TOO_LARGE**、application/problem+json+安全detail；current/version不变 |
| 原件2,097,152bytes + multipart **2,097,682bytes** | Nginx413 text/html，尚未进入业务；current/version不变 |
| 普通PUT /api/me/resume JSON整体>1m | 原默认Nginx413 text/html，未扩大其他API门禁；current/version不变 |
| community BLOG +真实1MiB MD | create201/upload201 READY；multipart>1m经原22m location成功，真实原件GET full200/SHA一致 |
| community1MiB+1 MD | 后端413 ATTACHMENT_TOO_LARGE ProblemDetail；原件合法成功没有被resume20k规则收窄 |
| community owner GET与B/ADMIN/匿名HEAD | A原bytes可读，B/ADMIN404、匿名401，无filename；private header合同保持 |
| public /actuator/health与静态入口 | actuator404，生产index200 |
| direct anonymous current resume/community RustFS现物GET+HEAD | 两个prefix各GET/HEAD均403；从合法owner读取证明对象真实存在，并非用不存在key测试匿名私有性 |

后端JSON/原件/ProblemDetail均no-store/private。Nginx直接运输拒绝仍为其通用HTML413，不含业务内容或文件metadata，不把它伪称后端ProblemDetail。

原BOM私有原件SHA：`62ed96e52471ebf0435ff7f535e7c97c2c3e8b3c8e2232c64c8bdcb71b767b0a`；community精确1MiB `'a'` bytes SHA：`9bc1b2a288b26af7257a36277ae3816a7d4f16e89c1e7e77d0a5c48bad62b360`。全部是合成测试数据，未读取日常内容。

## 生命周期与后续边界

冻结容器身份：backend `f8931bcc4117cd01e16664667666bd383f6e2cbc5634763d377e5b2ea4f68456`、Nginx `c5599e75bc146a1474e3f246ac3f9ac31bebf84f410e7db0de41c5a1c8a0799a`，双project/validation labels匹配。验收后 exact stop+rm **仅这两个自己的容器**，核network标签且无endpoint后删除仅自己空proxy network；exit0。

保留新proxy DB/schema、Redis11 namespace与对应原件/社区合成对象，不DROP DB、清桶或删数据卷，不down -v。source三数据服务结束仍Running/healthy；没有触碰UIreal18080/15173、opsnative8080/5173或其DB/PID/备份流程。

原始无secret记录位于忽略 `.local-runtime/ai-interview-proxy/{setup,verification,audit,cleanup}.json`。本记录支持RES-AC-07与父存储/运输/private HTTP相关INT-AC；浏览器、模型质量、联合备份恢复和最终提交/归档由对应真实证据接续，不以这份代理验证代替它们。
