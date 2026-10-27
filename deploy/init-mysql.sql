-- 仅当现有 MySQL 5.7 实例还没有为本项目分配数据库时，由管理员手动执行
-- Compose 不执行此文件，也不会部署或管理 MySQL 服务
-- 应用账号和授权由数据库管理员创建，密码不写入仓库
CREATE DATABASE IF NOT EXISTS mocklab
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_bin;
