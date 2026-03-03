package io.libs

class CommandRunner implements Serializable {
    PipelineContext ctx

    CommandRunner(PipelineContext ctx) {
        this.ctx = ctx
    }

    int run(String command, String workDir = "") {
        return ctx.run(command, workDir)
    }

    int runOrError(String command, String errorMessage, String workDir = "") {
        def code = run(command, workDir)
        if (code != 0) {
            ctx.error(errorMessage)
        }
        return code
    }
}
