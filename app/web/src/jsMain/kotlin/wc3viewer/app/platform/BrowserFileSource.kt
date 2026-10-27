package wc3viewer.app.platform

import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.dom.DragEvent
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

/** File input and drag-and-drop over the whole page. */
class BrowserFileSource(
    private val onFile: (name: String, bytes: ByteArray) -> Unit,
    private val onDragActive: (Boolean) -> Unit = {},
) {
    // dragenter/dragleave fire for every child element; count depth to know when the drag left the page.
    private var dragDepth = 0


    fun pick() {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".w3g"
        input.onchange = {
            input.files?.get(0)?.let(::read)
        }
        input.click()
    }

    fun installDropTarget() {
        document.addEventListener("dragover", { e -> e.preventDefault() })
        document.addEventListener("dragenter", { e ->
            e.preventDefault()
            if (dragDepth++ == 0) onDragActive(true)
        })
        document.addEventListener("dragleave", {
            if (--dragDepth <= 0) {
                dragDepth = 0
                onDragActive(false)
            }
        })
        document.addEventListener("drop", { e ->
            e.preventDefault()
            dragDepth = 0
            onDragActive(false)
            (e as DragEvent).dataTransfer?.files?.get(0)?.let(::read)
        })
    }

    /** Loads a replay by URL (same origin). */
    fun fetch(url: String) {
        // Empty init object: RequestInit() from kotlinx-browser fills fields with null, which fetch rejects.
        window.fetch(url, js("({})").unsafeCast<org.w3c.fetch.RequestInit>()).then { it.arrayBuffer() }.then { buffer ->
            onFile(url.substringAfterLast('/'), Int8Array(buffer).unsafeCast<ByteArray>())
        }
    }

    private fun read(file: File) {
        val reader = FileReader()
        reader.onload = {
            val buffer = reader.result as ArrayBuffer
            // On Kotlin/JS ByteArray is backed by Int8Array: no copy needed.
            onFile(file.name, Int8Array(buffer).unsafeCast<ByteArray>())
        }
        reader.readAsArrayBuffer(file)
    }
}
