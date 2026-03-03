package io.libs

class DB_method implements Serializable {
    PipelineContext ctx
    DbCommandUtils dbUtils
    SqlcmdService sqlcmd
    PsqlService psql
    IbcmdService ibcmd

    DB_method(PipelineContext ctx) {
        this.ctx = ctx
        this.dbUtils = new DbCommandUtils(ctx)
        this.sqlcmd = new SqlcmdService(ctx)
        this.psql = new PsqlService(ctx)
        this.ibcmd = new IbcmdService(ctx)
    }

    int backupDB(Map options = [:]) {
        def tool = dbUtils.normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return sqlcmd.backup(options)
        }
        if (tool == "ibcmd") {
            return ibcmd.backup(options)
        }
        return psql.backup(options)
    }

    int restoreDB(Map options = [:]) {
        def tool = dbUtils.normalizeTool(options.tool ?: options.engine ?: options.client)
        if (tool == "sqlcmd") {
            return sqlcmd.restore(options)
        }
        if (tool == "ibcmd") {
            return ibcmd.restore(options)
        }
        return psql.restore(options)
    }

    // Backward compatibility for legacy callers.
    int backupDB(String serverSql, String baseName, String backupFullPath) {
        return backupDB([
            tool: "sqlcmd",
            server: serverSql,
            database: baseName,
            backupTarget: backupFullPath
        ])
    }

    // Backward compatibility for legacy callers.
    int restoreDB(String serverSql, String baseName, String backupName, Object internal = null) {
        return restoreDB([
            tool: "sqlcmd",
            server: serverSql,
            database: baseName,
            backupTarget: backupName
        ])
    }
}
