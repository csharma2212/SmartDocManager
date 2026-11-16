package com.smartdoc.poc

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.FileNotFoundException

class SmartDocumentsProvider : DocumentsProvider() {

    companion object {
        private const val ROOT_ID = "smartdoc_root"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_ID)
        row.add(DocumentsContract.Root.COLUMN_TITLE, "SmartDocManager")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "Categorized Documents")
        row.add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val row = result.newRow()

        if (documentId == ROOT_ID) {
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_ID)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "SmartDocManager")
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, 0L)
        } else {
            val ctx = context ?: return result
            val docs = restoreLibraryDocs(ctx)
            val doc = docs.firstOrNull { it.uri == documentId }
            if (doc != null) {
                val displayName = getEffectiveExportName(doc)
                val mime = if (doc.type.equals("PDF", ignoreCase = true)) "application/pdf" else "image/jpeg"
                row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, doc.uri)
                row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
                row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mime)
                row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, doc.modified)
                row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                row.add(DocumentsContract.Document.COLUMN_SIZE, doc.size)
            }
        }
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val ctx = context ?: return result
        val docs = restoreLibraryDocs(ctx).filter { isCategorizedDoc(it) }

        for (doc in docs) {
            val row = result.newRow()
            val displayName = getEffectiveExportName(doc)
            val mime = if (doc.type.equals("PDF", ignoreCase = true)) "application/pdf" else "image/jpeg"
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, doc.uri)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mime)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, doc.modified)
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, doc.size)
        }
        return result
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("Context is null")
        val docs = restoreLibraryDocs(ctx)
        val doc = docs.firstOrNull { it.uri == documentId }
            ?: throw FileNotFoundException("Document not found in SmartDoc library: $documentId")

        return openFdSafe(ctx, Uri.parse(doc.uri))
    }
}
