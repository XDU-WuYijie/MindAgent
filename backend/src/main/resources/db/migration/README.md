# SQL Migration Notes

This directory is reserved for manual SQL migration scripts.

Current project behavior:

- `db/init_schema.sql` is executed by MySQL container init only on the first startup of an empty `docker/mysql/data/` directory.
- Future schema changes can be stored here as incremental SQL files, for example:
  - `V2_add_xxx.sql`
  - `V3_update_xxx.sql`

These files are not auto-executed by the application.
