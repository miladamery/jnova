package ir.nova

import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*


object JnovaProperties {

    private val properties: Properties;

    init {
        val rootPath = Thread.currentThread().contextClassLoader.getResource("").path
        val appConfigPath = rootPath + "jnova.properties"
        properties = Properties()
        properties.load(InputStreamReader(FileInputStream(appConfigPath), "UTF-8"))
    }

    fun getProperty(key: String): String = properties.getProperty(key)
}