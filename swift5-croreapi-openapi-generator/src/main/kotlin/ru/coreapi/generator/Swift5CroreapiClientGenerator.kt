package ru.coreapi.generator

import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import org.apache.commons.lang3.ArrayUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.text.WordUtils
import org.openapitools.codegen.CliOption
import org.openapitools.codegen.CodegenConstants
import org.openapitools.codegen.CodegenModel
import org.openapitools.codegen.CodegenOperation
import org.openapitools.codegen.CodegenParameter
import org.openapitools.codegen.CodegenProperty
import org.openapitools.codegen.CodegenType
import org.openapitools.codegen.DefaultCodegen
import org.openapitools.codegen.SupportingFile
import org.openapitools.codegen.languages.Swift5ClientCodegen
import org.openapitools.codegen.meta.GeneratorMetadata
import org.openapitools.codegen.meta.Stability
import org.openapitools.codegen.utils.ModelUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Copy of [Swift5ClientCodegen]
 */
class Swift5CroreapiClientGenerator : DefaultCodegen() {
    private val LOGGER = LoggerFactory.getLogger(Swift5ClientCodegen::class.java)
    private var projectName = "OpenAPIClient"
    private var nonPublicApi = false
    private var objcCompatible = false
    private var lenientTypeCast = false
    private var readonlyProperties = false
    private var swiftUseApiNamespace = false
    private var useSPMFileStructure = false
    private var swiftPackagePath = "Classes" + File.separator + "OpenAPIs"
    private var useClasses = false
    private var useBacktickEscapes = false
    private var generateModelAdditionalProperties = true
    private var hashableModels = true
    private var isMapFileBinaryToData = false
   private var responseAs: Array<String?>? = arrayOfNulls(0)
   private var sourceFolder = swiftPackagePath
   private var objcReservedWords =  mutableSetOf<String>()
   private var apiDocPath = "docs/"
   private var modelDocPath = "docs/"
    override fun getTag(): CodegenType {
        return CodegenType.CLIENT
    }

    override fun getName(): String {
        return "swift5-croreapi"
    }

    override fun getHelp(): String {
        return "Generates a Swift 5.x client library."
    }

    override fun addAdditionPropertiesToCodeGenModel(
        codegenModel: CodegenModel,
        schema: Schema<*>
    ) {
        val additionalProperties = getAdditionalProperties(schema)
        if (additionalProperties != null) {
            var inner: Schema<*>? = null
            if (ModelUtils.isArraySchema(schema)) {
                val ap = schema as ArraySchema
                inner = ap.items
            } else if (ModelUtils.isMapSchema(schema)) {
                inner = getAdditionalProperties(schema)
            }
            codegenModel.additionalPropertiesType = inner?.let { getTypeDeclaration(it) } ?: getSchemaType(additionalProperties)
        }
    }

