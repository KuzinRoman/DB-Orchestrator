package io.libs

class IbcmdService implements Serializable {
    PipelineContext ctx
    DbCommandUtils dbUtils
    CommandRunner runner

    IbcmdService(PipelineContext ctx) {
        this.ctx = ctx
        this.dbUtils = new DbCommandUtils(ctx)
        this.runner = new CommandRunner(ctx)
    }

    int backup(Map options = [:]) {
        def server = dbUtils.requireValue(options.server ?: options.host, "server")
        def database = dbUtils.requireValue(options.database ?: options.dbName, "database")
        def backupTarget = dbUtils.requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def dbms = dbUtils.normalizeIbcmdDbms(options.dbms ?: options.ibcmdDbms ?: options.databaseEngine)
        def ibcmdPath = dbUtils.normalizeExecutablePath(options.ibcmdPath, "ibcmd")
        def ibcmdUser = options.ibcmdUser ?: options.infobaseUser
        def ibcmdPassword = options.ibcmdPassword ?: options.infobasePassword
        def dbServer = server

        if (ctx.fileExists(backupTarget)) {
            ctx.echo("Backup already exists: ${backupTarget}")
            return 1
        }

        def command = "${ctx.escapeArg(ibcmdPath)} infobase dump " +
            "--db-server=${ctx.escapeArg(dbServer)} " +
            "--dbms=${ctx.escapeArg(dbms)} " +
            dbUtils.commandOption("db-user", options.username) +
            dbUtils.commandOption("db-pwd", options.password) +
            dbUtils.commandOption("user", ibcmdUser) +
            dbUtils.commandOption("password", ibcmdPassword) +
            "--db-name=${ctx.escapeArg(database)} " +
            "${ctx.escapeArg(backupTarget)} " +
            "> ${ctx.escapeArg(dbUtils.logPath('ibcmd_log.txt'))} 2>&1"

        return runner.run(command)
    }

    int restore(Map options = [:]) {
        def server = dbUtils.requireValue(options.server ?: options.host, "server")
        def database = dbUtils.requireValue(options.database ?: options.dbName, "database")
        def backupTarget = dbUtils.requireValue(options.backupTarget ?: options.backupPath, "backupTarget")
        def dbms = dbUtils.normalizeIbcmdDbms(options.dbms ?: options.ibcmdDbms ?: options.databaseEngine)
        def ibcmdPath = dbUtils.normalizeExecutablePath(options.ibcmdPath, "ibcmd")
        def dataDir = dbUtils.resolveIbcmdDataDir(options.ibcmdDataDir ?: options.dataDir, ibcmdPath)
        def ibcmdUser = options.ibcmdUser ?: options.infobaseUser
        def ibcmdPassword = options.ibcmdPassword ?: options.infobasePassword
        def dbServer = server

        if (!ctx.fileExists(backupTarget)) {
            ctx.error("Backup file not found: ${backupTarget}")
        }

        def command = "${ctx.escapeArg(ibcmdPath)} infobase restore " +
            "--db-server=${ctx.escapeArg(dbServer)} " +
            "--dbms=${ctx.escapeArg(dbms)} " +
            dbUtils.commandOption("db-user", options.username) +
            dbUtils.commandOption("db-pwd", options.password) +
            dbUtils.commandOption("user", ibcmdUser) +
            dbUtils.commandOption("password", ibcmdPassword) +
            "--db-name=${ctx.escapeArg(database)} " +
            "${ctx.escapeArg(backupTarget)} " +
            "> ${ctx.escapeArg(dbUtils.logPath('ibcmd_log.txt'))} 2>&1"

        return runner.run(command)
    }
}
