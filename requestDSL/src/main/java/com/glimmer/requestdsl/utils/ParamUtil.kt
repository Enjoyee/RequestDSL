package com.glimmer.requestdsl.utils

/**
 * obj转map，只支持一个父类
 */
fun Any.object2Map(hasSuperCls: Boolean = false): HashMap<String, Any> {
    val result = hashMapOf<String, Any>()
    val superFields = if (hasSuperCls) javaClass.superclass?.declaredFields else null
    val fields = if (superFields != null) javaClass.declaredFields.plus(superFields) else javaClass.declaredFields
    fields.forEach {
        it.isAccessible = true
        result[it.name] = it.get(this) ?: ""
    }
    return result
}