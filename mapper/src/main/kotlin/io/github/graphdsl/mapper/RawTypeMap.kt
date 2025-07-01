package io.github.graphdsl.mapper

import io.github.graphdsl.schema.GraphDslSchema
import io.github.graphdsl.utils.BitVector
import graphql.language.*
import graphql.schema.idl.TypeDefinitionRegistry

private typealias RawTypeMap = Map<String, GJSchemaRaw.TypeDef>

@Suppress("ktlint:standard:indent")
class GJSchemaRaw private constructor(
    override val types: Map<String, TypeDef>,
    override val directives: Map<String, Directive>,
    override val queryTypeDef: Object?,
    override val mutationTypeDef: Object?,
    override val subscriptionTypeDef: Object?
) : GraphDslSchema {
    override fun toString() = types.toString()

    companion object {
     
        /** Convert a graphql-java TypeDefinitionRegistry into
         *  a schema sketch. */
        fun fromRegistry(
            registry: TypeDefinitionRegistry,
            valueConverter: ValueConverter = ValueConverter.default,
            queryTypeName: String? = null,
            mutationTypeName: String? = null,
            subscriptionTypeName: String? = null,
        ): GJSchemaRaw {
            // graphql-java assumes these get created during language->schema translation
            val deprecatedDirectiveDef =
                DirectiveDefinition
                    .newDirectiveDefinition()
                    .name("deprecated")
                    .inputValueDefinition(
                        InputValueDefinition(
                            "reason",
                            NonNullType(TypeName("String")),
                            StringValue("No longer supported")
                        )
                    ).directiveLocations(
                        listOf(
                            DirectiveLocation("FIELD_DEFINITION"),
                            DirectiveLocation("ARGUMENT_DEFINITION"),
                            DirectiveLocation("INPUT_FIELD_DEFINITION"),
                            DirectiveLocation("ENUM_VALUE")
                        )
                    ).build()
            registry.add(deprecatedDirectiveDef)
            val specifiedByDirectiveDef =
                DirectiveDefinition
                    .newDirectiveDefinition()
                    .name("specifiedBy")
                    .inputValueDefinition(
                        InputValueDefinition("url", NonNullType(TypeName("String")))
                    ).directiveLocations(listOf(DirectiveLocation("SCALAR")))
                    .build()
            registry.add(specifiedByDirectiveDef)
            val oneOfDirectiveDef =
                DirectiveDefinition
                    .newDirectiveDefinition()
                    .name("oneOf")
                    .description(
                        Description(
                            "Indicates an Input Object is a OneOf Input Object.",
                            null,
                            false
                        )
                    ).directiveLocations(listOf(DirectiveLocation("INPUT_OBJECT")))
                    .build()
            registry.add(oneOfDirectiveDef)

            // Take a first pass, translating unions and accumulating a
            // name-to-list-of-unions map for interfaces and object types
            val unionsMap = mutableMapOf<String, MutableSet<String>>()
            val unionDefs: List<UnionTypeDefinition> =
                registry.getTypes(UnionTypeDefinition::class.java) + registry.unionTypeExtensions().values.flatten()
            for (def in unionDefs) {
                def.memberTypes.forEach {
                    unionsMap
                        .getOrPut((it as TypeName).name) {
                            mutableSetOf()
                        }.add(def.name)
                }
            }
            val membersMap = mutableMapOf<String, MutableSet<ImplementingTypeDefinition<*>>>()
            val implTypeDefs: List<ImplementingTypeDefinition<*>> =
                (
                        registry.getTypes(ImplementingTypeDefinition::class.java) +
                                registry.interfaceTypeExtensions().values.flatten() +
                                registry.objectTypeExtensions().values.flatten()
                        )
            for (def in implTypeDefs) {
                def.implements.forEach {
                    membersMap
                        .getOrPut((it as TypeName).name) {
                            mutableSetOf()
                        }.add(def)
                }
            }

            // Now build the TypeDefs
            val result = mutableMapOf<String, TypeDef>()
            val enumExtensions = registry.enumTypeExtensions()
            registry.getTypes(EnumTypeDefinition::class.java).forEach {
                val x = enumExtensions[it.name] ?: emptyList()
                val v = Enum(registry, it, x, result, valueConverter)
                result[it.name] = v
            }
            val inputObjectExtensions = registry.inputObjectTypeExtensions()
            registry.getTypes(InputObjectTypeDefinition::class.java).forEach {
                val x = inputObjectExtensions[it.name] ?: emptyList()
                val v = Input(registry, it, x, result, valueConverter)
                result[it.name] = v
            }
            val interfaceExtensions = registry.interfaceTypeExtensions()
            registry.getTypes(InterfaceTypeDefinition::class.java).forEach {
                val x = interfaceExtensions[it.name] ?: emptyList()
                val mems = allObjectTypes(it.name, membersMap)
                val v = Interface(registry, it, x, result, mems, valueConverter)
                result[it.name] = v
            }
            val objectExtensions = registry.objectTypeExtensions()
            registry.getTypes(ObjectTypeDefinition::class.java).forEach {
                // Filter out the GRAPHDSL_IGNORE stub type if it exists.
                if (it.name != GraphDslSchema.GRAPHDSL_IGNORE_SYMBOL) {
                    val x = objectExtensions[it.name] ?: emptyList()
                    val v = Object(registry, it, x, result, unionsMap[it.name] ?: emptyList(), valueConverter)
                    result[it.name] = v
                }
            }
            val scalarExtensions = registry.scalarTypeExtensions()
            registry.scalars().values.forEach {
                // Scalars are handled differently
                val x = scalarExtensions[it.name] ?: emptyList()
                val v = Scalar(registry, it as ScalarTypeDefinition, x, result, valueConverter)
                result[it.name] = v
            }
            val unionExtensions = registry.unionTypeExtensions()
            registry.getTypes(UnionTypeDefinition::class.java).forEach {
                val x = unionExtensions[it.name] ?: emptyList()
                val v = Union(registry, it, x, result, valueConverter)
                result[it.name] = v
            }

            val directives =
                registry.directiveDefinitions.entries.associate {
                    it.key to Directive(it.value, result, valueConverter)
                }

            val schemaDef = registry.schemaDefinition().orElse(null)
            val queryTypeDef = rootDef(result, schemaDef, queryTypeName, "Query")
            val mutationTypeDef = rootDef(result, schemaDef, mutationTypeName, "Mutation")
            val subscriptionTypeDef = rootDef(result, schemaDef, subscriptionTypeName, "Subscription")

            return GJSchemaRaw(result, directives, queryTypeDef, mutationTypeDef, subscriptionTypeDef)
        }

        private fun rootDef(
            defs: Map<String, TypeDef>,
            schemaDef: SchemaDefinition?,
            nameFromParam: String?,
            stdName: String
        ): Object? {
            val nameFromSchema =
                schemaDef
                    ?.operationTypeDefinitions
                    ?.find { it.name == stdName.lowercase() }
                    ?.typeName
                    ?.name
            var result: TypeDef? = nameFromSchema?.let {
                defs[nameFromSchema] ?: throw IllegalArgumentException("Type not found: $nameFromSchema")
            }
            if (nameFromParam != null && nameFromParam != NO_ROOT_TYPE_DEFAULT) {
                result = (defs[nameFromParam] ?: throw IllegalArgumentException("Type not found: $nameFromParam"))
            }
            if (result == null && nameFromParam != NO_ROOT_TYPE_DEFAULT) {
                result = defs[stdName]
            }
            if (result == null) return null
            if (result !is Object) {
                throw IllegalArgumentException("$stdName type ($nameFromParam) is not an object type.")
            }
            return result
        }

        private fun allObjectTypes(
            interfaceName: String,
            directs: Map<String, Iterable<ImplementingTypeDefinition<*>>>
        ): List<String> {
            val result = mutableListOf<String>()

            fun allObjectTypes(toAdd: ImplementingTypeDefinition<*>) {
                if (toAdd.name == interfaceName) {
                    throw IllegalArgumentException("Cyclical inheritance.")
                }
                if (toAdd is ObjectTypeDefinition) {
                    result.add(toAdd.name)
                } else {
                    directs[toAdd.name]?.let { namedTypes ->
                        namedTypes.forEach { allObjectTypes(it) }
                    }
                }
            }

            directs[interfaceName]?.let { namedTypes ->
                namedTypes.forEach { allObjectTypes(it) }
            }
            return result
        }

        /** Constant used to override default value for populating
         *  the root-type definitions.  See KDoc for [GJSchemaRaw].
         */
        const val NO_ROOT_TYPE_DEFAULT = "!!none"
    }

    // Well-designed GraphQL schemas are both immutable and highly
    // cyclical.  But the Kotlin(/Java) object-construction process
    // isn't friendly to immutable, cyclical data structures.  We
    // address this gap by passing a "type map" around during object
    // construction, which we use to lazily resolve references to
    // cyclical references.
    //
    // We've established the following protocol to ensure that our
    // lazy resolution is correct.  We differentiate between
    // "top-level" objects -- which are specifically [TypeDef]s in
    // our design -- from objects nested under those top-level
    // objects (for example, [EnumValue]s and [Field]s).  When it
    // comes to the construction of top-level objects, this is
    // done using `by lazy`, using [typeMap] to resolve references.
    // However, when it comes to the construction of nested objects,
    // since those constructors are being called when [typeMap] has
    // been populatd, we can use [typeMap] without further laziness.

    sealed interface Def : GraphDslSchema.Def {
        val def: NamedNode<*>

        override fun hasAppliedDirective(name: String) = appliedDirectives.find { it.name == name } != null
    }

    sealed interface TypeDef :
        GraphDslSchema.TypeDef,
        Def {
        override val def: TypeDefinition<*>
        val extensionDefs: List<TypeDefinition<*>>

        override fun asTypeExpr(): TypeExpr

        override val possibleObjectTypes: Set<Object>
    }

    sealed class TypeDefImpl : TypeDef {
        protected abstract val typeMap: RawTypeMap

        override fun asTypeExpr() = TypeExpr(typeMap, this.name)

        override fun toString() = describe()
    }

    abstract class Arg :
        HasDefaultValue(),
        GraphDslSchema.Arg

    interface HasArgs :
        Def,
        GraphDslSchema.HasArgs {
        override val args: List<Arg>
    }

    class DirectiveArg internal constructor(
        override val containingDef: Directive,
        override val def: InputValueDefinition,
        override val type: TypeExpr,
        override val _defaultValue: Any?,
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective>
    ) : Arg(),
        GraphDslSchema.DirectiveArg {
        override val name: String = def.name

        override fun toString() = describe()
    }

    open class Directive(
        override val def: DirectiveDefinition,
        protected val typeMap: RawTypeMap,
        protected val valueConverter: ValueConverter
    ) : GraphDslSchema.Directive,
        HasArgs {
        override val name: String = def.name
        override val isRepeatable: Boolean = def.isRepeatable
        override val args =
            def.inputValueDefinitions.map {
                val default = it.defaultValue(typeMap, valueConverter)
                DirectiveArg(this, it, typeMap.toTypeExpr(it.type), default, emptyList())
            }

        override val sourceLocation =
            def.sourceLocation?.sourceName?.let {
                GraphDslSchema.SourceLocation(it)
            }

        override val allowedLocations =
            def.directiveLocations
                .map {
                    GraphDslSchema.Directive.Location.valueOf(it.name)
                }.toSet()
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective> = emptyList()

        override fun toString() = describe()
    }

    class Scalar internal constructor(
        registry: TypeDefinitionRegistry,
        override val def: ScalarTypeDefinition,
        override val extensionDefs: List<ScalarTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        valueConverter: ValueConverter
    ) : TypeDefImpl(),
        GraphDslSchema.Scalar {
        override val name: String = def.name

        override val sourceLocation =
            def.sourceLocation?.sourceName?.let {
                GraphDslSchema.SourceLocation(it)
            }

        override val appliedDirectives by lazy {
            typeMap.collectDirectives(registry, def, extensionDefs, valueConverter)
        }

        override val possibleObjectTypes: Set<Object> get() = setOf<Object>()
    }

    class EnumValue internal constructor(
        override val containingDef: Enum,
        override val def: EnumValueDefinition,
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective>,
        override val containingExtension: GraphDslSchema.Extension<Enum, EnumValue>
    ) : GraphDslSchema.EnumValue,
        Def {
        override val name: String = def.name

        override fun toString() = describe()
    }

    class Enum internal constructor(
        registry: TypeDefinitionRegistry,
        override val def: EnumTypeDefinition,
        override val extensionDefs: List<EnumTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        valueConverter: ValueConverter
    ) : TypeDefImpl(),
        GraphDslSchema.Enum {
        override val name: String = def.name
        override val values by lazy { extensions.flatMap { it.members } }
        override val extensions by lazy {
            (listOf(def) + extensionDefs).map { gjLangTypeDef ->
                GraphDslSchema.Extension.of(
                    def = this@Enum,
                    memberFactory = { containingExtension ->
                        gjLangTypeDef.enumValueDefinitions.map { evd ->
                            val ad = evd.directives.toAppliedDirectives(registry, typeMap, valueConverter)
                            EnumValue(this, evd, ad, containingExtension)
                        }
                    },
                    isBase = gjLangTypeDef == def,
                    appliedDirectives = gjLangTypeDef.directives.toAppliedDirectives(registry, typeMap, valueConverter),
                    sourceLocation =
                    gjLangTypeDef.sourceLocation?.sourceName?.let {
                        GraphDslSchema.SourceLocation(it)
                    }
                )
            }
        }

        override fun value(name: String): EnumValue? = values.find { name == it.name }

        override val appliedDirectives by lazy {
            typeMap.collectDirectives(registry, def, extensionDefs, valueConverter)
        }
        override val possibleObjectTypes: Set<Object> get() = setOf<Object>()
    }

    sealed interface CompositeOutput :
        GraphDslSchema.CompositeOutput,
        TypeDef

    class Union internal constructor(
        registry: TypeDefinitionRegistry,
        override val def: UnionTypeDefinition,
        override val extensionDefs: List<UnionTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        valueConverter: ValueConverter
    ) : TypeDefImpl(),
        GraphDslSchema.Union,
        CompositeOutput {
        override val name: String = def.name
        override val possibleObjectTypes by lazy { extensions.flatMap { it.members }.toSet() }
        override val extensions by lazy {
            (listOf(def) + extensionDefs).map { gjLangTypeDef ->
                GraphDslSchema.Extension.of(
                    def = this@Union,
                    memberFactory = { _ ->
                        gjLangTypeDef.memberTypes
                            .filter { (it as TypeName).name != GraphDslSchema.GRAPHDSL_IGNORE_SYMBOL }
                            .map { (it as TypeName).name }
                            .map { typeMap[it] as Object }
                    },
                    isBase = gjLangTypeDef == def,
                    appliedDirectives = gjLangTypeDef.directives.toAppliedDirectives(registry, typeMap, valueConverter),
                    sourceLocation =
                    gjLangTypeDef.sourceLocation?.sourceName?.let {
                        GraphDslSchema.SourceLocation(it)
                    }
                )
            }
        }

        override val appliedDirectives by lazy {
            typeMap.collectDirectives(registry, def, extensionDefs, valueConverter)
        }
    }

    abstract class HasDefaultValue :
        GraphDslSchema.HasDefaultValue,
        Def {
        abstract override val containingDef: Def

        abstract override val type: TypeExpr

        protected abstract val _defaultValue: Any?

        override val hasDefault: Boolean get() = _defaultValue != NO_DEFAULT

        /** Returns the default value; throws NoSuchElementException if there is none. */
        override val defaultValue: Any?
            get() =
                _defaultValue.also {
                    if (it == NO_DEFAULT) throw NoSuchElementException("No default value")
                }

        companion object {
            internal val NO_DEFAULT = Any()
        }
    }

    class FieldArg internal constructor(
        override val containingDef: OutputField,
        override val def: InputValueDefinition,
        override val type: TypeExpr,
        override val _defaultValue: Any?,
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective>
    ) : Arg(),
        GraphDslSchema.FieldArg {
        override val name: String = def.name

        override fun toString() = describe()
    }

    sealed class Field :
        HasDefaultValue(),
        GraphDslSchema.Field,
        HasArgs {
        abstract override val containingDef: Record
        abstract override val containingExtension: GraphDslSchema.Extension<Record, Field>
        abstract override val type: TypeExpr
        abstract override val args: List<FieldArg>

        override fun toString() = describe()
    }

    class OutputField internal constructor(
        registry: TypeDefinitionRegistry,
        typeMap: RawTypeMap,
        override val def: FieldDefinition,
        override val containingExtension: GraphDslSchema.Extension<Record, Field>,
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective>,
        valueConverter: ValueConverter
    ) : Field() {
        override val name: String = def.name
        override val type = typeMap.toTypeExpr(def.type)
        override val isOverride = GraphDslSchema.isOverride(this)
        override val containingDef get() = containingExtension.def
        override val _defaultValue = NO_DEFAULT
        override val args =
            def.inputValueDefinitions.map {
                val default = it.defaultValue(typeMap, valueConverter)
                val ad = it.directives.toAppliedDirectives(registry, typeMap, valueConverter)
                FieldArg(this, it, typeMap.toTypeExpr(it.type), default, ad)
            }
    }

    class InputField internal constructor(
        override val def: InputValueDefinition,
        override val type: TypeExpr,
        override val _defaultValue: Any?,
        override val containingExtension: GraphDslSchema.Extension<Record, Field>,
        override val appliedDirectives: List<GraphDslSchema.AppliedDirective>
    ) : Field() {
        override val name: String = def.name
        override val isOverride = GraphDslSchema.isOverride(this)
        override val args = emptyList<FieldArg>()
        override val containingDef get() = containingExtension.def as Input
    }

    sealed interface Record :
        GraphDslSchema.Record,
        TypeDef {
        override val fields: List<Field>

        override fun field(name: String) = fields.find { name == it.name }

        override fun field(path: Iterable<String>): Field = GraphDslSchema.field(this, path)

        override val supers: List<Interface>
        override val unions: List<Union>
    }

    sealed class ImplementingType protected constructor(
        registry: TypeDefinitionRegistry,
        override val def: ImplementingTypeDefinition<*>,
        override val typeMap: RawTypeMap,
        valueConverter: ValueConverter
    ) : TypeDefImpl(),
        Record {
        abstract override val extensionDefs: List<ImplementingTypeDefinition<*>>

        override fun field(name: String) = super.field(name)

        override val supers by lazy {
            val superNames =
                def.implements.map {
                    (it as TypeName).name
                } +
                        extensionDefs.flatMap { it.implements }.map {
                            (it as TypeName).name
                        }
            superNames.map { typeMap[it] as Interface }
        }
        override val appliedDirectives by lazy {
            typeMap.collectDirectives(registry, def, extensionDefs, valueConverter)
        }
    }

    class Interface internal constructor(
        registry: TypeDefinitionRegistry,
        override val def: InterfaceTypeDefinition,
        override val extensionDefs: List<InterfaceTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        memberNames: Iterable<String>,
        valueConverter: ValueConverter
    ) : ImplementingType(registry, def, typeMap, valueConverter),
        GraphDslSchema.Interface,
        CompositeOutput {
        override val name: String = def.name
        override val fields by lazy { extensions.flatMap { it.members } }
        override val possibleObjectTypes by lazy { memberNames.map { typeMap[it] as Object }.toSet() }
        override val unions = emptyList<Union>()
        override val extensions by lazy {
            (listOf(def) + extensionDefs).map { gjLangTypeDef ->
                GraphDslSchema.ExtensionWithSupers.of(
                    def = this@Interface,
                    memberFactory = { containingExtension ->
                        gjLangTypeDef.fieldDefinitions
                            .filter { it.name != GraphDslSchema.GRAPHDSL_IGNORE_SYMBOL }
                            .map { fieldDefinition ->
                                val ad =
                                    fieldDefinition.directives.toAppliedDirectives(registry, typeMap, valueConverter)
                                OutputField(registry, typeMap, fieldDefinition, containingExtension, ad, valueConverter)
                            }
                    },
                    isBase = gjLangTypeDef == def,
                    appliedDirectives = gjLangTypeDef.directives.toAppliedDirectives(registry, typeMap, valueConverter),
                    supers =
                    gjLangTypeDef.implements.map {
                        typeMap[(it as TypeName).name] as Interface
                    },
                    sourceLocation =
                    gjLangTypeDef.sourceLocation?.sourceName?.let {
                        GraphDslSchema.SourceLocation(it)
                    }
                )
            }
        }
    }

    class Object internal constructor(
        registry: TypeDefinitionRegistry,
        override val def: ObjectTypeDefinition,
        override val extensionDefs: List<ObjectTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        unionNames: Iterable<String>,
        valueConverter: ValueConverter
    ) : ImplementingType(registry, def, typeMap, valueConverter),
        GraphDslSchema.Object,
        CompositeOutput {
        override val name: String = def.name
        override val fields by lazy { extensions.flatMap { it.members } }
        override val unions by lazy { unionNames.map { typeMap[it] as Union } }
        override val extensions by lazy {
            (listOf(def) + extensionDefs).map { gjLangTypeDef ->
                GraphDslSchema.ExtensionWithSupers.of(
                    def = this@Object,
                    memberFactory = { containingExtension ->
                        gjLangTypeDef.fieldDefinitions
                            .filter { it.name != GraphDslSchema.GRAPHDSL_IGNORE_SYMBOL }
                            .map { fieldDefinition ->
                                val ad =
                                    fieldDefinition.directives.toAppliedDirectives(registry, typeMap, valueConverter)
                                OutputField(registry, typeMap, fieldDefinition, containingExtension, ad, valueConverter)
                            }
                    },
                    isBase = gjLangTypeDef == def,
                    appliedDirectives = gjLangTypeDef.directives.toAppliedDirectives(registry, typeMap, valueConverter),
                    supers =
                    gjLangTypeDef.implements.map { typeMap[(it as TypeName).name] as Interface },
                    sourceLocation =
                    gjLangTypeDef.sourceLocation?.sourceName?.let {
                        GraphDslSchema.SourceLocation(it)
                    }
                )
            }
        }
        override val possibleObjectTypes: Set<Object> get() = setOf(this)
    }

    class Input internal constructor(
        private val registry: TypeDefinitionRegistry,
        override val def: InputObjectTypeDefinition,
        override val extensionDefs: List<InputObjectTypeExtensionDefinition>,
        override val typeMap: RawTypeMap,
        valueConverter: ValueConverter
    ) : TypeDefImpl(),
        GraphDslSchema.Input,
        Record {
        override val name: String = def.name
        override val fields by lazy { extensions.flatMap { it.members } }
        override val appliedDirectives = typeMap.collectDirectives(registry, def, extensionDefs, valueConverter)
        override val supers = emptyList<Interface>()
        override val unions = emptyList<Union>()

        private fun createInputField(
            def: InputValueDefinition,
            containingExtension: GraphDslSchema.Extension<Record, Field>,
            appliedDirectives: List<GraphDslSchema.AppliedDirective>,
            valueConverter: ValueConverter
        ): InputField {
            val t = typeMap.toTypeExpr(def.type)
            val default =
                when {
                    def.defaultValue == null -> HasDefaultValue.NO_DEFAULT
                    else -> valueConverter.convert(typeMap.toTypeExpr(def.type), def.defaultValue)
                }
            return InputField(def, t, default, containingExtension, appliedDirectives)
        }

        override val extensions by lazy {
            (listOf(def) + extensionDefs).map { gjLangTypeDef ->
                GraphDslSchema.Extension.of(
                    def = this@Input,
                    memberFactory = { containingExtension ->
                        gjLangTypeDef.inputValueDefinitions.map {
                            val ad = it.directives.toAppliedDirectives(registry, typeMap, valueConverter)
                            createInputField(it, containingExtension, ad, valueConverter)
                        }
                    },
                    isBase = gjLangTypeDef == def,
                    appliedDirectives = gjLangTypeDef.directives.toAppliedDirectives(registry, typeMap, valueConverter),
                    sourceLocation =
                    gjLangTypeDef.sourceLocation?.sourceName?.let {
                        GraphDslSchema.SourceLocation(it)
                    }
                )
            }
        }
        override val possibleObjectTypes: Set<Object> get() = setOf<Object>()
    }

    class TypeExpr internal constructor(
        private val typeMap: RawTypeMap,
        private val baseTypeDefName: String,
        override val baseTypeNullable: Boolean = true, // GraphQL default is types are nullable
        override val listNullable: BitVector = NO_WRAPPERS
    ) : GraphDslSchema.TypeExpr() {
        override val baseTypeDef
            get() =
                typeMap[baseTypeDefName] ?: throw java.lang.IllegalStateException("Bad basetype name: $baseTypeDefName")

        override fun unwrapLists() = TypeExpr(typeMap, baseTypeDefName, baseTypeNullable)

        override fun unwrapList(): TypeExpr? =
            if (listNullable.size == 0) {
                null
            } else {
                TypeExpr(typeMap, baseTypeDefName, baseTypeNullable, listNullable.lsr())
            }
    }
}

