/*--------------------------------------------------------------
  00-init.sql   (for local Docker-Compose Oracle 23c)
  PDB  : MOOPPDB1    Datafile dir : /opt/oracle/oradata/MOOPPDB1
----------------------------------------------------------------*/

/*----------------------------------------------------------------
  ① SYS / SYSTEM セッション
----------------------------------------------------------------*/
WHENEVER SQLERROR EXIT 1

ALTER SESSION SET CONTAINER = MOOPPDB1;

/* 表領域 */
CREATE TABLESPACE app_data
  DATAFILE '/opt/oracle/oradata/app_data01.dbf'
  SIZE 100M AUTOEXTEND ON NEXT 50M MAXSIZE 2G;

CREATE TABLESPACE app_idx
  DATAFILE '/opt/oracle/oradata/app_idx01.dbf'
  SIZE 50M  AUTOEXTEND ON NEXT 25M MAXSIZE 1G;

/* ユーザーとロール */
CREATE USER app_owner
  IDENTIFIED BY "OwnerPwd!"
  DEFAULT   TABLESPACE app_data
  QUOTA UNLIMITED ON app_data;

CREATE USER app_runtime
  IDENTIFIED BY "RuntimePwd!"
  DEFAULT   TABLESPACE app_data
  ACCOUNT UNLOCK;

GRANT CREATE SESSION,
      CREATE TABLE,  CREATE SEQUENCE,
      CREATE VIEW,   CREATE PROCEDURE
  TO app_owner;

GRANT CREATE SESSION TO app_runtime;

/* DML 専用ロール */
CREATE ROLE app_dml;
GRANT app_dml TO app_runtime;

/*----------------------------------------------------------------
  ② APP_OWNER セッション
----------------------------------------------------------------*/
DEFINE PDB_CONN = //localhost:1521/MOOPPDB1

CONNECT app_owner/OwnerPwd!@&PDB_CONN

/* シーケンス */
CREATE SEQUENCE estimate_seq START WITH 1 NOCACHE;

/* サンプルテーブル */
CREATE TABLE estimate (
  id            VARCHAR2(36)  PRIMARY KEY,
  title         VARCHAR2(255) NOT NULL,
  customer_name VARCHAR2(255),
  total_amount  NUMBER
) 
TABLESPACE app_data
STORAGE ( INITIAL 64K NEXT 64K )  -- 任意：小容量向け
;

/* 権限をロールへ集約 */
GRANT SELECT, INSERT, UPDATE, DELETE ON estimate    TO app_dml;
GRANT SELECT ON estimate_seq                        TO app_dml;

/* サンプルデータ */
INSERT INTO estimate VALUES
  ('550e8400-e29b-41d4-a716-446655440000', '2025年度 サーバ見積', '株式会社A', 100000);
INSERT INTO estimate VALUES
  ('550e8400-e29b-41d4-a716-446655440001', 'NW更改案件',          '株式会社B', 200000);
COMMIT;

/*----------------------------------------------------------------
  ③ APP_RUNTIME セッション
----------------------------------------------------------------*/
CONNECT app_runtime/RuntimePwd!@&PDB_CONN

ALTER SESSION SET CURRENT_SCHEMA = app_owner;

/*----------------------------------------------------------------
  完了
----------------------------------------------------------------*/
PROMPT === Initialization completed successfully ===
EXIT