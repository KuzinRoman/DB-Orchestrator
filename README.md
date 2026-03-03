# DB Orchestrator

Репозиторий с пайплайном резервного копирования/восстановления БД и запуском произвольного скрипта после разворачивания.
Можно применять, как для баз 1С, так и вообще для любых баз MSSQL и PostgreSQL.
Запуск скрипта позволяет деперсонифициировать данные, зная SQL-таблицы 1С.


# Для запуска требуется:

Установить oscript;
Установить irac. "opm install irac"

Использует sqlcmd - для MSSQL;
psql - для POSTGRE;
ibmcd - для бекапирования средствами 1С без выбрасывания сенасов из базы 1С.

## Как работает, что лежит внутри

- `src/io/executor_db.groovy` — Jenkins pipeline:
  - создание бэкапа (`sqlcmd`/`psql`/`ibcmd`)
  - создание инфобазы перед восстановлением (`createDatabase.os`)
  - восстановление в целевую БД

- `src/io/libs/DB_method.groovy` и связанные сервисы:
  - `SqlcmdService.groovy`
  - `PsqlService.groovy`
  - `IbcmdService.groovy`
  - `DbCommandUtils.groovy`
  - `PipelineContext.groovy`
  - `CommandRunner.groovy`

- `src/io/libs/createDatabase.os` — oscript для создания инфобазы.

## Ожидания окружения

- Jenkins agent с Windows (`bat`) и доступом к:
  - `sqlcmd` (для MSSQL)
  - `psql` и `pg_dump` (для PostgreSQL)
  - `ibcmd` (если выбран `DB_TOOL=ibcmd`)
  - `oscript` и `vrunner` (для шага создания инфобазы)
- В Jenkins настроена Shared Library с именем `executor-db-pipeline`
  (или измените annotation в `src/io/executor_db.groovy`).

## Ключевые параметры pipeline

- `DBMS`: `MSSQLServer` / `PostgreSQL`
- `DB_TOOL`: `auto` / `sqlcmd` / `psql` / `ibcmd`
- `DB_HOST`, `DB_NAME`, `DB_TARGET`
- `CREDENTIALS_ID_DB`, `CREDENTIALS_ID_IBCMD`
- `RAS_HOST`, `RAS_PORT`
- `POST_RESTORE_TOOL`, `POST_RESTORE_SQL_FILE`, `POST_RESTORE_SQL_TEXT`

## Примечания

- Для `sqlcmd` используется интегрированная аутентификация (`-E`).
- Для `psql` логин/пароль берутся из `CREDENTIALS_ID_DB` (`PGPASSWORD`).
- `createDatabase.os` принимает `DBMS` отдельным аргументом; если не передан, fallback — `MSSQLServer`.
