package io.libs

class PsqlService implements Serializable {
    PipelineContext ctx
    DbCommandUtils dbUtils
    CommandRunner runner

    PsqlService(PipelineContext ctx) {
        this.ctx = ctx
        this.dbUtils = new DbCommandUtils(ctx)
        this.runner = new CommandRunner(ctx)
    }

    int backup(Map options = [:]) {
        def host = dbUtils.requireValue(options.server ?: options.host, "host")
        def database = dbUtils.requireValue(options.database ?: options.dbName, "database")
        def backupTarget = dbUtils.requireValue(options.backupTarget ?: options.backupPath, "backupTarget")

        if (ctx.fileExists(backupTarget)) {
            ctx.echo("Backup already exists: ${backupTarget}")
            return 1
        }

        def command = pgDumpCommand(host, options.port, database, options.username, backupTarget)
        return runner.run(command)
    }

    int restore(Map options = [:]) {
        def host = dbUtils.requireValue(options.server ?: options.host, "host")
        def database = dbUtils.requireValue(options.database ?: options.dbName, "database")
        def backupTarget = dbUtils.requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def maintenanceDb = "postgres"

        if (!ctx.fileExists(backupTarget)) {
            ctx.error("Backup file not found: ${backupTarget}")
        }

        def terminateSql = "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
            "WHERE datname = ${dbUtils.pgsqlString(database)} AND pid <> pg_backend_pid();"
        def terminateCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, terminateSql))
        if (terminateCode != 0) {
            return terminateCode
        }

        def dropSql = "DROP DATABASE IF EXISTS ${dbUtils.pgsqlIdentifier(database)};"
        def dropCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, dropSql))
        if (dropCode != 0) {
            return dropCode
        }

        def createSql = "CREATE DATABASE ${dbUtils.pgsqlIdentifier(database)};"
        def createCode = runner.run(psqlCommand(host, options.port, maintenanceDb, options.username, createSql))
        if (createCode != 0) {
            return createCode
        }

        return runner.run(psqlFileRestoreCommand(host, options.port, database, options.username, backupTarget))
    }

    private String psqlCommand(String host, def port, String database, def username, String sql) {
        return "psql -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            "-d ${ctx.escapeArg(database)} " +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-w " +
            "-v ON_ERROR_STOP=1 -X -q " +
            "-L ${ctx.escapeArg(dbUtils.logPath('psql_log.txt'))} " +
            "-c ${ctx.escapeArg(sql)}"
    }

    private String psqlFileRestoreCommand(String host, def port, String database, def username, String backupFile) {
        return "psql -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            "-d ${ctx.escapeArg(database)} " +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-w -v ON_ERROR_STOP=1 -X " +
            "-L ${ctx.escapeArg(dbUtils.logPath('psql_log.txt'))} " +
            "-f ${ctx.escapeArg(backupFile)}"
    }

    private String pgDumpCommand(String host, def port, String database, def username, String backupFile) {
        return "pg_dump -h ${ctx.escapeArg(host)} " +
            (port != null && !port.toString().trim().isEmpty() ? "-p ${ctx.escapeArg(port.toString().trim())} " : "") +
            (username != null && !username.toString().trim().isEmpty() ? "-U ${ctx.escapeArg(username.toString())} " : "") +
            "-d ${ctx.escapeArg(database)} -w -F p -f ${ctx.escapeArg(backupFile)} --no-owner --no-privileges"
    }
}
