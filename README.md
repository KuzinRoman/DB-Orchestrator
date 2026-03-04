# DB Orchestrator

Репозиторий с пайплайном резервного копирования/восстановления БД и запуском произвольного скрипта после разворачивания.
Можно применять, как для баз 1С, так и вообще для любых баз MSSQL и PostgreSQL.
Запуск скрипта позволяет деперсонифицировать данные, зная SQL-таблицы.

Репозиторий пявился в рамках дополнительного занятия по курсу `archdevops.ru`.
Видео демонстрирует работу пайплайна - https://vkvideo.ru/video-229431761_456239026

## Демо

![Демонстрация пайплайна](docs/demo.gif)

# Для запуска требуется:

## Если требуется автоматически создавтаь базу на кластере:

Установить oscript;
Установить irac. "opm install irac"
Установить vanessa-runner. "opm install vanessa-runner"


## Установить:
sqlcmd и psql – для работы с БД

sqlcmd - для MSSQL;
psql - для POSTGRE;
ibmcd - для бекапирования средствами 1С без выбрасывания сеансов из базы 1С.

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

- `DBMS`: `MSSQLServer` / `PostgreSQL` - СУБД
- `DB_TOOL`: `auto` / `sqlcmd` / `psql` / `ibcmd` - режим бекапирования и восстановления
- `DB_HOST`, `DB_NAME`, `DB_TARGET`
- `CREDENTIALS_ID_DB`, `CREDENTIALS_ID_IBCMD` - необходимо задать в CREDENTIALS JENKINS.
CREDENTIALS_ID_DB - логин и пароль от СУБД, CREDENTIALS_ID_IBCMD - логин и пароль от 1С.
- `RAS_HOST`, `RAS_PORT` - адрес rac для создания базы
- `POST_RESTORE_TOOL`, `POST_RESTORE_SQL_FILE`, `POST_RESTORE_SQL_TEXT` - параметры для вызова после восстановления базы.

## Примечания

- Для `sqlcmd` используется интегрированная аутентификация (`-E`).
- Для `psql` логин/пароль берутся из `CREDENTIALS_ID_DB` (`PGPASSWORD`).
- `createDatabase.os` принимает `DBMS` отдельным аргументом; если не передан, fallback — `MSSQLServer`.
