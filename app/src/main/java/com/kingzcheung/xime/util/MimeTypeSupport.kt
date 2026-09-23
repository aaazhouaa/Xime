package com.kingzcheung.xime.util

/**
 * MIME 类型匹配（纯 Kotlin，无 Android 依赖，便于单元测试直调）。
 *
 * 匹配语义：
 * - 通配符 `星号/星号` 匹配任意类型；
 * - `image/星号` 匹配同主类型（type）的任意子类型；
 * - 其余按字面比较，忽略大小写。
 */
object MimeTypeSupport {

    /** 单个声明类型是否覆盖目标类型。 */
    fun matches(declared: String, mimeType: String): Boolean {
        if (declared == "*/*") return true
        if (declared.equals(mimeType, ignoreCase = true)) return true
        return declared.endsWith("/*") &&
            mimeType.startsWith(declared.removeSuffix("/*"), ignoreCase = true)
    }

    /** 宿主声明的 contentMimeTypes 中是否存在覆盖目标类型的项；null/空数组视为不支持。 */
    fun matchesAny(declaredMimeTypes: Array<String>?, mimeType: String): Boolean {
        if (declaredMimeTypes.isNullOrEmpty()) return false
        return declaredMimeTypes.any { matches(it, mimeType) }
    }
}
