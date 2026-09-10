package com.profecuaderno.app.ui

/**
 * Prevents the app lock from being triggered while an Android system activity
 * (such as the document picker) is intentionally open.
 */
object ExternalActivityGuard {
    @Volatile
    var active: Boolean = false
}
