package org.ll.tts.engine

import android.content.Context
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object TtsEngine {
    private val ttsCache = mutableMapOf<String, OfflineTts>()
    var tts: OfflineTts? = null

    // https://en.wikipedia.org/wiki/ISO_639-3
    var lang: String? = ""
    var country: String? = ""

    var volume: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speed: MutableState<Float> = mutableFloatStateOf(1.0F)
    var speakerId: MutableState<Int> = mutableIntStateOf(0)

    private var modelName: String = "model.onnx"
    private var acousticModelName: String? = null // for matcha tts
    private var vocoder: String? = null // for matcha tts
    private var voices: String? = null // for kokoro
    private var ruleFsts: String? = null
    private var ruleFars: String? = null
    private var lexicon: String? = null
    private var dataDir: String = "espeak-ng-data"
    private var dictDir: String? = null

    fun getAvailableLanguages(context: Context): ArrayList<String> {
        val langCodes = java.util.ArrayList<String>()
        val db = LangDB.getInstance(context)
        val allLanguages = db.allInstalledLanguages
        for (language in allLanguages) {
            langCodes.add(language.lang)
        }
        return langCodes
    }

    fun createTts(context: Context, language: String) {
        if (tts == null || lang != language) {
            if (ttsCache.containsKey(language)) {
                Log.i(TAG, "From TTS cache: " + language)
                tts = ttsCache[language]
                loadLanguageSettings(context, language)
            } else {
                initTts(context, language)
            }
        }
    }

    private fun loadLanguageSettings(context: Context, language: String) {
        val db = LangDB.getInstance(context)
        val allLanguages = db.allInstalledLanguages
        val currentLanguage = allLanguages.firstOrNull { it.lang == language }
        if (currentLanguage != null) {
            this.lang = language
            this.country = currentLanguage.country
            this.speed.value = currentLanguage.speed
            this.speakerId.value = currentLanguage.sid
            this.volume.value = currentLanguage.volume
            this.modelName = currentLanguage.name
            PreferenceHelper(context).setCurrentLanguage(language)
        }
    }

    fun removeLanguageFromCache(language: String) {
        ttsCache.remove(language)
        Log.i(TAG, "Removed TTS cache for: $language")
        Log.i(TAG, "TTS cache size:"+ ttsCache.size)
    }

    private fun initTts(context: Context, lang: String) {
        Log.i(TAG, "Add to TTS cache: " + lang)

        loadLanguageSettings(context, lang)

        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath

        val modelDir = "$externalFilesDir/$lang$country"

        var newDataDir = ""
        if (dataDir != null) {
            newDataDir = copyDataDir(context, dataDir!!)
        }

        if (dictDir != null) {
            val newDir = copyDataDir(context, dictDir!!)
            dictDir = "$newDir/$dictDir"
            ruleFsts = "$modelDir/phone.fst,$modelDir/date.fst,$modelDir/number.fst"
        }

        Log.i(TAG, "Initializing OfflineTts with modelDir: $modelDir, modelName: $modelName")
        
        val config = getOfflineTtsConfig(
            modelDir = modelDir!!,
            modelName = modelName ?: "model.onnx",
            acousticModelName = acousticModelName ?: "",
            vocoder = vocoder ?: "",
            voices = voices ?: "",
            lexicon = lexicon ?: "",
            dataDir = newDataDir ?: "",
            dictDir = dictDir ?: "",
            ruleFsts = ruleFsts ?: "",
            ruleFars = ruleFars ?: ""
        )

        val configDebugOff = config.copy(  // create a new instance with debug switched off
            model = config.model.copy(debug = false)
        )

        try {
            tts = OfflineTts(assetManager = null, config = configDebugOff)
            ttsCache[lang] = tts!!
            Log.i(TAG, "TTS cache size:"+ ttsCache.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize OfflineTts: ${e.message}")
            throw e
        }
    }

    private fun copyDataDir(context: Context, dataDir: String): String {
        Log.i(TAG, "data dir is $dataDir")
        val newDataDir = context.getExternalFilesDir(null)!!.absolutePath + "/" + dataDir
        copyAssets(context, dataDir)
        Log.i(TAG, "newDataDir: $newDataDir")
        return newDataDir
    }

    private fun copyAssets(context: Context, path: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(path)
            if (assets.isNullOrEmpty()) {
                copyFile(context, path)
            } else {
                val fullPath = "${context.getExternalFilesDir(null)}/$path"
                val dir = File(fullPath)
                dir.mkdirs()
                for (asset in assets) {
                    val p: String = if (path == "") "" else "$path/"
                    copyAssets(context, p + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy assets from $path. $ex")
        }
    }

    private fun copyFile(context: Context, filename: String) {
        try {
            val istream = context.assets.open(filename)
            val newFilename = context.getExternalFilesDir(null)!!.absolutePath + "/" + filename
            val file = File(newFilename)
            if (!file.exists()) {
                val ostream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var read: Int
                while (istream.read(buffer).also { read = it } != -1) {
                    ostream.write(buffer, 0, read)
                }
                istream.close()
                ostream.flush()
                ostream.close()
                Log.d(TAG, "Copied file: $filename to $newFilename")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy file $filename, $ex")
        }
    }

    fun copyModelFromAssets(context: Context, assetPath: String, lang: String, country: String, modelName: String, type: String) {
        val externalFilesDir = context.getExternalFilesDir(null)!!.absolutePath
        val targetDir = "$externalFilesDir/$lang$country"
        
        Log.i(TAG, "Copying model from assets: $assetPath to $targetDir")

        // Copy the model folder from assets to external storage
        copyAssets(context, assetPath, targetDir)

        // Register the model in the database
        val db = LangDB.getInstance(context)
        db.removeLang(lang) // Ensure we don't have duplicate entries
        db.addLanguage(modelName, lang, country, 0, 1.0f, 1.0f, type)

        // Set as current language if none is set
        val prefs = PreferenceHelper(context)
        if (prefs.getCurrentLanguage().isNullOrEmpty()) {
            prefs.setCurrentLanguage(lang)
        }
    }

    private fun copyAssets(context: Context, assetPath: String, targetBaseDir: String) {
        val assets: Array<String>?
        try {
            assets = context.assets.list(assetPath)
            if (assets.isNullOrEmpty()) {
                copyFile(context, assetPath, targetBaseDir)
            } else {
                val dir = File(targetBaseDir)
                if (!dir.exists()) dir.mkdirs()
                for (asset in assets) {
                    val p = if (assetPath == "") "" else "$assetPath/"
                    val t = if (targetBaseDir.endsWith("/")) targetBaseDir else "$targetBaseDir/"
                    copyAssets(context, p + asset, t + asset)
                }
            }
        } catch (ex: IOException) {
            Log.e(TAG, "Failed to copy assets from $assetPath. $ex")
        }
    }

    private fun copyFile(context: Context, filename: String, targetPath: String) {
        try {
            val istream = context.assets.open(filename)
            val file = File(targetPath)
            file.parentFile?.mkdirs()

            if (!file.exists()) {
                val ostream = FileOutputStream(file)
                val buffer = ByteArray(1024)
                var read: Int
                while (istream.read(buffer).also { read = it } != -1) {
                    ostream.write(buffer, 0, read)
                }
                istream.close()
                ostream.flush()
                ostream.close()
                Log.d(TAG, "Copied file: $filename to $targetPath")
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to copy file $filename to $targetPath, $ex")
        }
    }
}
