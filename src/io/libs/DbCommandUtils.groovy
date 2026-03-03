package io.libs

class DbCommandUtils implements Serializable {
    PipelineContext ctx

    DbCommandUtils(PipelineContext ctx) {
        this.ctx = ctx
    }

    String requireValue(def value, String optionName) {
        if (value == null || value.toString().trim().isEmpty()) {
            ctx.error("Option '${optionName}' is required")
        }
        return value.toString().trim()
    }

    String optionalValue(def value, String defaultValue) {
        return value == null || value.toString().trim().isEmpty() ? defaultValue : value.toString().trim()
    }

    String normalizeTool(def rawTool) {
        def tool = rawTool == null ? "" : rawTool.toString().trim().toLowerCase()
        if (tool in ["sqlcmd", "mssql", "sqlserver"]) {
            return "sqlcmd"
        }
        if (tool in ["psql", "postgres", "postgresql"]) {
            return "psql"
        }
        if (tool in ["ibcmd", "1c", "1c-ib"]) {
            return "ibcmd"
        }
        ctx.error("Unsupported DB tool '${rawTool}'. Allowed values: sqlcmd, psql, ibcmd")
        return ""
    }

    String normalizeIbcmdDbms(def rawDbms) {
        def dbms = rawDbms == null ? "" : rawDbms.toString().trim().toLowerCase()
        if (dbms in ["mssql", "mssqlserver", "sqlserver"]) {
            return "MSSQLServer"
        }
        if (dbms in ["postgres", "postgresql", "pgsql"]) {
            return "PostgreSQL"
        }
        ctx.error("Unsupported ibcmd DBMS '${rawDbms}'. Allowed values: MSSQLServer, PostgreSQL")
        return ""
    }

    String sqlServerHost(String server, def port) {
        def value = server.toString().trim()
        if (port != null && !port.toString().trim().isEmpty()) {
            return "${value},${port.toString().trim()}"
        }
        return value
    }

    String mssqlIdentifier(String value) {
        return "[" + value.toString().replace("]", "]]") + "]"
    }

    String mssqlString(String value) {
        return "N'" + value.toString().replace("'", "''") + "'"
    }

    String pgsqlIdentifier(String value) {
        return "\"" + value.toString().replace("\"", "\"\"") + "\""
    }

    String pgsqlString(String value) {
        return "'" + value.toString().replace("'", "''") + "'"
    }

    String commandOption(String optionName, def value) {
        if (value == null || value.toString().trim().isEmpty()) {
            return ""
        }
        def safe = value.toString().trim()
        if (!ctx.isUnix()) {
            // cmd.exe treats % as variable expansion even inside quotes.
            safe = safe.replace("%", "%%").replace("\"", "\"\"")
            return "--${optionName}=\"${safe}\" "
        }
        return "--${optionName}=${ctx.escapeArg(safe)} "
    }

    String normalizeExecutablePath(def rawPath, String executableName) {
        def path = optionalValue(rawPath, executableName)
        if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2) {
            path = path.substring(1, path.length() - 1)
        }

        def normalized = path.trim()
        if (!ctx.isUnix()) {
            normalized = normalized.replace("/", "\\")
        }
        def lower = normalized.toLowerCase()
        def exe = executableName.toLowerCase()
        def resolvedExecutable = ctx.isUnix() ? executableName : "${executableName}.exe"

        if (lower == exe || lower.endsWith("\\${exe}") || lower.endsWith("/${exe}")) {
            return ctx.isUnix() ? normalized : "${normalized}.exe"
        }
        if (lower == "${exe}.exe" || lower.endsWith("\\${exe}.exe") || lower.endsWith("/${exe}.exe")) {
            return normalized
        }
        if (normalized.endsWith("\\") || normalized.endsWith("/")) {
            return "${normalized}${resolvedExecutable}"
        }
        def separator = ctx.isUnix() ? "/" : "\\"
        return "${normalized}${separator}${resolvedExecutable}"
    }

    String resolveIbcmdDataDir(def rawDataDir, String ibcmdExecutablePath) {
        def provided = rawDataDir == null ? "" : rawDataDir.toString().trim()
        if (!provided.isEmpty()) {
            if (provided.startsWith("\"") && provided.endsWith("\"") && provided.length() >= 2) {
                provided = provided.substring(1, provided.length() - 1)
            }
            return provided.trim()
        }

        def executable = optionalValue(ibcmdExecutablePath, "ibcmd")
        def normalized = executable.trim()
        if (!ctx.isUnix()) {
            normalized = normalized.replace("/", "\\")
            def lower = normalized.toLowerCase()
            if (lower.endsWith("\\ibcmd.exe")) {
                return normalized.substring(0, normalized.length() - "\\ibcmd.exe".length())
            }
            if (lower.endsWith("\\ibcmd")) {
                return normalized.substring(0, normalized.length() - "\\ibcmd".length())
            }
            return normalized
        }

        def unixLower = normalized.toLowerCase()
        if (unixLower.endsWith("/ibcmd")) {
            return normalized.substring(0, normalized.length() - "/ibcmd".length())
        }
        return normalized
    }

    String logPath(String fileName) {
        def workspace = ctx.env("WORKSPACE")
        return workspace?.trim() ? "${workspace}\\${fileName}" : fileName
    }
}
