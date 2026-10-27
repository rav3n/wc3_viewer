package wc3viewer.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array

/**
 * Fonts loaded at runtime. The canvas renderer has no system monospace, so fourcc codes use
 * JetBrains Mono NL (SIL OFL 1.1, see resources/fonts). Until it loads, text falls back to the default font.
 */
object Fonts {
    var mono: FontFamily by mutableStateOf(FontFamily.Monospace)
        private set

    fun load() {
        window.fetch("fonts/JetBrainsMonoNL-Regular.ttf", js("({})").unsafeCast<org.w3c.fetch.RequestInit>())
            .then { it.arrayBuffer() }
            .then { buffer: ArrayBuffer ->
                mono = FontFamily(Font("JetBrainsMonoNL", Int8Array(buffer).unsafeCast<ByteArray>()))
            }
    }
}
