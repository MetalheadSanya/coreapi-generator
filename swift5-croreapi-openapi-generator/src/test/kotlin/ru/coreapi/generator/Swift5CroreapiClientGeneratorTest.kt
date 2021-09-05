package ru.coreapi.generator


import ru.coreapi.generator.tools.CodegenAbstractTest
import kotlin.test.Test


/**
 * Тест для [Swift5CroreapiClientGenerator]
 */
class Swift5CroreapiClientGeneratorTest : CodegenAbstractTest() {

    @Test
    fun shouldGenerateSimpleModel() {
        generateClient(specificationPath = "/cases/simple-model/simple-model.yaml")
        val modelProbs = getResourceAsFile("/cases/simple-model/generated/").listFiles()!!
        modelProbs
            .map {it.name}
            .forEach { nameOfModel ->
                checkModel("/OpenAPIClient/Classes/OpenAPIs/Models/$nameOfModel","/cases/simple-model/generated/$nameOfModel")
        }
    }
}
