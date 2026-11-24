package reverie.egoland

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fgets
import platform.posix.fopen
import platform.posix.getenv

enum class DecorationMode {
    CLIENT,
    SERVER,
    AUTO
}

data class Config(
    val decoration: DecorationMode,
    val autostart: List<String>,
    val xwayland: Boolean
) {
    companion object {
        private fun unquote(value: String): String {
            if (value.length < 2) return value
            val first = value[0]
            val last = value[value.length - 1]
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length - 1)
            }
            return value
        }

        @OptIn(ExperimentalForeignApi::class)
        fun load(path: String? = null): Config {
            val configPath = path ?: getenv("EGOLAND_CONFIG")?.toKString() ?: "egoland.conf"
            var decoration = DecorationMode.CLIENT
            val commands = mutableListOf<String>()
            var xwayland = true

            val file = fopen(configPath, "r") ?: run {
                println("egoland: config $configPath not found, using defaults")
                return Config(decoration, commands, xwayland)
            }

            memScoped {
                val buffer = allocArray<ByteVar>(1024)
                while (fgets(buffer, 1024, file) != null) {
                    val raw = buffer.toKString().trim()
                    if (raw.isEmpty() || raw.startsWith("#")) continue
                    val eq = raw.indexOf('=')
                    if (eq < 0) continue

                    val key = raw.substring(0, eq).trim().lowercase()
                    val value = unquote(raw.substring(eq + 1).trim())
                    when (key) {
                        "decoration" -> decoration = when (value.lowercase()) {
                            "server" -> DecorationMode.SERVER
                            "auto" -> DecorationMode.AUTO
                            else -> DecorationMode.CLIENT
                        }

                        "autostart" -> if (value.isNotEmpty()) commands.add(value)

                        "xwayland" -> xwayland = value.lowercase() != "false"
                    }
                }
            }
            fclose(file)
            return Config(decoration, commands, xwayland)
        }
    }
}
