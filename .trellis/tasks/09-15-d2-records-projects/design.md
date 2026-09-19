# 技术设计

- PostgreSQL 使用显式迁移和 MyBatis 映射；业务标识采用 UUID。
- `occurred_at` 表达工作发生时间，`created_at` 表达录入时间；业务时区为 Asia/Shanghai。
- 项目改名保持标识不变，归档使用状态字段；历史记录不级联删除。
- Controller、Service、Mapper、DTO 按 project 与 record 业务模块组织。
