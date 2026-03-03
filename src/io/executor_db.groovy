@Library('executor-db-pipeline')

import io.libs.DbPipelineUtils

def utils = new DbPipelineUtils(this)

pipeline {
    parameters {
        booleanParam(name: 'ECHO_OFF', defaultValue: true, description: 'Отключить вывод выполняемых команд в Windows bat')
        string(name: 'CREDENTIALS_ID_DB', defaultValue: (params?.CREDENTIALS_ID_DB ?: (env.CREDENTIALS_ID_DB ?: 'CREDENTIALS_ID_DB')), description: 'ID учетных данных для подключения к СУБД')
        string(name: 'CREDENTIALS_ID_IBCMD', defaultValue: (params?.CREDENTIALS_ID_IBCMD ?: (env.CREDENTIALS_ID_IBCMD ?: (params?.CREDENTIALS_ID_DB ?: (env.CREDENTIALS_ID_DB ?: 'CREDENTIALS_ID_DB')))), description: 'ID учетных данных для параметров ibcmd --user/--password')

        choice(name: 'DBMS', choices: ['MSSQLServer', 'PostgreSQL'], description: 'Тип СУБД')
        choice(name: 'DB_TOOL', choices: ['auto', 'sqlcmd', 'psql', 'ibcmd'], description: 'Инструмент для работы с БД (auto: MSSQLServer -> sqlcmd, PostgreSQL -> psql)')
        string(name: 'IBCMD_PATH', defaultValue: (params?.IBCMD_PATH ?: (env.IBCMD_PATH ?: 'ibcmd')), description: 'Путь к исполняемому файлу ibcmd')
        string(name: 'DB_HOST', defaultValue: (params?.DB_HOST ?: (env.DB_HOST ?: (env.server1c ?: 'localhost'))), description: 'Адрес сервера СУБД')
        string(name: 'RAS_HOST', defaultValue: (params?.RAS_HOST ?: (env.RAS_HOST ?: (env.server1c ?: 'localhost'))), description: 'Адрес RAS-сервера для скрипта createDatabase.os')
        string(name: 'RAS_PORT', defaultValue: (params?.RAS_PORT ?: (env.RAS_PORT ?: '1545')), description: 'Порт RAS-сервера для скрипта createDatabase.os')
        string(name: 'DB_NAME', defaultValue: (params?.DB_NAME ?: (env.DB_NAME ?: (env.database ?: 'Prosloyka'))), description: 'Имя исходной базы для создания резервной копии')
        string(name: 'DB_TARGET', defaultValue: (params?.DB_TARGET ?: (env.DB_TARGET ?: 'Prosloyka_copy')), description: 'Имя целевой базы для восстановления')

        string(name: 'BACKUP_DIR', defaultValue: (params?.BACKUP_DIR ?: (env.BACKUP_DIR ?: 'C:\\temp\\db_backups')), description: 'Папка для сохранения файла резервной копии')
        string(name: 'BACKUP_NAME', defaultValue: (params?.BACKUP_NAME ?: (env.BACKUP_NAME ?: 'db_backup')), description: 'Префикс имени файла резервной копии (без расширения)')
        choice(name: 'POST_RESTORE_TOOL', choices: ['skip', 'auto', 'sqlcmd', 'psql'], description: 'Инструмент для опционального выполнения SQL после восстановления')
        string(name: 'POST_RESTORE_SQL_FILE', defaultValue: (params?.POST_RESTORE_SQL_FILE ?: (env.POST_RESTORE_SQL_FILE ?: '')), description: 'Путь к SQL-файлу для выполнения после восстановления (необязательно)')
        text(name: 'POST_RESTORE_SQL_TEXT', defaultValue: (params?.POST_RESTORE_SQL_TEXT ?: (env.POST_RESTORE_SQL_TEXT ?: '')), description: 'SQL-текст для выполнения после восстановления (необязательно)')
    }

    agent { label 'localhost' }

    stages {
        stage('Prepare') {
            steps {
                script {
                    if (!params.DB_HOST?.trim()) {
                        error 'DB_HOST is required'
                    }
                    if (!params.DB_NAME?.trim()) {
                        error 'DB_NAME is required'
                    }
                    if (!params.RAS_HOST?.trim()) {
                        error 'RAS_HOST is required'
                    }
                    if (!params.RAS_PORT?.trim()) {
                        error 'RAS_PORT is required'
                    }
                    if (!(params.RAS_PORT.trim() ==~ /^\d+$/)) {
                        error 'RAS_PORT must be numeric'
                    }
                    if (!params.DB_TARGET?.trim()) {
                        error 'DB_TARGET is required'
                    }
                    if (params.DB_NAME.equalsIgnoreCase(params.DB_TARGET)) {
                        error 'DB_TARGET must be different from DB_NAME'
                    }
                    if (!params.BACKUP_DIR?.trim()) {
                        error 'BACKUP_DIR is required'
                    }

                    if (params.DB_TOOL == 'auto') {
                        env.EFFECTIVE_DB_TOOL = params.DBMS == 'MSSQLServer' ? 'sqlcmd' : 'psql'
                    } else {
                        env.EFFECTIVE_DB_TOOL = params.DB_TOOL
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd' && params.DBMS != 'MSSQLServer') {
                        error 'sqlcmd can be used only with DBMS=MSSQLServer'
                    }
                    if (env.EFFECTIVE_DB_TOOL == 'psql' && params.DBMS != 'PostgreSQL') {
                        error 'psql can be used only with DBMS=PostgreSQL'
                    }

                    def mkdirCmd = "if not exist ${utils.escapeArg(params.BACKUP_DIR.trim())} mkdir ${utils.escapeArg(params.BACKUP_DIR.trim())}"
                    utils.shell.runOrError(mkdirCmd, 'Unable to create backup directory')

                    def backupNameRaw = params.BACKUP_NAME?.trim() ? params.BACKUP_NAME.trim() : params.DB_NAME.trim()
                    def backupNameSafe = backupNameRaw.replaceAll('[^a-zA-Z0-9._-]', '_')
                    def extension = env.EFFECTIVE_DB_TOOL == 'sqlcmd' ? '.bak' : (env.EFFECTIVE_DB_TOOL == 'psql' ? '.sql' : '.dt')
                    env.BACKUP_FILE_PATH = "${params.BACKUP_DIR.trim()}\\${backupNameSafe}_${env.BUILD_NUMBER}${extension}"

                    if (env.EFFECTIVE_DB_TOOL == 'ibcmd') {
                        if (!params.IBCMD_PATH?.trim()) {
                            error 'IBCMD_PATH is required for ibcmd'
                        }
                        if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                            error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                        }
                    }

                    def postRestoreTool = params.POST_RESTORE_TOOL?.trim()?.toLowerCase()
                    if (!(postRestoreTool in ['skip', 'auto', 'sqlcmd', 'psql'])) {
                        error "Unsupported POST_RESTORE_TOOL '${params.POST_RESTORE_TOOL}'. Allowed values: skip, auto, sqlcmd, psql"
                    }
                    if (postRestoreTool == 'sqlcmd' && params.DBMS != 'MSSQLServer') {
                        error 'POST_RESTORE_TOOL=sqlcmd can be used only with DBMS=MSSQLServer'
                    }
                    if (postRestoreTool == 'psql' && params.DBMS != 'PostgreSQL') {
                        error 'POST_RESTORE_TOOL=psql can be used only with DBMS=PostgreSQL'
                    }

                    def workspacePath = env.WORKSPACE?.trim()
                    if (!workspacePath) {
                        error 'WORKSPACE is not defined'
                    }
                    def createDatabaseScriptCandidates = [
                        "${workspacePath}\\src\\io\\libs\\createDatabase.os",
                        "${workspacePath}\\libs\\createDatabase.os",
                        "${workspacePath}\\src\\os\\createDatabase.os"
                    ]
                    def resolvedCreateDatabaseScriptPath = ''
                    for (String candidate in createDatabaseScriptCandidates) {
                        if (fileExists(candidate)) {
                            resolvedCreateDatabaseScriptPath = candidate
                            break
                        }
                    }
                    if (!resolvedCreateDatabaseScriptPath) {
                        error "createDatabase.os not found. Checked: ${createDatabaseScriptCandidates.join(', ')}"
                    }
                    if (!params.CREDENTIALS_ID_DB?.trim()) {
                        error 'CREDENTIALS_ID_DB is required to create infobase before restore'
                    }
                    env.CREATE_DATABASE_OS_PATH = resolvedCreateDatabaseScriptPath

                    writeFile(file: 'backup_path.txt', text: env.BACKUP_FILE_PATH + "\r\n")
                    echo "Backup path: ${env.BACKUP_FILE_PATH}"
                }
            }
        }

        stage('Backup DB_NAME') {
            steps {
                script {
                    def runBackup = { String dbUser = '', String dbPassword = '', String ibcmdUser = '', String ibcmdPassword = '' ->
                        def options = [
                            tool: env.EFFECTIVE_DB_TOOL,
                            host: params.DB_HOST.trim(),
                            database: params.DB_NAME.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            dbms: params.DBMS,
                            ibcmdPath: params.IBCMD_PATH.trim(),
                            ibcmdUser: ibcmdUser,
                            ibcmdPassword: ibcmdPassword,
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.backupDb(options)
                        if (returnCode != 0) {
                            error "Backup failed. Check sqlcmd_log.txt, psql_log.txt or ibcmd_log.txt in workspace."
                        }
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd') {
                        runBackup('', '')
                        return
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'psql') {
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for psql'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runBackup(DB_USER, DB_PASSWORD, '', '')
                            }
                        }
                        return
                    }

                    if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                        error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_IBCMD, usernameVariable: 'IBCMD_USER', passwordVariable: 'IBCMD_PASSWORD')]) {
                        def ibcmdUser = IBCMD_USER
                        def ibcmdPassword = IBCMD_PASSWORD
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for ibcmd --db-user/--db-pwd'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            runBackup(DB_USER, DB_PASSWORD, ibcmdUser, ibcmdPassword)
                        }
                    }
                }
            }
        }

        stage('Ensure infobase before restore') {
            steps {
                script {
                    def createDatabaseScriptPath = env.CREATE_DATABASE_OS_PATH?.trim()
                    if (!createDatabaseScriptPath) {
                        error 'Path to createDatabase.os is not resolved'
                    }
                    def rasEndpoint = "${params.RAS_HOST.trim()}:${params.RAS_PORT.trim()}"
                    def descriptionBase = ''
                    try {
                        def userCause = currentBuild?.rawBuild?.getCauses()?.find { it?.class?.name == 'hudson.model.Cause$UserIdCause' }
                        descriptionBase = userCause?.userName ?: ''
                    } catch (ignored) {
                        descriptionBase = ''
                    }
                    def createInfobaseLogPath = "${env.WORKSPACE}\\create_infobase_if_absent_log.txt"

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_ADMIN_USER', passwordVariable: 'DB_ADMIN_PASSWORD')]) {
                        def command = "oscript ${utils.escapeArg(createDatabaseScriptPath)} " +
                            "${utils.escapeArg(rasEndpoint)} " +
                            "${utils.escapeArg(params.DB_TARGET.trim())} " +
                            "${utils.escapeArg(params.DB_HOST.trim())} " +
                            "${utils.escapeArg(DB_ADMIN_USER)} " +
                            "${utils.escapeArg(DB_ADMIN_PASSWORD)} " +
                            "${utils.escapeArg(params.DBMS)} " +
                            "${utils.escapeArg(descriptionBase)} " +
                            "> ${utils.escapeArg(createInfobaseLogPath)} 2>&1"
                        int returnCode = utils.cmd(command)
                        if (returnCode != 0) {
                            echo "Код возврата: ${returnCode.toString()}"
                            error 'Не удалось создать информационную базу на сервере 1С. Подробности: create_infobase_if_absent_log.txt'
                        }
                    }
                }
            }
        }

        stage('Restore to DB_TARGET') {
            steps {
                script {
                    def runRestore = { String dbUser = '', String dbPassword = '', String ibcmdUser = '', String ibcmdPassword = '' ->
                        def options = [
                            tool: env.EFFECTIVE_DB_TOOL,
                            host: params.DB_HOST.trim(),
                            database: params.DB_TARGET.trim(),
                            backupTarget: env.BACKUP_FILE_PATH,
                            dbms: params.DBMS,
                            ibcmdPath: params.IBCMD_PATH.trim(),
                            ibcmdUser: ibcmdUser,
                            ibcmdPassword: ibcmdPassword,
                            username: dbUser,
                            password: dbPassword
                        ]

                        int returnCode = utils.restoreDb(options)
                        if (returnCode != 0) {
                            error "Restore failed. Check sqlcmd_log.txt, psql_log.txt or ibcmd_log.txt in workspace."
                        }
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'sqlcmd') {
                        runRestore('', '')
                        return
                    }

                    if (env.EFFECTIVE_DB_TOOL == 'psql') {
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for psql'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                                runRestore(DB_USER, DB_PASSWORD, '', '')
                            }
                        }
                        return
                    }

                    if (!params.CREDENTIALS_ID_IBCMD?.trim()) {
                        error 'CREDENTIALS_ID_IBCMD is required for ibcmd'
                    }

                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_IBCMD, usernameVariable: 'IBCMD_USER', passwordVariable: 'IBCMD_PASSWORD')]) {
                        def ibcmdUser = IBCMD_USER
                        def ibcmdPassword = IBCMD_PASSWORD
                        if (!params.CREDENTIALS_ID_DB?.trim()) {
                            error 'CREDENTIALS_ID_DB is required for ibcmd --db-user/--db-pwd'
                        }
                        withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                            runRestore(DB_USER, DB_PASSWORD, ibcmdUser, ibcmdPassword)
                        }
                    }
                }
            }
        }

        stage('Run post-restore SQL') {
            when {
                expression {
                    def hasSqlFile = params.POST_RESTORE_SQL_FILE?.trim()
                    def hasSqlText = params.POST_RESTORE_SQL_TEXT?.trim()
                    return params.POST_RESTORE_TOOL != 'skip' && (hasSqlFile || hasSqlText)
                }
            }
            steps {
                script {
                    def postSqlFile = params.POST_RESTORE_SQL_FILE?.trim()
                    def postSqlText = params.POST_RESTORE_SQL_TEXT?.trim()

                    if (postSqlFile && postSqlText) {
                        error 'Specify only one of POST_RESTORE_SQL_FILE or POST_RESTORE_SQL_TEXT'
                    }

                    if (postSqlText) {
                        postSqlFile = 'post_restore_script.sql'
                        writeFile(file: postSqlFile, text: postSqlText + "\r\n")
                    }

                    if (!postSqlFile?.trim()) {
                        echo 'No post-restore SQL specified. Skipping.'
                        return
                    }

                    if (!fileExists(postSqlFile)) {
                        error "Post-restore SQL file not found: ${postSqlFile}"
                    }

                    def requestedPostTool = params.POST_RESTORE_TOOL?.trim()?.toLowerCase()
                    def effectivePostTool = requestedPostTool == 'auto' ? (params.DBMS == 'MSSQLServer' ? 'sqlcmd' : 'psql') : requestedPostTool

                    if (!(effectivePostTool in ['sqlcmd', 'psql'])) {
                        error "Unsupported effective post-restore tool '${effectivePostTool}'"
                    }

                    def runPostRestoreScript = { String dbUser = '' ->
                        def command
                        if (effectivePostTool == 'sqlcmd') {
                            command = "sqlcmd -S ${utils.escapeArg(params.DB_HOST.trim())} " +
                                "-d ${utils.escapeArg(params.DB_TARGET.trim())} " +
                                "-E -b -i ${utils.escapeArg(postSqlFile)} " +
                                "-o ${utils.escapeArg('sqlcmd_post_restore_log.txt')}"
                        } else {
                            command = "psql -h ${utils.escapeArg(params.DB_HOST.trim())} " +
                                "-d ${utils.escapeArg(params.DB_TARGET.trim())} " +
                                "-U ${utils.escapeArg(dbUser)} " +
                                "-w -v ON_ERROR_STOP=1 -X " +
                                "-L ${utils.escapeArg('psql_post_restore_log.txt')} " +
                                "-f ${utils.escapeArg(postSqlFile)}"
                        }

                        int returnCode = utils.cmd(command)
                        if (returnCode != 0) {
                            error "Post-restore SQL execution failed. Check sqlcmd_post_restore_log.txt or psql_post_restore_log.txt in workspace."
                        }
                    }

                    if (effectivePostTool == 'sqlcmd') {
                        runPostRestoreScript('')
                        return
                    }

                    if (!params.CREDENTIALS_ID_DB?.trim()) {
                        error 'CREDENTIALS_ID_DB is required for post-restore psql script execution'
                    }
                    withCredentials([usernamePassword(credentialsId: params.CREDENTIALS_ID_DB, usernameVariable: 'DB_USER', passwordVariable: 'DB_PASSWORD')]) {
                        withEnv(["PGPASSWORD=${DB_PASSWORD}"]) {
                            runPostRestoreScript(DB_USER)
                        }
                    }
                }
            }
        }
    }
}
