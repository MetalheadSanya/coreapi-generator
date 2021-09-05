package ru.coreapi.generator.tools

import org.junit.Assert
import org.junit.Rule
import org.junit.rules.TestName
import org.openapitools.codegen.DefaultGenerator
import org.openapitools.codegen.config.CodegenConfigurator
import org.slf4j.LoggerFactory
import ru.coreapi.generator.Swift5CroreapiClientGeneratorTest
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.BeforeTest

abstract class CodegenAbstractTest {

    @get:Rule
    var name = TestName()

    protected lateinit var codegenRootDir: File
    private val defaultGenerator: DefaultGenerator = DefaultGenerator()

    companion object {
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd__HH_mm_ss")
    }

    private fun getLogger() = LoggerFactory.getLogger(this::class.java)

    @BeforeTest
    fun initDir() {
        codegenRootDir =
            File(
                "./build/codegen-test/${name.methodName}_${DATE_TIME_FORMATTER.format(LocalDateTime.now())}_${
                    ThreadLocalRandom.current().nextInt(100)
                }"
            )
        codegenRootDir.mkdirs()
        getLogger().info("Work with: ${codegenRootDir.absolutePath}")
    }

    protected fun getResourceAsFile(resourcePath: String) =
        File(Swift5CroreapiClientGeneratorTest::class.java.getResource(resourcePath).toURI())

    protected fun checkModel(actualModelFilePath: String, expectedModelPath: String) {
        getLogger().info("Check $expectedModelPath")

        Assert.assertEquals(
            getResourceAsFile(expectedModelPath).readText(Charsets.UTF_8),
            File(codegenRootDir, "$actualModelFilePath").readText(Charsets.UTF_8)
        )
    }

    protected fun generateClient(specificationPath: String) {
        val specAbsolutePath = File(Swift5CroreapiClientGeneratorTest::class.java.getResource("$specificationPath").toURI()).absolutePath
        val clientOptInput = CodegenConfigurator().apply {
            setInputSpec(specAbsolutePath)
            setGeneratorName("swift5-croreapi")
            setOutputDir(codegenRootDir.absolutePath)
        }.toClientOptInput()

        defaultGenerator.opts(clientOptInput).generate()
        FileUtils.printDirectoryTree(codegenRootDir)
    }
}