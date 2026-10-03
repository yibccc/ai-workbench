# Research: V18 存储实现前有限技术核验

- Query: 核验固定依赖及 Java17/HTTP client、动画 WebP 与有界 subsampling、PDFBox 严格与加密结构 API、当前 RustFS1.0 SigV4/path-style/已知长度 PUT 的传输合同。
- Scope: mixed；只读当前指定合同、DTO/DAO、脱敏 IAM 证据及官方固定版 POM/源码。唯一写入本研究文件；不安装依赖、不运行 Maven、不实现 V18、不改任务状态、不读 Env 凭据。
- Date: 2026-10-03（Asia/Shanghai）

## Findings

### 1. 当前文件与必须保留的合同

| 文件 | 说明 / 代码证据 |
|---|---|
| `.trellis/tasks/10-03-community-oss/design.md:112` | ObjectStorage 的 put/open/delete，固定版本与供应商安全异常；本轮仅 RustFS。 |
| 同文件 `:122` | 真结构验证、实际 WebP 解码能力、有界采样；动画 WebP / 加密 PDF 不默认禁止，不新增像素配额。 |
| 同文件 `:124`、`:126`、`:128`、`:130` | 事务外对象 I/O，token/deadline fencing，FAILED key 保留，显式 recover，10件/50MiB，逐次鉴权后才输出流或元数据。 |
| `.trellis/tasks/10-03-community-oss/research/storage.md` | 已有基础研究；其中“RustFS 尚未建立”是较早观察，不能覆盖下面的当前 IAM 证据。 |
| `.trellis/tasks/10-03-community-publishing/storage-handoff.md:5` | V17 无附件交付边界，V18 必须接入同一保存/发布事务。 |
| `backend/src/main/java/com/aiworkbench/dto/community/CommunityModels.java:17` | AttachmentInfo 为 UUID id、String kind/fileName/contentType、long size、String state/safeFailureCode。不是之前猜测的 enum DTO。 |
| `backend/src/main/java/com/aiworkbench/entity/community/CommunityRows.java:12` | 不可变 Post 包含 owner/status/version/currentRevisionId，可供短事务使用。 |
| `backend/src/main/java/com/aiworkbench/mapper/CommunityPostMapper.java:19`、`:28` | lockOwned(ownerId,postId) 与 updateAggregate(ownerId,postId,version,status,revisionId,firstPublishedAt,updatedAt) 的真实签名。 |
| `.local-runtime/community-oss-iam-state.json:73`、`:81`、`:128`、`:158` | 已完成应用 Put/Get/Delete、292-byte 对象长度/SHA256回读、匿名拒绝、越 prefix/桶权限/自身管理拒绝；UTC 2026-10-02T19:24:48.469784+00:00。 |
| `.local-runtime/community-oss-initialize-iam.py:117`、`:134`、`:141`、`:249` | 当前成功探测使用 path-style 普通 HTTP body、AWS4-HMAC-SHA256 的 s3 scope、payload SHA256；HTTPConnection 对 bytes body 生成已知 Content-Length。源码非凭据。 |

DAO/DTO 只做必要合同核对，没有重新审查 V17 全部业务，也不把 IAM 普通 HTTP 探测算作 Java SDK 集成通过。

### 2. 固定依赖确实存在；明确同步 HTTP client

2026-10-03 通过 Python urllib **直接读取官方 Maven Central**，下面 POM 均 HTTP200（只读入内存，未写 Maven 缓存）：

