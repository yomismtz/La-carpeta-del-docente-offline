package com.profecuaderno.app.data

/**
 * Deletes a group/period and all records linked to it through SQLite foreign-key cascades.
 * If the deleted group was active, the newest remaining non-archived group becomes active.
 */
fun TeacherDbHelper.deletePeriodCascade(periodId: Long) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val wasActive = db.rawQuery(
            "SELECT active FROM periods WHERE id=?",
            arrayOf(periodId.toString())
        ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

        db.delete("periods", "id=?", arrayOf(periodId.toString()))

        if (wasActive) {
            db.execSQL("UPDATE periods SET active=0")
            db.rawQuery(
                "SELECT id FROM periods WHERE archived=0 ORDER BY id DESC LIMIT 1",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    db.execSQL(
                        "UPDATE periods SET active=1 WHERE id=?",
                        arrayOf(cursor.getLong(0))
                    )
                }
            }
        }

        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
