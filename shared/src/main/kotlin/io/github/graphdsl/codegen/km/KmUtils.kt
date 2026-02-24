// KmToCtUtils.kt are easier to purge of Javassist dependencies, thus more likely
// to be integrated into kotlinx-metadata

package io.github.graphdsl.codegen.km

import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeProjection
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.KmVariance
import kotlinx.metadata.isNullable
import io.github.graphdsl.codegen.ct.isJavaPrimitive
import io.github.graphdsl.codegen.ct.javaTypeName
import io.github.graphdsl.codegen.ct.kmToJvmBoxedName
import io.github.graphdsl.codegen.utils.JavaName
import io.github.graphdsl.codegen.utils.Km
import io.github.graphdsl.codegen.utils.KmName
import io.github.graphdsl.codegen.utils.name

/** Make sure an input type is correct and within the bounds we are currently able
 *  to handle.  null result means things are fine, otherwise a string is returned
 *  that can be used as an exception message.  */
internal fun KmType.isInputTypeFor(returnType: KmType): String? {
    if (arguments.size != returnType.arguments.size) {
        return "Argument counts don't agree (${returnType.arguments.size} != ${arguments.size}"
    }
    val thisC = classifier
    if (thisC !is KmClassifier.Class) {
        return "Currently only support Class classifiers (${thisC::class.simpleName}"
    }
    val returnC = returnType.classifier
    if (returnC !is KmClassifier.Class) {
        return "Currently only support Class classifiers (${returnType.classifier::class.simpleName}"
    }
    if (thisC.name != returnC.name) {
        return "Classifier names don't agree (${returnC.name} != ${thisC.name})."
    }

    arguments.zip(returnType.arguments).forEach { (myArg, returnArg) ->
        val thisT = myArg.type
        val returnT = returnArg.type
        if (thisT == null || returnT == null) {
            return "Argument type can't be null ($returnT, $thisT)."
        }
        thisT.isInputTypeFor(returnT)?.let {
            return ".$it" // number of leading periods tells debugger how deep we went
        }
    }

    return null
}


/** render a KmType into its kotlin code representation */
val KmType.kotlinTypeString: String get() {
    val args = arguments.map {
        if (it == KmTypeProjection.STAR) {
            "*"
        } else {
            val label = when (it.variance) {
                KmVariance.OUT -> "out"
                KmVariance.IN -> "in"
                else -> null
            }
            listOfNotNull(label, it.type?.kotlinTypeString)
                .joinToString(" ")
        }
    }

    val type = this
    return buildString {
        append(type.name.asJavaName)
        if (args.isNotEmpty()) {
            append(args.joinToString(separator = ",", prefix = "<", postfix = ">"))
        }
        if (type.isNullable) {
            append("?")
        }
    }
}

// Property-related utilities

fun getterName(propertyName: String): String = if (startsWithIs(propertyName)) propertyName else "get${propertyName.capitalize()}"

fun setterName(propertyName: String): String {
    val setName = if (startsWithIs(propertyName)) propertyName.drop(2) else propertyName.capitalize()
    return "set$setName"
}

/**
 * There are special property getter / setter naming conventions for properties that start with "is", see:
 * https://kotlinlang.org/docs/java-to-kotlin-interop.html#properties
 */
private fun startsWithIs(s: String): Boolean {
    if (s.length < 3) return false

    // "is" followed by an upper case letter or number
    return s.startsWith("is") && !s[2].isLowerCase()
}
