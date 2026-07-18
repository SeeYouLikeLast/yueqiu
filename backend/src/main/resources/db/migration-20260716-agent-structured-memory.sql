-- Run once on an existing database. Fresh databases already receive this column from schema.sql.
alter table agent_conversation
  add column requirements_json json null after title;
