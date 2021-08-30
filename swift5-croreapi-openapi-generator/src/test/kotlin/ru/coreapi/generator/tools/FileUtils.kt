package ru.coreapi.generator.tools

import org.slf4j.LoggerFactory
import java.io.File

object FileUtils {

    private val log = LoggerFactory.getLogger(FileUtils::class.java)

    fun printDirectoryTree(folder: File) {
        require(folder.isDirectory) { "folder is not a Directory" }
        val indent = 0
        val sb = StringBuilder().append("\n")
        printDirectoryTree(folder, indent, sb)
        log.info(sb.toString())
    }

    private fun printDirectoryTree(
        folder: File, indent: Int,
        sb: StringBuilder
    ) {
        require(folder.isDirectory) { "folder is not a Directory" }
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(folder.name)
        sb.append("/")
        sb.append("\n")
        for (file in folder.listFiles()) {
            if (file.isDirectory) {
                printDirectoryTree(file, indent + 1, sb)
            } else {
                printFile(file, indent + 1, sb)
            }
        }
    }

    private fun printFile(file: File, indent: Int, sb: StringBuilder) {
        sb.append(getIndentString(indent))
        sb.append("+--")
        sb.append(file.name)
        sb.append("\n")
    }

    private fun getIndentString(indent: Int): String {
        val sb = StringBuilder()
        for (i in 0 until indent) {
            sb.append("|  ")
        }
        return sb.toString()
    }
}