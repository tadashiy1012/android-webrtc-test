package jp.yama.webrtctest001

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import java.util.*

data class Media(val type: String, val text: String?, val bitmap: Bitmap?) {
    var id: String
    init {
        this.id = UUID.randomUUID().toString()
    }
}