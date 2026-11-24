-- 为已有数据库补充用户额度字段（新库直接用 create_table.sql 即可）
-- 执行方式：mysql -uroot -p < sql/alter_add_user_quota.sql

use cong_ai_code;

alter table `user`
    add column quota int default 20 not null comment '剩余生成额度（次数）' after userRole;

-- 已有用户统一补 20 次额度
update `user` set quota = 20 where quota is null;
