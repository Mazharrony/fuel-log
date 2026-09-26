package com.fuelexpenselog.app.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

/**
 * The only Storage Access Framework code in the app: a document the user picked, turned into
 * a stream. Everything that reads or writes the bytes is pure and tested against byte arrays;
 * no storage permission, and no documentfile dependency.
 */
class SafGateway(private val resolver: ContentResolver) {

    /** "wt" truncates: overwriting a longer file must not leave its old tail behind. */
    fun openOutput(uri: Uri): OutputStream =
        checkNotNull(resolver.openOutputStream(uri, "wt")) { "Could not open the chosen file for writing." }

    fun openInput(uri: Uri): InputStream =
        checkNotNull(resolver.openInputStream(uri)) { "Could not open the chosen file." }

    fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