    override fun processOpts() {
        super.processOpts()
        if (StringUtils.isEmpty(System.getenv("SWIFT_POST_PROCESS_FILE"))) {
            LOGGER.info(
                "Environment variable SWIFT_POST_PROCESS_FILE not defined so the Swift code may not be properly formatted. To define it, try 'export SWIFT_POST_PROCESS_FILE=/usr/local/bin/swiftformat' (Linux/Mac)"
            )
            LOGGER.info(
                "NOTE: To enable file post-processing, 'enablePostProcessFile' must be set to `true` (--enable-post-process-file for CLI)."
            )
        }

        // Setup project name
        if (additionalProperties.containsKey(PROJECT_NAME)) {
            projectName = additionalProperties[PROJECT_NAME].toString();
        } else {
            additionalProperties[PROJECT_NAME] = projectName
        }
        sourceFolder = projectName + File.separator + sourceFolder

        // Setup nonPublicApi option, which generates code with reduced access
        // modifiers; allows embedding elsewhere without exposing non-public API calls
        // to consumers
        if (additionalProperties.containsKey(CodegenConstants.NON_PUBLIC_API)) {
            nonPublicApi = convertPropertyToBooleanAndWriteBack(CodegenConstants.NON_PUBLIC_API)
        }
        additionalProperties[CodegenConstants.NON_PUBLIC_API] = nonPublicApi

        // Setup objcCompatible option, which adds additional properties
        // and methods for Objective-C compatibility
        if (additionalProperties.containsKey(OBJC_COMPATIBLE)) {
            objcCompatible = convertPropertyToBooleanAndWriteBack(OBJC_COMPATIBLE)
        }
        additionalProperties[OBJC_COMPATIBLE] = objcCompatible

        // add objc reserved words
        if (java.lang.Boolean.TRUE == objcCompatible) {
            reservedWords.addAll(objcReservedWords)
        }
        if (additionalProperties.containsKey(RESPONSE_AS)) {
            val responseAsObject = additionalProperties[RESPONSE_AS]
            if (responseAsObject is String) {
                responseAs =(responseAsObject.split(",".toRegex()).toTypedArray())
            } else {
                responseAs  = responseAsObject as Array<String?>?
            }
        }
        additionalProperties[RESPONSE_AS] = responseAs
        if (ArrayUtils.contains(responseAs, RESPONSE_LIBRARY_PROMISE_KIT)) {
            additionalProperties["usePromiseKit"] = true
        }
        if (ArrayUtils.contains(responseAs, RESPONSE_LIBRARY_RX_SWIFT)) {
            additionalProperties["useRxSwift"] = true
        }
        if (ArrayUtils.contains(responseAs, RESPONSE_LIBRARY_RESULT)) {
            additionalProperties["useResult"] = true
        }
        if (ArrayUtils.contains(responseAs, RESPONSE_LIBRARY_COMBINE)) {
            additionalProperties["useCombine"] = true
        }

        // Setup readonlyProperties option, which declares properties so they can only
        // be set at initialization
        if (additionalProperties.containsKey(READONLY_PROPERTIES)) {
            readonlyProperties = convertPropertyToBooleanAndWriteBack(READONLY_PROPERTIES)
        }
        additionalProperties[READONLY_PROPERTIES] = readonlyProperties

        // Setup swiftUseApiNamespace option, which makes all the API
        // classes inner-class of {{projectName}}
        if (additionalProperties.containsKey(SWIFT_USE_API_NAMESPACE)) {
            swiftUseApiNamespace = convertPropertyToBooleanAndWriteBack(SWIFT_USE_API_NAMESPACE)
        }
        if (!additionalProperties.containsKey(POD_AUTHORS)) {
            additionalProperties[POD_AUTHORS] = DEFAULT_POD_AUTHORS
        }
        if (additionalProperties.containsKey(USE_SPM_FILE_STRUCTURE)) {
            useSPMFileStructure  = convertPropertyToBooleanAndWriteBack(USE_SPM_FILE_STRUCTURE)
            sourceFolder = "Sources" + File.separator + projectName
        }
        if (additionalProperties.containsKey(SWIFT_PACKAGE_PATH) && (additionalProperties[SWIFT_PACKAGE_PATH] as String?)!!.length > 0) {
            swiftPackagePath  =(additionalProperties[SWIFT_PACKAGE_PATH] as String?)!!
            sourceFolder = swiftPackagePath
        }
        if (additionalProperties.containsKey(USE_BACKTICK_ESCAPES)) {
            useBacktickEscapes = convertPropertyToBooleanAndWriteBack(USE_BACKTICK_ESCAPES)
        }
        if (additionalProperties.containsKey(GENERATE_MODEL_ADDITIONAL_PROPERTIES)) {
            generateModelAdditionalProperties  =convertPropertyToBooleanAndWriteBack(GENERATE_MODEL_ADDITIONAL_PROPERTIES)
        }
        additionalProperties[GENERATE_MODEL_ADDITIONAL_PROPERTIES] = generateModelAdditionalProperties
        if (additionalProperties.containsKey(HASHABLE_MODELS)) {
            hashableModels = convertPropertyToBooleanAndWriteBack(HASHABLE_MODELS)
        }
        additionalProperties[HASHABLE_MODELS] = hashableModels
        if (additionalProperties.containsKey(MAP_FILE_BINARY_TO_DATA)) {
            isMapFileBinaryToData = convertPropertyToBooleanAndWriteBack(MAP_FILE_BINARY_TO_DATA)
        }
        additionalProperties[MAP_FILE_BINARY_TO_DATA] = isMapFileBinaryToData
        if (isMapFileBinaryToData) {
            typeMapping["file"] = "Data"
            typeMapping["binary"] = "Data"
        }
        if (additionalProperties.containsKey(USE_CLASSES)) {
            useClasses =convertPropertyToBooleanAndWriteBack(USE_CLASSES)
        }
        additionalProperties[USE_CLASSES] = useClasses
        lenientTypeCast = convertPropertyToBooleanAndWriteBack(LENIENT_TYPE_CAST)

        // make api and model doc path available in mustache template
        additionalProperties["apiDocPath"] = apiDocPath
        additionalProperties["modelDocPath"] = modelDocPath
        if (getLibrary() != LIBRARY_VAPOR) {
            supportingFiles.add(
                SupportingFile(
                    "Podspec.mustache",
                    "",
                    "$projectName.podspec"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "Cartfile.mustache",
                    "",
                    "Cartfile"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "CodableHelper.mustache",
                    sourceFolder,
                    "CodableHelper.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "OpenISO8601DateFormatter.mustache",
                    sourceFolder,
                    "OpenISO8601DateFormatter.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "JSONDataEncoding.mustache",
                    sourceFolder,
                    "JSONDataEncoding.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "JSONEncodingHelper.mustache",
                    sourceFolder,
                    "JSONEncodingHelper.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "git_push.sh.mustache",
                    "",
                    "git_push.sh"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "SynchronizedDictionary.mustache",
                    sourceFolder,
                    "SynchronizedDictionary.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "XcodeGen.mustache",
                    "",
                    "project.yml"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "APIHelper.mustache",
                    sourceFolder,
                    "APIHelper.swift"
                )
            )
            supportingFiles.add(
                SupportingFile(
                    "Models.mustache",
                    sourceFolder,
                    "Models.swift"
                )
            )
        }
        supportingFiles.add(
            SupportingFile(
                "Package.swift.mustache",
                "",
                "Package.swift"
            )
        )
        supportingFiles.add(
            SupportingFile(
                "Configuration.mustache",
                sourceFolder,
                "Configuration.swift"
            )
        )
        supportingFiles.add(
            SupportingFile(
                "Extensions.mustache",
                sourceFolder,
                "Extensions.swift"
            )
        )
        supportingFiles.add(
            SupportingFile(
                "APIs.mustache",
                sourceFolder,
                "APIs.swift"
            )
        )
        supportingFiles.add(
            SupportingFile(
                "gitignore.mustache",
                "",
                ".gitignore"
            )
        )
        supportingFiles.add(
            SupportingFile(
                "README.mustache",
                "",
                "README.md"
            )
        )
        when (getLibrary()) {
            LIBRARY_ALAMOFIRE -> {
                additionalProperties["useAlamofire"] = true
                supportingFiles.add(
                    SupportingFile(
                        "AlamofireImplementations.mustache",
                        sourceFolder,
                        "AlamofireImplementations.swift"
                    )
                )
            }
            LIBRARY_URLSESSION -> {
                additionalProperties["useURLSession"] = true
                supportingFiles.add(
                    SupportingFile(
                        "URLSessionImplementations.mustache",
                        sourceFolder,
                        "URLSessionImplementations.swift"
                    )
                )
            }
            LIBRARY_VAPOR -> additionalProperties["useVapor"] = true
            else -> {
            }
        }
    }

    override fun isReservedWord(word: String?): Boolean {
        return word != null && reservedWords.contains(word) //don't lowercase as super does
    }

