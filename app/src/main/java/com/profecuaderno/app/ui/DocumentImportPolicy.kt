package com.profecuaderno.app.ui

object DocumentImportPolicy {
    val mimeTypes = arrayOf(
        "application/pdf",
        "text/csv",
        "text/comma-separated-values",
        "application/csv",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic",
        "image/heif"
    )

    val supportedExtensions = setOf(
        "pdf", "csv", "xls", "xlsx", "doc", "docx", "txt",
        "jpg", "jpeg", "png", "webp", "heic", "heif"
    )

    fun isSupported(fileName: String?): Boolean {
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return ext in supportedExtensions
    }
}
