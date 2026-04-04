package com.edgeai.app.ml

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.edgeai.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaModelManager @Inject constructor(
    private val context: Context,
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "GemmaModelManager"
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotLoaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var _modelPath: String? = null
    val modelPath: String? get() = _modelPath

    private var _engine: Engine? = null
    val engine: Engine? get() = _engine

    suspend fun discoverModel(): String? = withContext(Dispatchers.IO) {
        val searchDirs = buildList {
            // App internal storage (no permissions needed)
            add(context.filesDir)
            for (subDir in config.modelConfig.searchDirectories) {
                add(File(context.filesDir, subDir))
            }
            // External storage (needs permissions on API 30+)
            try {
                val extRoot = Environment.getExternalStorageDirectory()
                for (subDir in config.modelConfig.searchDirectories) {
                    add(File(extRoot, subDir))
                }
                add(extRoot)
            } catch (e: Exception) {
                Log.w(TAG, "External storage not accessible: ${e.message}")
            }
            // Emulator-friendly: /data/local/tmp (world-readable)
            add(File("/data/local/tmp"))
        }

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            // Try listing directory
            val found = dir.listFiles()?.firstOrNull { file ->
                file.isFile && isModelFile(file.name)
            }
            if (found != null) {
                Log.i(TAG, "Model discovered at: ${found.absolutePath}")
                return@withContext found.absolutePath
            }
        }

        // Direct file check for known model filenames (when dir listing is not allowed)
        val knownNames = listOf(
            "gemma-4-E2B-it.litertlm",
            "gemma4-E2B-it.litertlm",
            "gemma-3-E4B-it.litertlm",
        )
        val directCheckDirs = searchDirs + listOf(File("/data/local/tmp"))
        for (dir in directCheckDirs) {
            for (name in knownNames) {
                val file = File(dir, name)
                if (file.exists() && file.isFile && file.canRead()) {
                    Log.i(TAG, "Model found via direct check: ${file.absolutePath}")
                    return@withContext file.absolutePath
                }
            }
        }

        Log.w(TAG, "No model file found in any search directory")
        null
    }

    suspend fun loadModel(path: String? = null): Result<String> = withContext(Dispatchers.IO) {
        _state.value = ModelState.Loading

        try {
            val modelPath = path ?: discoverModel()

            if (modelPath == null) {
                val error = "No Gemma model found. Push a .litertlm file to /sdcard/Download/"
                _state.value = ModelState.Error(error)
                return@withContext Result.failure(IllegalStateException(error))
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                val error = "Model file does not exist: $modelPath"
                _state.value = ModelState.Error(error)
                return@withContext Result.failure(IllegalStateException(error))
            }

            Log.i(TAG, "Loading model from: $modelPath (${modelFile.length() / 1_048_576}MB)")

            val isEmulator = android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.FINGERPRINT.contains("emulator")
                || android.os.Build.FINGERPRINT.contains("sdk_gphone")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK")
                || android.os.Build.MODEL.contains("sdk_gphone")
                || android.os.Build.HARDWARE == "ranchu"
                || android.os.Build.HARDWARE == "goldfish"
                || System.getProperty("ro.kernel.qemu") == "1"

            val backend = if (isEmulator) Backend.CPU() else Backend.GPU()
            val visionBackend = if (isEmulator) Backend.CPU() else Backend.GPU()

            Log.i(TAG, "Using backend: ${if (isEmulator) "CPU (emulator)" else "GPU"}")

            // Try GPU first on physical devices, fall back to CPU if GPU fails
            val engine = try {
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = backend,
                    visionBackend = visionBackend,
                    maxNumTokens = config.modelConfig.maxTokens,
                    cacheDir = context.cacheDir.absolutePath,
                )
                val eng = Engine(engineConfig)
                eng.initialize()
                Log.i(TAG, "Engine initialized with ${if (isEmulator) "CPU" else "GPU"} backend")
                eng
            } catch (gpuError: Exception) {
                if (!isEmulator) {
                    Log.w(TAG, "GPU backend failed, falling back to CPU: ${gpuError.message}")
                    val cpuConfig = EngineConfig(
                        modelPath = modelPath,
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU(),
                        maxNumTokens = config.modelConfig.maxTokens,
                        cacheDir = context.cacheDir.absolutePath,
                    )
                    val eng = Engine(cpuConfig)
                    eng.initialize()
                    Log.i(TAG, "Engine initialized with CPU fallback backend")
                    eng
                } else {
                    throw gpuError
                }
            }

            _engine = engine
            _modelPath = modelPath
            _state.value = ModelState.Ready

            Log.i(TAG, "Engine initialized successfully")
            Result.success(modelPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            _state.value = ModelState.Error(e.message ?: "Unknown error", e)
            Result.failure(e)
        }
    }

    fun getModelInfo(): ModelInfo? {
        val path = _modelPath ?: return null
        val file = File(path)
        if (!file.exists()) return null

        return ModelInfo(
            fileName = file.name,
            fileSizeMb = file.length() / 1_048_576,
            path = path,
            format = when {
                file.name.endsWith(".litertlm") -> "LiteRT-LM"
                file.name.endsWith(".task") -> "MediaPipe Task"
                file.name.endsWith(".bin") -> "Binary weights"
                else -> "Unknown"
            },
        )
    }

    fun close() {
        _engine?.close()
        _engine = null
        _modelPath = null
        _state.value = ModelState.NotLoaded
    }

    private fun isModelFile(name: String): Boolean {
        val lower = name.lowercase()
        val extensions = config.modelConfig.supportedExtensions
        val prefixes = config.modelConfig.filePrefixes
        return extensions.any { lower.endsWith(it) } &&
            prefixes.any { lower.startsWith(it) }
    }
}

data class ModelInfo(
    val fileName: String,
    val fileSizeMb: Long,
    val path: String,
    val format: String,
)
