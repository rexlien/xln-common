package com.github.jasync.sql.db.mysql.util

import com.github.jasync.sql.db.Configuration
import com.github.jasync.sql.db.SSLConfiguration
import com.github.jasync.sql.db.util.AbstractURIParser
import xln.common.utils.ExecutorUtils
import java.nio.charset.Charset
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread

object R2DBCURLParser : AbstractURIParser() {


    override val DEFAULT = Configuration(
            username = "root",
            host = "127.0.0.1", //Matched JDBC default
            port = 3306,
            password = null,
            database = null
    )

    override val SCHEME = "^mysql$".toRegex()

    override fun assembleConfiguration(properties: Map<String, String>, charset: Charset): Configuration {
        return DEFAULT.copy(
                username = properties.getOrElse(USERNAME) { DEFAULT.username },
                password = properties[PASSWORD],
                database = properties[DBNAME],
                host = properties.getOrElse(HOST) { DEFAULT.host },
                port = properties[PORT]?.toInt() ?: DEFAULT.port,
                ssl = SSLConfiguration(properties),
                charset = charset,
                executionContext = ExecutorUtils.getForkJoinExecutor()
        )
    }

}