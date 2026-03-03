package io.libs

class DbPipelineUtils implements Serializable {
    PipelineContext ctx
    CommandRunner shell
    DB_method db

    DbPipelineUtils(steps) {
        this.ctx = new PipelineContext(steps)
        this.shell = new CommandRunner(ctx)
        this.db = new DB_method(ctx)
    }

    int cmd(String command, String workDir = "") {
        return shell.run(command, workDir)
    }

    int backupDb(Map options = [:]) {
        return db.backupDB(options)
    }

    int restoreDb(Map options = [:]) {
        return db.restoreDB(options)
    }

    String escapeArg(def value) {
        return ctx.escapeArg(value == null ? "" : value.toString())
    }
}
