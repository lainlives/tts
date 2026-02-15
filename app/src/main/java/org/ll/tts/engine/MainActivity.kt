@file:OptIn(ExperimentalMaterial3Api::class)

package org.ll.tts.engine

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

const val TAG = "sherpa-onnx-tts-engine"

class MainActivity : ComponentActivity() {

    private lateinit var track: AudioTrack
    private var stopped: Boolean = false
    private var samplesChannel = Channel<FloatArray>()
    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var langDB: LangDB

    override fun onPause() {
        super.onPause()
        samplesChannel.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceHelper = PreferenceHelper(this)
        langDB = LangDB.getInstance(this)
        Migrate.renameModelFolder(this)
        
        // Ensure the bundled model is initialized
        if (!preferenceHelper.isInitFinished() || langDB.allInstalledLanguages.isEmpty()) {
            TtsEngine.copyModelFromAssets(
                context = this,
                assetPath = "model/engUSA",
                lang = "eng",
                country = "USA",
                modelName = "model.onnx",
                type = "vits-piper"
            )
            preferenceHelper.setInitFinished()
        }

        val currentLang = preferenceHelper.getCurrentLanguage()
        if (!currentLang.isNullOrEmpty()) {
            TtsEngine.createTts(this, currentLang)
            initAudioTrack()
            setupDisplay()
        } else {
            // Fallback to initial setup if language registration failed
            preferenceHelper.setCurrentLanguage("eng")
            restart()
        }
    }

    private fun restart() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    private fun setupDisplay() {
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("uTTS Enterprise") },
                                actions = {
                                    IconButton(
                                        onClick = {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/woheller69/ttsengine")))
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Info, 
                                            contentDescription = "Info",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(
                                onClick = {
                                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = "TTS Settings")
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            var sampleText by remember { mutableStateOf(getSampleText(TtsEngine.lang ?: "")) }
                            
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                item {
                                    Text(
                                        "System Controlled Speed enabled.", 
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "Configure speed in Android Settings -> Accessibility -> Text-to-speech output.", 
                                        modifier = Modifier.padding(bottom = 16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                item {
                                    OutlinedTextField(
                                        value = sampleText,
                                        onValueChange = { sampleText = it },
                                        label = { Text(getString(R.string.input)) },
                                        maxLines = 10,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .wrapContentHeight(),
                                        singleLine = false
                                    )
                                }

                                item {
                                    Row {
                                        Button(
                                            modifier = Modifier.padding(5.dp),
                                            onClick = {
                                                if (sampleText.isBlank()) {
                                                    Toast.makeText(applicationContext, getString(R.string.input), Toast.LENGTH_SHORT).show()
                                                } else {
                                                    playTestAudio(sampleText)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_play_24dp), 
                                                contentDescription = stringResource(id = R.string.play),
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(stringResource(id = R.string.play))
                                        }

                                        Button(
                                            modifier = Modifier.padding(5.dp),
                                            onClick = {
                                                stopped = true
                                                track.pause()
                                                track.flush()
                                            }
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_stop_24dp), 
                                                contentDescription = stringResource(id = R.string.stop),
                                                modifier = Modifier.padding(end = 8.dp)
                                            )
                                            Text(stringResource(id = R.string.stop))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playTestAudio(text: String) {
        stopped = false
        track.pause()
        track.flush()
        track.play()

        samplesChannel = Channel<FloatArray>()

        CoroutineScope(Dispatchers.IO).launch {
            for (samples in samplesChannel) {
                for (i in samples.indices) {
                    samples[i] *= TtsEngine.volume.value
                }
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            TtsEngine.tts?.generateWithCallback(
                text = text,
                sid = TtsEngine.speakerId.value,
                speed = TtsEngine.speed.value, // Uses engine default for UI preview
                callback = ::callback,
            )
        }
    }

    override fun onDestroy() {
        if (this::track.isInitialized) track.release()
        super.onDestroy()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun callback(samples: FloatArray): Int {
        if (!stopped) {
            val samplesCopy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                if (!samplesChannel.isClosedForSend) samplesChannel.send(samplesCopy)
            }
            return 1
        } else {
            track.stop()
            return 0
        }
    }

    private fun initAudioTrack() {
        val tts = TtsEngine.tts ?: return
        val sampleRate = tts.sampleRate()
        val bufLength = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        track = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        track.play()
    }
}
