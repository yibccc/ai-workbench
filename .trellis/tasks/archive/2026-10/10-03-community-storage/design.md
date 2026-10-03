# 私有附件设计

采用父`../10-03-community-oss/design.md`的ObjectStorage、V18/引用、upload状态/预留、锁顺序、读取与cleanup合同。环境/官方SDK版本/实际命令证据见父`research/storage.md`。publishing须先完成V17，本任务可按明确所有权修改其save/publish事务接入附件；不另建发布事务副本。

拥有拟新增V18、AttachmentController/Service接口+impl/Mapper/XML/DTO、`storage/ObjectStorage`与`RustFsObjectStorage`/配置、格式验证器、附件测试；以及`pom.xml`/application/compose/nginx/local scripts/env example必要改动。此前publishing文件只动同事务附件验证/引用集成。

依赖优先AWS Java S3 SDK（版本见storage研究）、PDFBox和TwelveMonkeys WebP，若三者足够无需额外Tika。图片真实parser与解码验证可subsamping有界检查，不新增用户像素产品配额；MD严格UTF8可BOM无二进制控制字符。临时文件有界finally删除，file真实额度与multipart overhead分离。

当前单RustFS存储namespace以配置bucket/endpoint映射，DB只key/size/type/sha，不落供应商URL。后续Aliyun adapter映射同接口，复制相同key并核验后切配置；本轮不建兼容迁移/复制代码。RustFS安装初始化是执行阶段必要工作，尚未运行。

同post锁控制draft pending预留与version、真实总额、不可变revision refs；外部put/delete不在DB长事务中。cleanup标DELETING封住绑定竞争，历史引用永久保留至未来明确策略。所有stream权检前不发文件名/长度；不支持Range时完整200但先401/404，no-store/no304/云重定向。
