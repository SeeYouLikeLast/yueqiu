-- 登录用户手动选择位置后保存详细地址；已有数据保持为空，不影响历史资料。
alter table player_profiles add column precise_address varchar(255) null;