internal fun Directive.toAppliedDirective(
    def: DirectiveDefinition,
    valueConverter: ValueConverter,
    typeExprConverter: (Type<*>) -> GraphDslSchema.TypeExpr
): GraphDslSchema.AppliedDirective {
    val args = def.inputValueDefinitions
    return GraphDslSchema.AppliedDirective.of(
        this.name,
        args.fold(mutableMapOf()) { m, arg ->
            val t = typeExprConverter(arg.type)
            val v: Value<*> =
                this.getArgument(arg.name)?.value ?: arg.defaultValue
                ?: NullValue.of().also {
                    if (!t.isNullable) {
                        throw IllegalStateException("No default value for non-nullable argument ${arg.name}")
                    }
                }
            m[arg.name] = valueConverter.convert(t, v)
            m
        }
    )
}

internal fun <T : GraphDslSchema.TypeExpr> Type<*>.toTypeExpr(createTypeExpr: (String, Boolean, BitVector) -> T): T {
    val listNullable = BitVector.Builder()
    var currentNullableBit = 1L
    var t = this
    while (t !is TypeName) {
        when (t) {
            is ListType -> {
                listNullable.add(currentNullableBit, 1)
                currentNullableBit = 1L
                t = t.type
            }

            is NonNullType -> {
                currentNullableBit = 0L
                t = t.type
            }

            else -> {
                throw IllegalStateException("Unexpected GraphQL wrapper $this.")
            }
        }
    }
    return createTypeExpr(t.name, (currentNullableBit == 1L), listNullable.build())
}