| artifact | POM / Java 声明 |
|---|---|
| `software.amazon.awssdk:bom:2.55.10` | [BOM POM](https://repo.maven.apache.org/maven2/software/amazon/awssdk/bom/2.55.10/bom-2.55.10.pom)，108116 bytes。 |
| `software.amazon.awssdk:s3:2.55.10` | [s3 POM](https://repo.maven.apache.org/maven2/software/amazon/awssdk/s3/2.55.10/s3-2.55.10.pom)，9884 bytes；父 [aws-sdk-java-pom](https://repo.maven.apache.org/maven2/software/amazon/awssdk/aws-sdk-java-pom/2.55.10/aws-sdk-java-pom-2.55.10.pom):190/246 使用 jre.version=1.8，source/target 同该值。 |
| `software.amazon.awssdk:apache5-client:2.55.10` | [Apache5 POM](https://repo.maven.apache.org/maven2/software/amazon/awssdk/apache5-client/2.55.10/apache5-client-2.55.10.pom)，4453 bytes；自身 jre.version=1.8；依赖 httpclient5/httpcore5。 |
| `software.amazon.awssdk:apache-client:2.55.10` | [Apache4 POM](https://repo.maven.apache.org/maven2/software/amazon/awssdk/apache-client/2.55.10/apache-client-2.55.10.pom)，5273 bytes；模块仍存在，但固定版源码已标记 deprecated / Apache4 maintenance。当前新增实现优先上行 Apache5，不需要两个实现或回退。 |
| `org.apache.pdfbox:pdfbox:3.0.8` | [PDFBox POM](https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox/3.0.8/pdfbox-3.0.8.pom)，59250 bytes；[parent POM](https://repo.maven.apache.org/maven2/org/apache/pdfbox/pdfbox-parent/3.0.8/pdfbox-parent-3.0.8.pom):118/119 source/target=1.8，Java9+ profile release=8。 |
| `com.twelvemonkeys.imageio:imageio-webp:3.15.2` | [WebP POM](https://repo.maven.apache.org/maven2/com/twelvemonkeys/imageio/imageio-webp/3.15.2/imageio-webp-3.15.2.pom)，1852 bytes；[top parent](https://repo.maven.apache.org/maven2/com/twelvemonkeys/twelvemonkeys/3.15.2/twelvemonkeys-3.15.2.pom):256/257 source/target=8，imageio-core/metadata 与 common-* 使用同版。 |

可实施依赖：BOM 用 dependencyManagement/import；显式 compile 依赖 `s3`、`apache5-client`，另固定 PDFBox/WebP 指定版。**显式 HTTP module 是为了代码直接引用 builder，而不是依赖不确定的 runtime service loader。** 当前仅证明 POM 存在与库声明的 Java8 字节码目标，支持在 Java17 使用的预期；尚未验证 Spring Boot dependencyManagement 后实际传递版本、JDK17 编译或运行，不能声称构建已过。

[Apache5 固定源码](https://github.com/aws/aws-sdk-java-v2/blob/2.55.10/http-clients/apache5-client/src/main/java/software/amazon/awssdk/http/apache5/Apache5HttpClient.java#L422) 给出：`Apache5HttpClient.builder()`、`connectionTimeout(Duration)`、`socketTimeout(Duration)`、`connectionAcquisitionTimeout(Duration)`、`maxConnections(Integer)`。配合 S3 `ClientOverrideConfiguration.apiCallAttemptTimeout(Duration)` / `apiCallTimeout(Duration)`；所选总预算必须短于持久上传 deadline。客户端作为受控 bean 关闭，不能每个附件另建无界连接池。

### 3. WebP：支持动画；小输出不代表所有内部解码已小内存

固定 [WebPImageReader 源码](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.2/imageio/imageio-webp/src/main/java/com/twelvemonkeys/imageio/plugins/webp/WebPImageReader.java#L126) 实际处理 ANIM/ANMF。`getNumImages(true)` 搜索帧后返回数量；`getNumImages(false)` 对动画返回 -1，不能把 -1 当非法。必须 `reader.setInput(fileImageInputStream, false, true)`，seekForwardOnly=false，才能 allowSearch=true；然后逐帧取 width/height，不把 ANIM 当禁止类型，也不只验证第一帧后忽略坏后帧。

正确采样 API（应用候选代码，非已执行结果）：

```java
int sx = (int) Math.max(1L, ((long) width + 255L) / 256L);
int sy = (int) Math.max(1L, ((long) height + 255L) / 256L);
ImageReadParam param = reader.getDefaultReadParam();
param.setSourceSubsampling(sx, sy, 0, 0);
BufferedImage sample = reader.read(frameIndex, param);
```

使用 long 做 ceil，正维度先由结构检查确认；256 是验证输出采样尺寸，**不是源像素配额**。用 FileImageInputStream 保持源文件-backed；finally dispose reader / close stream；所有帧只留一张 sample，处理后释放。不能把完整 attachment 读成 byte[]。

但有两项固定版源码限制，不能被上述代码掩盖：

- [reader:76/437](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.2/imageio/imageio-webp/src/main/java/com/twelvemonkeys/imageio/plugins/webp/WebPImageReader.java#L433) 在采样前比较原始图像尺寸与整个文件长度，固定最大 expansion ratio=2048；这不是 bitstream 语法判断，合法的极高压缩图也可能被拒绝。
- [VP8LDecoder:142](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.2/imageio/imageio-webp/src/main/java/com/twelvemonkeys/imageio/plugins/webp/lossless/VP8LDecoder.java#L142) 在 subsampling 时仍创建完整尺寸临时 raster；[压缩 ALPH:570](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.2/imageio/imageio-webp/src/main/java/com/twelvemonkeys/imageio/plugins/webp/WebPImageReader.java#L570) 同样全帧分配。不能据 sample=256² 就宣称 VP8L/透明动画内部内存有界。

[ImageReaderBase:395](https://github.com/haraldk/TwelveMonkeys/blob/twelvemonkeys-3.15.2/imageio/imageio-core/src/main/java/com/twelvemonkeys/imageio/ImageReaderBase.java#L395) 的 `com.twelvemonkeys.imageio.maxImageBytes` 只影响未知 inputLength 情况；对 FileImageInputStream 的已知长度，不能用该属性解除 2048 guard。不要伪报 length=-1 或捕获所有 IIOException 后直接当合法。

避免静默新增拒绝政策的最小**结构验证路径候选**：先做流式 RIFF/WEBP 全容器与每个 ANMF 子块校验，再对库可安全采样的帧真正解码；已知库能力之外的高压缩/全帧分配路径可只做外壳和编码头结构识别，不把“库不能安全分配”命名为 FORMAT_INVALID。容器检查应按 [Google 官方规范](https://developers.google.com/speed/webp/docs/riff_container) 验证 uint32 长度/文件边界、偶数 padding、VP8X flags、ANIM 与 ANMF 顺序、帧矩形落在 canvas、每帧有 VP8/VP8L 且编码头维度一致；保留可忽略未知 chunk、metadata、透明度、动画与合法扩展，不新设帧数/像素配额。规范的 SHOULD/MAY 不能擅自升级为拒绝全部合法 reader 可接受输入的产品规则。

**证明范围必须诚实：** 上述结构分支能证明容器/帧外壳并拒绝明显改名与截断，不能证明未解码压缩 bitstream 的全部内容有效。如果验收把“所有合法 WebP 都必须完整深度解码验证”视作不可降低合同，固定库加 sample 方案尚不足，需实际有界的流式 decoder/验证器或单独资源隔离研究，不能直接报已达标。硬资源隔离应使用真实进程内存预算、父进程截止时间、destroyForcibly + waitFor 确认退出；Future.cancel/timeout 不能证明 decoder 已停止。资源不足是操作失败，不得默认为文件非法或批准新像素限制。

### 4. PDFBox：严格文件-backed 解析与加密外壳验证可分开

[固定 PDFParser:91/149](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/PDFParser.java#L149) 有明确公开 API：

```java
try (RandomAccessReadBufferedFile source = new RandomAccessReadBufferedFile(path)) {
    PDFParser parser = new PDFParser(source, "", null, null,
            IOUtils.createTempFileOnlyStreamCache());
    try (PDDocument document = parser.parse(false)) {
        // 检查 Catalog/Pages 字典与引用结构；不提取正文、不渲染页面。
    }
}
```

`parse()` 默认 lenient；`parse(false)` 关闭修复。`Loader.loadPDF(file)` 没有本例明确的 strict 开关，不能拿宽松成功代替严格证明。PDFParser 同时允许 FDF header，因此应用还需通过实际 PDF header 方法/类型排除 FDF，不只靠“任何 parser 成功”。[RandomAccessReadBufferedFile:107](https://github.com/apache/pdfbox/blob/3.0.8/io/src/main/java/org/apache/pdfbox/io/RandomAccessReadBufferedFile.java#L107) 的 Path 构造器用 FileChannel、4KiB页、最多1000缓存页；[IOUtils:373](https://github.com/apache/pdfbox/blob/3.0.8/io/src/main/java/org/apache/pdfbox/io/IOUtils.java#L373) 提供 temp-file-only stream cache。这证明源读取/stream-cache 路径，并不证明 parser 对恶意压缩 xref/ObjStm 永不分配大内存。

严格 parse 的 `checkPages` 在无修复情形主要确认 Pages 根是字典；如要验证子引用结构，必须显式遍历页树字典并做 visited/cycle 防护和 xref/对象边界检查，不用 getText/getContents/render 强迫读私有正文，也不能新加最大页数配额。[COSParser:1402](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/COSParser.java#L1402)

**加密文件不可只 catch InvalidPasswordException 后接受。** [COSParser:252/300/1861](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/COSParser.java#L252) 在严格读取 xref/trailer 后，先 prepareDecryption，再进入 Catalog/Pages；非空密码、PublicKey、其他合法 handler 失败阶段不同，错误仅证明解密前遇到某件事。无密钥也无法认证密文内的文档语义。

可实施的最小独立加密**外壳** validator：应用私有 PDFParser 子类仅暴露 validateEnvelope(Path)；在该专用路径使用可继承 protected API `setLenient(false)`、`parsePDFHeader()`、`retrieveTrailer()`，覆写 `prepareDecryption()` 避免调用密码/证书 handler。从 protected `document` 读取公开 `COSDocument.getTrailer()` / `getXrefTable()` / `getEncryptionDictionary()`，检查 clear xref/trailer、真实 /Root 引用、/Encrypt 字典及 /Filter name、索引指向的对象头/边界、Prev 循环；finally 关闭 COSDocument/source。字典存在不是完整语法证明，仍需该应用 validator 落实索引与边界校验。

[公开 COSDocument API](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/cos/COSDocument.java#L346) 与 [BaseParser protected document](https://github.com/apache/pdfbox/blob/3.0.8/pdfbox/src/main/java/org/apache/pdfbox/pdfparser/BaseParser.java#L162) 均在固定标签存在，不需要反射/private 字段。在有 clear uncompressed Catalog/Pages 时可检查它们；如果 Root 位于**加密 ObjStm**，不能强制无凭据 decompress/dereference 而拒绝合法 PDF。仅按 /Standard 某个 R 值白名单或统一强求 O/U/ID 的一种长度，也会误拒 PublicKey/其他合法 handler；按实际已公开字典类型做检查，不运行 getSecurityHandler。该路径不标 document.setDecrypted，不返回可读正文 PDDocument。

这条路径证明可观察的 PDF 外层结构并拒绝简单伪造、坏 xref、明显截断；**不能证明加密压缩对象、页面正文或所有未来安全 handler 的内容有效，也不提供病毒认证**。必须用非空密码、空密码、PublicKey/加密 ObjStm、损坏 xref/Encrypt 的实际样例验证；此次未生成样例、未执行该候选子类。

### 5. RustFS1.0：当前已知普通签名 PUT 可用；Java checksum 需真实证明

当前已执行 IAM 证据证明 s3 SigV4 + path-style + 已知长度普通 PUT 工作，未发现“必须 aws-chunked”或“必须额外 trailer checksum”的证据。探测脚本不发 SDK 默认 CRC32/trailer；不能把这次结果扩张为 SDK2.55.10 所有默认传输组合已验证。

AWS 官方说明 SDK>=2.30.0 无显式 checksum 时会自动计算 CRC32；预计算 checksum 值会取代自动计算。[AWS checksums](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html) 固定 [S3Configuration](https://github.com/aws/aws-sdk-java-v2/blob/2.55.10/services/s3/src/main/java/software/amazon/awssdk/services/s3/S3Configuration.java#L68) 的 chunked 默认 true，path-style 默认 false；已知 RequestBody 长度本身不能当作“绝无 SDK checksum/aws-chunked”的证明。

可先实现和测试一种确定传输形态，无运行时 fallback：

```java
S3Configuration.builder()
    .pathStyleAccessEnabled(true)
    .chunkedEncodingEnabled(false)
    .build();
PutObjectRequest.builder().bucket(bucket).key(key)
    .contentLength(actualSize).contentType(verifiedType)
    .checksumSHA256(Base64.getEncoder().encodeToString(rawSha256))
    .build();
RequestBody.fromFile(validatedPath);
```

checksumSHA256 需要 SHA256 原始32 bytes 的 Base64，**不能 Base64 编码 hex 文本**。actualSize/hash 都由同一不再修改的暂存文件得出，put 前再次核对 size。固定 [RequestBody:102](https://github.com/aws/aws-sdk-java-v2/blob/2.55.10/core/sdk-core/src/main/java/software/amazon/awssdk/core/sync/RequestBody.java#L102) 以文件 provider 和 Files.size 得已知长度，避免 unknown-length 的 buffering provider。

RustFS [固定1.0.0兼容矩阵](https://github.com/rustfs/rustfs/blob/1.0.0/docs/architecture/s3-compatibility-matrix.md) 记录对象操作与选定 checksum 行为支持；[同版 replication transport](https://github.com/rustfs/rustfs/blob/1.0.0/docs/operations/replication-outbound-transport.md) 的 plain body/已知 Content-Length/校验头证据是**RustFS 向外复制**路径，不可误写为本应用 Java 入站验证。SDK显式 SHA256 header 的实际入站接受仍须运行真实小文件及边界文件 Put/Get/hash/Delete，验证不存入 chunk framing、远端实际长度/字节与源一致，错误 checksum 被拒绝。不要只观察 PUT200。

无需现在增加 LegacyMd5Plugin、sdk旧版、WHEN_REQUIRED自动重试、公开 bucket、无签名降级或阿里云 adapter；这些既无当前故障证据，也未获用户兼容授权。若实际固定客户端失败，保留明确失败证据再修正单一当前传输实现。

### 6. Related specs

- `.trellis/workflow.md`：研究落盘与角色隔离；已批准任务由主会话推进，不由研究 agent 改状态。
- `.trellis/spec/backend/index.md`、`runtime-integration.md`：Java17、真实构建/基础设施证明、loopback/凭据边界；POM存在与配置合法不是运行通过。
- 父 `design.md:112` / `:122`：固定依赖、外壳/真实结构、无新增合法子类型拒绝；`:124` / `:126`：网络超时必须配合持久 fencing。
- `AGENTS.md`：不主动新增兼容层、旧版回退或默认禁用门禁；本报告没有批准新配额/格式限制。

## Caveats / Not Found

1. 默认 exec 的 sandbox ACL 启动失败，有限只读命令与本研究文件写入使用 require_escalated；没有扩张到产品修改授权。
2. web 工具对 Central POM / 猜测的 RustFS Java文档地址返回 unavailable；改用官方 Central URL 的只读 urllib 得 HTTP200，官方固定源码也直接读取成功。未把搜索引擎较旧 release cache 作为固定版本依据。
3. 未安装依赖、未运行 Maven/Java、未写 pom/产品配置、未重跑 IAM、未访问阿里云。文中代码均候选，不是已编译实现或测试结果。
4. WebP 的源尺寸2048扩张门禁、VP8L/ALPH完整 raster 是已查到的真实能力限制。既不能用 Future timeout假称资源已终止，也不能静默把它们转成新拒绝政策；上面的结构分支有明确深度证明边界，完整深度解码要求仍需进一步实现/证据。
5. 加密 PDF 无解密材料时只可证明外层结构，不能认证隐藏内容。密码/证书/自定义 handler 不是未经批准的禁止类型；候选外壳 API 必须由实现测试，不能仅凭异常 catch 判格式通过。