    override fun escapeReservedWord(name: String): String {
        if (reservedWordsMappings().containsKey(name)) {
            return reservedWordsMappings()[name]!!
        }
        return if (useBacktickEscapes && !objcCompatible) "`$name`" else "_$name"
    }

    override fun modelFileFolder(): String {
        return (outputFolder + File.separator + sourceFolder
                + modelPackage().replace('.', File.separatorChar))
    }

    override fun apiFileFolder(): String {
        return (outputFolder + File.separator + sourceFolder
                + apiPackage().replace('.', File.separatorChar))
    }

    override fun getTypeDeclaration(p: Schema<*>): String {
        if (ModelUtils.isArraySchema(p)) {
            val ap = p as ArraySchema
            val inner = ap.items
            return if (ModelUtils.isSet(p)) "Set<" + getTypeDeclaration(inner) + ">" else "[" + getTypeDeclaration(inner) + "]"
        } else if (ModelUtils.isMapSchema(p)) {
            val inner = getAdditionalProperties(p)
            return "[String: " + getTypeDeclaration(inner) + "]"
        }
        return super.getTypeDeclaration(p)
    }

    override fun getSchemaType(p: Schema<*>?): String {
        val openAPIType = super.getSchemaType(p)
        val type: String
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping[openAPIType]!!
            if (languageSpecificPrimitives.contains(type) || defaultIncludes.contains(type)) {
                return type
            }
        } else {
            type = openAPIType
        }
        return toModelName(type)
    }

    override fun isDataTypeFile(dataType: String): Boolean {
        return "URL" == dataType
    }

    override fun isDataTypeBinary(dataType: String): Boolean {
        return "Data" == dataType
    }

    /**
     * Output the proper model name (capitalized).
     *
     * @param name the name of the model
     * @return capitalized model name
     */
    override fun toModelName(name: String): String {
        // FIXME parameter should not be assigned. Also declare it as "final"
        var name = name
        name = sanitizeName(name)
        if (!StringUtils.isEmpty(modelNameSuffix)) { // set model suffix
            name = name + "_" + modelNameSuffix
        }
        if (!StringUtils.isEmpty(modelNamePrefix)) { // set model prefix
            name = modelNamePrefix + "_" + name
        }

        // camelize the model name
        // phone_number => PhoneNumber
        name = org.openapitools.codegen.utils.StringUtils.camelize(name)

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            val modelName = "Model$name"
            LOGGER.warn("{} (reserved word) cannot be used as model name. Renamed to {}", name, modelName)
            return modelName
        }

        // model name starts with number
        if (name.matches("^\\d.*".toRegex())) {
            // e.g. 200Response => Model200Response (after camelize)
            val modelName = "Model$name"
            LOGGER.warn(
                "{} (model name starts with number) cannot be used as model name. Renamed to {}", name,
                modelName
            )
            return modelName
        }
        return name
    }

    /**
     * Return the capitalized file name of the model.
     *
     * @param name the model name
     * @return the file name of the model
     */
    override fun toModelFilename(name: String): String {
        // should be the same as the model name
        return toModelName(name)
    }

    override fun toDefaultValue(p: Schema<*>): String? {
        if (p.enum != null && !p.enum.isEmpty()) {
            if (p.default != null) {
                return if (ModelUtils.isStringSchema(p)) {
                    "." + toEnumVarName(escapeText(p.default as String), p.type)
                } else {
                    "." + toEnumVarName(escapeText(p.default.toString()), p.type)
                }
            }
        }
        if (p.default != null) {
            if (ModelUtils.isIntegerSchema(p) || ModelUtils.isNumberSchema(p) || ModelUtils.isBooleanSchema(p)) {
                return p.default.toString()
            } else if (ModelUtils.isDateTimeSchema(p)) {
                // Datetime time stamps in Swift are expressed as Seconds with Microsecond precision.
                // In Java, we need to be creative to get the Timestamp in Microseconds as a long.
                val instant = (p.default as OffsetDateTime).toInstant()
                val epochMicro = TimeUnit.SECONDS.toMicros(instant.epochSecond) + instant[ChronoField.MICRO_OF_SECOND]
                return "Date(timeIntervalSince1970: $epochMicro.0 / 1_000_000)"
            } else if (ModelUtils.isStringSchema(p)) {
                return "\"" + escapeText(p.default as String) + "\""
            }
            // TODO: Handle more cases from `ModelUtils`, such as Date
        }
        return null
    }

    override fun toInstantiationType(p: Schema<*>): String? {
        if (ModelUtils.isMapSchema(p)) {
            return getSchemaType(getAdditionalProperties(p))
        } else if (ModelUtils.isArraySchema(p)) {
            val ap = p as ArraySchema
            val inner = getSchemaType(ap.items)
            return if (ModelUtils.isSet(p)) "Set<$inner>" else "[$inner]"
        }
        return null
    }

    override fun toApiName(name: String): String {
        return if (name.length == 0) {
            "DefaultAPI"
        } else org.openapitools.codegen.utils.StringUtils.camelize(apiNamePrefix + "_" + name) + "API"
    }

    override fun apiDocFileFolder(): String {
        return "$outputFolder/$apiDocPath".replace("/", File.separator)
    }

    override fun modelDocFileFolder(): String {
        return "$outputFolder/$modelDocPath".replace("/", File.separator)
    }

    override fun toModelDocFilename(name: String): String {
        return toModelName(name)
    }

    override fun toApiDocFilename(name: String): String {
        return toApiName(name)
    }

    override fun toOperationId(operationId: String): String {
        var operationId = operationId
        operationId = org.openapitools.codegen.utils.StringUtils.camelize(sanitizeName(operationId), true)

        // Throw exception if method name is empty.
        // This should not happen but keep the check just in case
        if (StringUtils.isEmpty(operationId)) {
            throw RuntimeException("Empty method name (operationId) not allowed")
        }

        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(operationId)) {
            val newOperationId = org.openapitools.codegen.utils.StringUtils.camelize("call_$operationId", true)
            LOGGER.warn("{} (reserved word) cannot be used as method name. Renamed to {}", operationId, newOperationId)
            return newOperationId
        }

        // operationId starts with a number
        if (operationId.matches("^\\d.*".toRegex())) {
            LOGGER.warn(
                "{} (starting with a number) cannot be used as method name. Renamed to {}",
                operationId,
                org.openapitools.codegen.utils.StringUtils.camelize(sanitizeName("call_$operationId"), true)
            )
            operationId = org.openapitools.codegen.utils.StringUtils.camelize(sanitizeName("call_$operationId"), true)
        }
        return operationId
    }

    override fun toVarName(name: String): String {
        // sanitize name
        var name = name
        name = sanitizeName(name)

        // if it's all uppper case, do nothing
        if (name.matches("^[A-Z_]*$".toRegex())) {
            return name
        }

        // camelize the variable name
        // pet_id => petId
        name = org.openapitools.codegen.utils.StringUtils.camelize(name, true)

        // for reserved words surround with `` or append _
        if (isReservedWord(name)) {
            name = escapeReservedWord(name)
        }

        // for words starting with number, append _
        if (name.matches("^\\d.*".toRegex())) {
            name = "_$name"
        }
        return name
    }

    override fun toParamName(name: String): String {
        // sanitize name
        var name = name
        name = sanitizeName(name)

        // replace - with _ e.g. created-at => created_at
        name = name.replace("-".toRegex(), "_")

        // if it's all uppper case, do nothing
        if (name.matches("^[A-Z_]*$".toRegex())) {
            return name
        }

        // camelize(lower) the variable name
        // pet_id => petId
        name = org.openapitools.codegen.utils.StringUtils.camelize(name, true)

        // for reserved words surround with ``
        if (isReservedWord(name)) {
            name = escapeReservedWord(name)
        }

        // for words starting with number, append _
        if (name.matches("^\\d.*".toRegex())) {
            name = "_$name"
        }
        return name
    }

    override fun fromModel(name: String, model: Schema<*>?): CodegenModel {
        val allDefinitions = ModelUtils.getSchemas(openAPI)
        var codegenModel = super.fromModel(name, model)
        if (codegenModel.description != null) {
            codegenModel.imports.add("ApiModel")
        }
        if (allDefinitions != null) {
            var parentSchema = codegenModel.parentSchema

            // multilevel inheritance: reconcile properties of all the parents
            while (parentSchema != null) {
                val parentModel = allDefinitions[parentSchema]!!
                val parentCodegenModel = super.fromModel(
                    codegenModel.parent,
                    parentModel
                )
                codegenModel = reconcileProperties(codegenModel, parentCodegenModel)

                // get the next parent
                parentSchema = parentCodegenModel.parentSchema
            }
        }
        if (hashableModels) {
            codegenModel.vendorExtensions["x-swift-hashable"] = true
        }
        return codegenModel
    }

    override fun toEnumValue(value: String, datatype: String): String {
        // for string, array of string
        return if ("String" == datatype || "[String]" == datatype || "[String: String]" == datatype) {
            "\"" + value + "\""
        } else {
            value
        }
    }

    override fun toEnumDefaultValue(value: String, datatype: String): String {
        return datatype + "_" + value
    }

    override fun toEnumVarName(name: String, datatype: String): String {
        var name = name
        if (name.length == 0) {
            return "empty"
        }
        val startWithNumberPattern = Pattern.compile("^\\d+")
        val startWithNumberMatcher = startWithNumberPattern.matcher(name)
        if (startWithNumberMatcher.find()) {
            val startingNumbers = startWithNumberMatcher.group(0)
            val nameWithoutStartingNumbers = name.substring(startingNumbers.length)
            return "_" + startingNumbers + org.openapitools.codegen.utils.StringUtils.camelize(nameWithoutStartingNumbers, true)
        }

        // for symbol, e.g. $, #
        if (getSymbolName(name) != null) {
            return org.openapitools.codegen.utils.StringUtils.camelize(
                WordUtils.capitalizeFully(getSymbolName(name).toUpperCase(Locale.ROOT)),
                true
            )
        }

        // Camelize only when we have a structure defined below
        var camelized = false
        if (name.matches("[A-Z][a-z0-9]+[a-zA-Z0-9]*".toRegex())) {
            name = org.openapitools.codegen.utils.StringUtils.camelize(name, true)
            camelized = true
        }

        // Reserved Name
        val nameLowercase = StringUtils.lowerCase(name)
        if (isReservedWord(nameLowercase)) {
            return escapeReservedWord(nameLowercase)
        }

        // Check for numerical conversions
        if ("Int" == datatype || "Int32" == datatype || "Int64" == datatype || "Float" == datatype || "Double" == datatype) {
            var varName = "number" + org.openapitools.codegen.utils.StringUtils.camelize(name)
            varName = varName.replace("-".toRegex(), "minus")
            varName = varName.replace("\\+".toRegex(), "plus")
            varName = varName.replace("\\.".toRegex(), "dot")
            return varName
        }

        // If we have already camelized the word, don't progress
        // any further
        if (camelized) {
            return name
        }
        val separators = charArrayOf('-', '_', ' ', ':', '(', ')')
        return org.openapitools.codegen.utils.StringUtils.camelize(
            WordUtils.capitalizeFully(StringUtils.lowerCase(name), *separators)
                .replace("[-_ :\\(\\)]".toRegex(), ""),
            true
        )
    }

    override fun toEnumName(property: CodegenProperty): String {
        var enumName = toModelName(property.name)

        // Ensure that the enum type doesn't match a reserved word or
        // the variable name doesn't match the generated enum type or the
        // Swift compiler will generate an error
        if (isReservedWord(property.datatypeWithEnum)
            || toVarName(property.name) == property.datatypeWithEnum
        ) {
            enumName = property.datatypeWithEnum + "Enum"
        }

        // TODO: toModelName already does something for names starting with number,
        // so this code is probably never called
        return if (enumName.matches("\\d.*".toRegex())) { // starts with number
            "_$enumName"
        } else {
            enumName
        }
    }

    override fun postProcessModels(objs: Map<String, Any>): Map<String, Any> {
        val postProcessedModelsEnum = postProcessModelsEnum(objs)

        // We iterate through the list of models, and also iterate through each of the
        // properties for each model. For each property, if:
        //
        // CodegenProperty.name != CodegenProperty.baseName
        //
        // then we set
        //
        // CodegenProperty.vendorExtensions["x-codegen-escaped-property-name"] = true
        //
        // Also, if any property in the model has x-codegen-escaped-property-name=true, then we mark:
        //
        // CodegenModel.vendorExtensions["x-codegen-has-escaped-property-names"] = true
        //
        val models = postProcessedModelsEnum["models"] as List<Any>?
        for (_mo in models!!) {
            val mo = _mo as Map<String, Any>
            val cm = mo["model"] as CodegenModel?
            var modelHasPropertyWithEscapedName = false
            for (prop in cm!!.allVars) {
                if (prop.name != prop.baseName) {
                    prop.vendorExtensions["x-codegen-escaped-property-name"] = true
                    modelHasPropertyWithEscapedName = true
                }
            }
            if (modelHasPropertyWithEscapedName) {
                cm.vendorExtensions["x-codegen-has-escaped-property-names"] = true
            }
        }
        return postProcessedModelsEnum
    }

    override fun postProcessModelProperty(model: CodegenModel, property: CodegenProperty) {
        super.postProcessModelProperty(model, property)
        val isSwiftScalarType = (property.isInteger || property.isLong || property.isFloat
                || property.isDouble || property.isBoolean)
        if ((!property.required || property.isNullable) && isSwiftScalarType) {
            // Optional scalar types like Int?, Int64?, Float?, Double?, and Bool?
            // do not translate to Objective-C. So we want to flag those
            // properties in case we want to put special code in the templates
            // which provide Objective-C compatibility.
            property.vendorExtensions["x-swift-optional-scalar"] = true
        }
    }

    override fun escapeQuotationMark(input: String): String {
        // remove " to avoid code injection
        return input.replace("\"", "")
    }

    override fun escapeUnsafeCharacters(input: String): String {
        return input.replace("*/", "*_/").replace("/*", "/_*")
    }

    override fun postProcessFile(file: File?, fileType: String) {
        if (file == null) {
            return
        }
        val swiftPostProcessFile = System.getenv("SWIFT_POST_PROCESS_FILE")
        if (StringUtils.isEmpty(swiftPostProcessFile)) {
            return  // skip if SWIFT_POST_PROCESS_FILE env variable is not defined
        }
        // only process files with swift extension
        if (file.name.endsWith(".swift")) {
            val command = "$swiftPostProcessFile $file"
            try {
                val p = Runtime.getRuntime().exec(command)
                val exitValue = p.waitFor()
                if (exitValue != 0) {
                    LOGGER.error("Error running the command ({}). Exit value: {}", command, exitValue)
                } else {
                    LOGGER.info("Successfully executed: {}", command)
                }
            } catch (e: InterruptedException) {
                LOGGER.error("Error running the command ({}). Exception: {}", command, e.message)
                // Restore interrupted state
                Thread.currentThread().interrupt()
            } catch (e: IOException) {
                LOGGER.error("Error running the command ({}). Exception: {}", command, e.message)
                Thread.currentThread().interrupt()
            }
        }
    }

    override fun postProcessOperationsWithModels(objs: Map<String, Any>, allModels: List<Any>): Map<String, Any> {
        val objectMap = objs["operations"] as Map<String, Any>?
        val modelMaps = HashMap<String, CodegenModel?>()
        for (o in allModels) {
            val h = o as HashMap<String, Any>
            val m = h["model"] as CodegenModel?
            modelMaps[m!!.classname] = m
        }
        val operations = objectMap!!["operation"] as List<CodegenOperation>?
        for (operation in operations!!) {
            for (cp in operation.allParams) {
                cp.vendorExtensions["x-swift-example"] = constructExampleCode(cp, modelMaps, HashSet())
            }
        }
        return objs
    }

    fun constructExampleCode(
        codegenParameter: CodegenParameter,
        modelMaps: HashMap<String, CodegenModel?>,
        visitedModels: MutableSet<String?>
    ): String {
        return if (codegenParameter.isArray) { // array
            "[" + constructExampleCode(codegenParameter.items, modelMaps, visitedModels) + "]"
        } else if (codegenParameter.isMap) { // TODO: map, file type
            "\"TODO\""
        } else if (languageSpecificPrimitives.contains(codegenParameter.dataType)) { // primitive type
            if ("String" == codegenParameter.dataType || "Character" == codegenParameter.dataType) {
                if (StringUtils.isEmpty(codegenParameter.example)) {
                    "\"" + codegenParameter.example + "\""
                } else {
                    "\"" + codegenParameter.paramName + "_example\""
                }
            } else if ("Bool" == codegenParameter.dataType) { // boolean
                if (java.lang.Boolean.parseBoolean(codegenParameter.example)) {
                    "true"
                } else {
                    "false"
                }
            } else if ("URL" == codegenParameter.dataType) { // URL
                "URL(string: \"https://example.com\")!"
            } else if ("Data" == codegenParameter.dataType) { // URL
                "Data([9, 8, 7])"
            } else if ("Date" == codegenParameter.dataType) { // date
                "Date()"
            } else { // numeric
                if (StringUtils.isEmpty(codegenParameter.example)) {
                    codegenParameter.example
                } else {
                    "987"
                }
            }
        } else { // model
            // look up the model
            if (modelMaps.containsKey(codegenParameter.dataType)) {
                if (visitedModels.contains(codegenParameter.dataType)) {
                    // recursive/self-referencing model, simply return nil to avoid stackoverflow
                    "nil"
                } else {
                    visitedModels.add(codegenParameter.dataType)
                    constructExampleCode(modelMaps[codegenParameter.dataType], modelMaps, visitedModels)
                }
            } else {
                //LOGGER.error("Error in constructing examples. Failed to look up the model " + codegenParameter.dataType);
                "TODO"
            }
        }
    }

    fun constructExampleCode(
        codegenProperty: CodegenProperty,
        modelMaps: HashMap<String, CodegenModel?>,
        visitedModels: MutableSet<String?>
    ): String {
        return if (codegenProperty.isArray) { // array
            "[" + constructExampleCode(codegenProperty.items, modelMaps, visitedModels) + "]"
        } else if (codegenProperty.isMap) { // TODO: map, file type
            "\"TODO\""
        } else if (languageSpecificPrimitives.contains(codegenProperty.dataType)) { // primitive type
            if ("String" == codegenProperty.dataType || "Character" == codegenProperty.dataType) {
                if (StringUtils.isEmpty(codegenProperty.example)) {
                    "\"" + codegenProperty.example + "\""
                } else {
                    "\"" + codegenProperty.name + "_example\""
                }
            } else if ("Bool" == codegenProperty.dataType) { // boolean
                if (java.lang.Boolean.parseBoolean(codegenProperty.example)) {
                    "true"
                } else {
                    "false"
                }
            } else if ("URL" == codegenProperty.dataType) { // URL
                "URL(string: \"https://example.com\")!"
            } else if ("Date" == codegenProperty.dataType) { // date
                "Date()"
            } else { // numeric
                if (StringUtils.isEmpty(codegenProperty.example)) {
                    codegenProperty.example
                } else {
                    "123"
                }
            }
        } else {
            // look up the model
            if (modelMaps.containsKey(codegenProperty.dataType)) {
                if (visitedModels.contains(codegenProperty.dataType)) {
                    // recursive/self-referencing model, simply return nil to avoid stackoverflow
                    "nil"
                } else {
                    visitedModels.add(codegenProperty.dataType)
                    constructExampleCode(modelMaps[codegenProperty.dataType], modelMaps, visitedModels)
                }
            } else {
                //LOGGER.error("Error in constructing examples. Failed to look up the model " + codegenProperty.dataType);
                "\"TODO\""
            }
        }
    }

    fun constructExampleCode(
        codegenModel: CodegenModel?,
        modelMaps: HashMap<String, CodegenModel?>,
        visitedModels: MutableSet<String?>
    ): String {
        var example: String?
        example = codegenModel!!.name + "("
        val propertyExamples: MutableList<String?> = ArrayList()
        for (codegenProperty in codegenModel.vars) {
            propertyExamples.add(codegenProperty.name + ": " + constructExampleCode(codegenProperty, modelMaps, visitedModels))
        }
        example += StringUtils.join(propertyExamples, ", ")
        example += ")"
        return example
    }

    companion object {
        const val PROJECT_NAME = "projectName"
        const val RESPONSE_AS = "responseAs"
        const val OBJC_COMPATIBLE = "objcCompatible"
        const val POD_SOURCE = "podSource"
        const val POD_AUTHORS = "podAuthors"
        const val POD_SOCIAL_MEDIA_URL = "podSocialMediaURL"
        const val POD_LICENSE = "podLicense"
        const val POD_HOMEPAGE = "podHomepage"
        const val POD_SUMMARY = "podSummary"
        const val POD_DESCRIPTION = "podDescription"
        const val POD_SCREENSHOTS = "podScreenshots"
        const val POD_DOCUMENTATION_URL = "podDocumentationURL"
        const val READONLY_PROPERTIES = "readonlyProperties"
        const val SWIFT_USE_API_NAMESPACE = "swiftUseApiNamespace"
        const val DEFAULT_POD_AUTHORS = "OpenAPI Generator"
        const val LENIENT_TYPE_CAST = "lenientTypeCast"
        const val USE_SPM_FILE_STRUCTURE = "useSPMFileStructure"
        const val SWIFT_PACKAGE_PATH = "swiftPackagePath"
        const val USE_CLASSES = "useClasses"
        const val USE_BACKTICK_ESCAPES = "useBacktickEscapes"
        const val GENERATE_MODEL_ADDITIONAL_PROPERTIES = "generateModelAdditionalProperties"
        const val HASHABLE_MODELS = "hashableModels"
        const val MAP_FILE_BINARY_TO_DATA = "mapFileBinaryToData"
        protected const val LIBRARY_ALAMOFIRE = "alamofire"
        protected const val LIBRARY_URLSESSION = "urlsession"
        protected const val LIBRARY_VAPOR = "vapor"
        protected const val RESPONSE_LIBRARY_PROMISE_KIT = "PromiseKit"
        protected const val RESPONSE_LIBRARY_RX_SWIFT = "RxSwift"
        protected const val RESPONSE_LIBRARY_RESULT = "Result"
        protected const val RESPONSE_LIBRARY_COMBINE = "Combine"
        protected val RESPONSE_LIBRARIES =
            arrayOf(RESPONSE_LIBRARY_PROMISE_KIT, RESPONSE_LIBRARY_RX_SWIFT, RESPONSE_LIBRARY_RESULT, RESPONSE_LIBRARY_COMBINE)

        private fun reconcileProperties(
            codegenModel: CodegenModel,
            parentCodegenModel: CodegenModel
        ): CodegenModel {
            // To support inheritance in this generator, we will analyze
            // the parent and child models, look for properties that match, and remove
            // them from the child models and leave them in the parent.
            // Because the child models extend the parents, the properties
            // will be available via the parent.

            // Get the properties for the parent and child models
            val parentModelCodegenProperties = parentCodegenModel.vars
            val codegenProperties = codegenModel.vars
            codegenModel.allVars = ArrayList(codegenProperties)
            codegenModel.parentVars = parentCodegenModel.allVars

            // Iterate over all of the parent model properties
            var removedChildProperty = false
            for (parentModelCodegenProperty in parentModelCodegenProperties) {
                // Now that we have found a prop in the parent class,
                // and search the child class for the same prop.
                val iterator = codegenProperties.iterator()
                while (iterator.hasNext()) {
                    val codegenProperty = iterator.next()
                    if (codegenProperty.baseName == parentModelCodegenProperty.baseName) {
                        // We found a property in the child class that is
                        // a duplicate of the one in the parent, so remove it.
                        iterator.remove()
                        removedChildProperty = true
                    }
                }
            }
            if (removedChildProperty) {
                codegenModel.vars = codegenProperties
            }
            return codegenModel
        }
    }

    /**
     * Constructor for the swift5 language codegen module.
     */
    init {
        useOneOfInterfaces = true
        generatorMetadata = GeneratorMetadata.newBuilder(generatorMetadata)
            .stability(Stability.STABLE)
            .build()
        outputFolder = "generated-code" + File.separator + "swift"
        modelTemplateFiles["model.mustache"] = ".swift"
        apiTemplateFiles["api.mustache"] = ".swift"
        templateDir = "swift5-croreapi"
        embeddedTemplateDir = templateDir
        apiPackage = File.separator + "APIs"
        modelPackage = File.separator + "Models"
        modelDocTemplateFiles["model_doc.mustache"] = ".md"
        apiDocTemplateFiles["api_doc.mustache"] = ".md"
        languageSpecificPrimitives = HashSet(
            Arrays.asList(
                "Int",
                "Int32",
                "Int64",
                "Float",
                "Double",
                "Bool",
                "Void",
                "String",
                "Data",
                "Date",
                "Character",
                "UUID",
                "URL",
                "AnyObject",
                "Any",
                "Decimal"
            )
        )
        defaultIncludes = HashSet(
            Arrays.asList(
                "Data",
                "Date",
                "URL",  // for file
                "UUID",
                "Array",
                "Dictionary",
                "Set",
                "Any",
                "Empty",
                "AnyObject",
                "Any",
                "Decimal"
            )
        )
        objcReservedWords = HashSet(
            Arrays.asList( // Added for Objective-C compatibility
                "id",
                "description",
                "NSArray",
                "NSURL",
                "CGFloat",
                "NSSet",
                "NSString",
                "NSInteger",
                "NSUInteger",
                "NSError",
                "NSDictionary",  // 'Property 'hash' with type 'String' cannot override a property with type 'Int' (when objcCompatible=true)
                "hash",  // Cannot override with a stored property 'className'
                "className"
            )
        )
        reservedWords = HashSet(
            Arrays.asList( // name used by swift client
                "ErrorResponse",
                "Response",  // Swift keywords. This list is taken from here:
                // https://developer.apple.com/library/content/documentation/Swift/Conceptual/Swift_Programming_Language/LexicalStructure.html#//apple_ref/doc/uid/TP40014097-CH30-ID410
                //
                // Keywords used in declarations
                "associatedtype",
                "class",
                "deinit",
                "enum",
                "extension",
                "fileprivate",
                "func",
                "import",
                "init",
                "inout",
                "internal",
                "let",
                "open",
                "operator",
                "private",
                "protocol",
                "public",
                "static",
                "struct",
                "subscript",
                "typealias",
                "var",  // Keywords uses in statements
                "break",
                "case",
                "continue",
                "default",
                "defer",
                "do",
                "else",
                "fallthrough",
                "for",
                "guard",
                "if",
                "in",
                "repeat",
                "return",
                "switch",
                "where",
                "while",  // Keywords used in expressions and types
                "as",
                "Any",
                "catch",
                "false",
                "is",
                "nil",
                "rethrows",
                "super",
                "self",
                "Self",
                "throw",
                "throws",
                "true",
                "try",  // Keywords used in patterns
                "_",  // Keywords that begin with a number sign
                "#available",
                "#colorLiteral",
                "#column",
                "#else",
                "#elseif",
                "#endif",
                "#file",
                "#fileLiteral",
                "#function",
                "#if",
                "#imageLiteral",
                "#line",
                "#selector",
                "#sourceLocation",  // Keywords reserved in particular contexts
                "associativity",
                "convenience",
                "dynamic",
                "didSet",
                "final",
                "get",
                "infix",
                "indirect",
                "lazy",
                "left",
                "mutating",
                "none",
                "nonmutating",
                "optional",
                "override",
                "postfix",
                "precedence",
                "prefix",
                "Protocol",
                "required",
                "right",
                "set",
                "Type",
                "unowned",
                "weak",
                "willSet",  //
                // Swift Standard Library types
                // https://developer.apple.com/documentation/swift
                //
                // Numbers and Basic Values
                "Bool",
                "Int",
                "Double",
                "Float",
                "Range",
                "ClosedRange",
                "Error",
                "Optional",  // Special-Use Numeric Types
                "UInt",
                "UInt8",
                "UInt16",
                "UInt32",
                "UInt64",
                "Int8",
                "Int16",
                "Int32",
                "Int64",
                "Float80",
                "Float32",
                "Float64",  // Strings and Text
                "String",
                "Character",
                "Unicode",
                "StaticString",  // Collections
                "Array",
                "Dictionary",
                "Set",
                "OptionSet",
                "CountableRange",
                "CountableClosedRange",  // The following are commonly-used Foundation types
                "URL",
                "Data",
                "Codable",
                "Encodable",
                "Decodable",  // The following are other words we want to reserve
                "Void",
                "AnyObject",
                "Class",
                "dynamicType",
                "COLUMN",
                "FILE",
                "FUNCTION",
                "LINE"
            )
        )
        typeMapping = HashMap()
        typeMapping["array"] = "Array"
        typeMapping["map"] = "Dictionary"
        typeMapping["set"] = "Set"
        typeMapping["date"] = "Date"
        typeMapping["Date"] = "Date"
        typeMapping["DateTime"] = "Date"
        typeMapping["boolean"] = "Bool"
        typeMapping["string"] = "String"
        typeMapping["char"] = "Character"
        typeMapping["short"] = "Int"
        typeMapping["int"] = "Int"
        typeMapping["long"] = "Int64"
        typeMapping["integer"] = "Int"
        typeMapping["Integer"] = "Int"
        typeMapping["float"] = "Float"
        typeMapping["number"] = "Double"
        typeMapping["double"] = "Double"
        typeMapping["file"] = "URL"
        typeMapping["binary"] = "URL"
        typeMapping["ByteArray"] = "Data"
        typeMapping["UUID"] = "UUID"
        typeMapping["URI"] = "String"
        typeMapping["decimal"] = "Decimal"
        typeMapping["object"] = "AnyCodable"
        typeMapping["AnyType"] = "AnyCodable"
        importMapping = HashMap()
        cliOptions.add(CliOption(PROJECT_NAME, "Project name in Xcode"))
        cliOptions.add(
            CliOption(
                RESPONSE_AS,
                "Optionally use libraries to manage response.  Currently "
                        + StringUtils.join(RESPONSE_LIBRARIES, ", ")
                        + " are available."
            )
        )
        cliOptions.add(
            CliOption(
                CodegenConstants.NON_PUBLIC_API, CodegenConstants.NON_PUBLIC_API_DESC
                        + "(default: false)"
            )
        )
        cliOptions.add(
            CliOption(
                OBJC_COMPATIBLE, "Add additional properties and methods for Objective-C "
                        + "compatibility (default: false)"
            )
        )
        cliOptions.add(CliOption(POD_SOURCE, "Source information used for Podspec"))
        cliOptions.add(CliOption(CodegenConstants.POD_VERSION, "Version used for Podspec"))
        cliOptions.add(CliOption(POD_AUTHORS, "Authors used for Podspec"))
        cliOptions.add(CliOption(POD_SOCIAL_MEDIA_URL, "Social Media URL used for Podspec"))
        cliOptions.add(CliOption(POD_LICENSE, "License used for Podspec"))
        cliOptions.add(CliOption(POD_HOMEPAGE, "Homepage used for Podspec"))
        cliOptions.add(CliOption(POD_SUMMARY, "Summary used for Podspec"))
        cliOptions.add(CliOption(POD_DESCRIPTION, "Description used for Podspec"))
        cliOptions.add(CliOption(POD_SCREENSHOTS, "Screenshots used for Podspec"))
        cliOptions.add(
            CliOption(
                POD_DOCUMENTATION_URL,
                "Documentation URL used for Podspec"
            )
        )
        cliOptions.add(
            CliOption(
                READONLY_PROPERTIES, "Make properties "
                        + "readonly (default: false)"
            )
        )
        cliOptions.add(
            CliOption(
                SWIFT_USE_API_NAMESPACE, "Flag to make all the API classes inner-class "
                        + "of {{projectName}}API"
            )
        )
        cliOptions.add(
            CliOption(
                CodegenConstants.HIDE_GENERATION_TIMESTAMP,
                CodegenConstants.HIDE_GENERATION_TIMESTAMP_DESC
            )
                .defaultValue(java.lang.Boolean.TRUE.toString())
        )
        cliOptions.add(
            CliOption(
                LENIENT_TYPE_CAST, "Accept and cast values for simple types (string->bool, "
                        + "string->int, int->string)"
            )
                .defaultValue(java.lang.Boolean.FALSE.toString())
        )
        cliOptions.add(
            CliOption(
                USE_BACKTICK_ESCAPES,
                "Escape reserved words using backticks (default: false)"
            )
                .defaultValue(java.lang.Boolean.FALSE.toString())
        )
        cliOptions.add(
            CliOption(
                GENERATE_MODEL_ADDITIONAL_PROPERTIES,
                "Generate model additional properties (default: true)"
            )
                .defaultValue(java.lang.Boolean.TRUE.toString())
        )
        cliOptions.add(CliOption(CodegenConstants.API_NAME_PREFIX, CodegenConstants.API_NAME_PREFIX_DESC))
        cliOptions.add(
            CliOption(
                USE_SPM_FILE_STRUCTURE, "Use SPM file structure"
                        + " and set the source path to Sources" + File.separator + "{{projectName}} (default: false)."
            )
        )
        cliOptions.add(
            CliOption(
                SWIFT_PACKAGE_PATH, "Set a custom source path instead of "
                        + projectName + File.separator + "Classes" + File.separator + "OpenAPIs" + "."
            )
        )
        cliOptions.add(
            CliOption(USE_CLASSES, "Use final classes for models instead of structs (default: false)")
                .defaultValue(java.lang.Boolean.FALSE.toString())
        )
        cliOptions.add(
            CliOption(
                HASHABLE_MODELS,
                "Make hashable models (default: true)"
            )
                .defaultValue(java.lang.Boolean.TRUE.toString())
        )
        cliOptions.add(
            CliOption(
                MAP_FILE_BINARY_TO_DATA,
                "[WARNING] This option will be removed and enabled by default in the future once we've enhanced the code to work with `Data` in all the different situations. Map File and Binary to Data (default: false)"
            )
                .defaultValue(java.lang.Boolean.FALSE.toString())
        )
        supportedLibraries[LIBRARY_URLSESSION] = "[DEFAULT] HTTP client: URLSession"
        supportedLibraries[LIBRARY_ALAMOFIRE] = "HTTP client: Alamofire"
        supportedLibraries[LIBRARY_VAPOR] = "HTTP client: Vapor"
        val libraryOption = CliOption(CodegenConstants.LIBRARY, "Library template (sub-template) to use")
        libraryOption.enum = supportedLibraries
        libraryOption.default = LIBRARY_URLSESSION
        cliOptions.add(libraryOption)
        setLibrary(LIBRARY_URLSESSION)
    }

    override fun postProcess() {
    }
}