private fun RawTypeMap.toTypeExpr(type: Type<*>): GJSchemaRaw.TypeExpr =
    type.toTypeExpr { baseTypeDefName, baseTypeNullable, listNullable ->
        GJSchemaRaw.TypeExpr(this, baseTypeDefName, baseTypeNullable, listNullable)
    }

private fun RawTypeMap.toAppliedDirective(
    registry: TypeDefinitionRegistry,
    dir: Directive,
    valueConverter: ValueConverter
): GraphDslSchema.AppliedDirective {
    val def =
        registry.getDirectiveDefinition(dir.name).orElse(null)
            ?: throw java.lang.IllegalStateException("Directive @${dir.name} not found in schema.")
    return dir.toAppliedDirective(def, valueConverter) { this.toTypeExpr(it) }
}

private fun RawTypeMap.collectDirectives(
    registry: TypeDefinitionRegistry,
    def: DirectivesContainer<*>,
    extensionDefs: List<TypeDefinition<*>>,
    valueConverter: ValueConverter
): List<GraphDslSchema.AppliedDirective> {
    val result = mutableListOf<GraphDslSchema.AppliedDirective>()
    for (directive in def.directives) {
        result.add(this.toAppliedDirective(registry, directive, valueConverter))
    }
    for (extension in extensionDefs) {
        for (directive in extension.directives) {
            result.add(this.toAppliedDirective(registry, directive, valueConverter))
        }
    }
    return result
}


private fun Iterable<Directive>.toAppliedDirectives(
    registry: TypeDefinitionRegistry,
    typeMap: RawTypeMap,
    valueConverter: ValueConverter
) = this.map { typeMap.toAppliedDirective(registry, it, valueConverter) }

private fun InputValueDefinition.defaultValue(
    typeMap: RawTypeMap,
    valueConverter: ValueConverter
) = if (defaultValue == null) {
    GJSchemaRaw.HasDefaultValue.NO_DEFAULT
} else {
    valueConverter.convert(typeMap.toTypeExpr(type), defaultValue)
}