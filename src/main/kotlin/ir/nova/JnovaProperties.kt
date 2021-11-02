package ir.nova

import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*


object JnovaProperties {

    private val properties: Properties = Properties()

    init {
        val classLoader = this.javaClass.classLoader
        classLoader.getResourceAsStream("jnova.properties").use {
            properties.load(InputStreamReader(it, "UTF-8"))
        }
    }

    fun getProperty(key: String): String = properties.getProperty(key)
}