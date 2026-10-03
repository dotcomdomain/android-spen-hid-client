package me.arianb.usb_hid_client.dictation

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class DictationOptions(
    val enabled: Boolean = false,
    val device: String = "gpio_keys",
    val type: Int = 1,
    val code: Int = 703,
    val replacePending: Boolean = true,
    val parallel: Boolean = false,
    val parallelLimit: Int = 2,
)

object DictationPreferences {
    private lateinit var context: Context
    val options = MutableStateFlow(DictationOptions())
    fun initialize(context: Context) {
        this.context = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        options.value = DictationOptions(
            prefs.getBoolean("dictation_enabled", false),
            prefs.getString("dictation_device", "gpio_keys") ?: "gpio_keys",
            prefs.getInt("dictation_type", 1), prefs.getInt("dictation_code", 703),
            prefs.getBoolean("dictation_replace_pending", true),
            prefs.getBoolean("dictation_parallel", false),
            prefs.getInt("dictation_parallel_limit", 2).coerceIn(1, 5),
        )
    }
    fun save(value: DictationOptions) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean("dictation_enabled", value.enabled)
            .putString("dictation_device", value.device)
            .putInt("dictation_type", value.type).putInt("dictation_code", value.code)
            .putBoolean("dictation_replace_pending", value.replacePending)
            .putBoolean("dictation_parallel", value.parallel)
            .putInt("dictation_parallel_limit", value.parallelLimit.coerceIn(1, 5)).apply()
        options.value = value
    }
    fun modelFile(context: Context) = File(context.noBackupFilesDir, "whisper/ggml-base-q5_1.bin")
}

enum class DictationPhase { IDLE, RECORDING, PROCESSING, SENDING, DOWNLOADING, ERROR }
data class DictationStatus(
    val phase: DictationPhase = DictationPhase.IDLE,
    val message: String = "Ready",
    val progress: Float = 0f,
    val modelReady: Boolean = false,
    val indicatorVisible: Boolean = false,
    val overlayVisible: Boolean = false,
)
object DictationState {
    val status = MutableStateFlow(DictationStatus())
}
