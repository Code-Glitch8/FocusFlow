package com.example.nativebridge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceInputManager(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        preferOffline: Boolean,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        mainHandler.post {
            if (!isAvailable()) {
                onFailure("Speech recognition service is unavailable on this device.")
                return@post
            }
            stopInternal()
            val instance = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = instance
            instance.setRecognitionListener(
                buildListener(
                    onPartial = onPartial,
                    onFinal = onFinal,
                    onFailure = onFailure
                )
            )
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (preferOffline) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            runCatching { instance.startListening(intent) }.onFailure { err ->
                onFailure(err.localizedMessage ?: "Unable to start microphone listener.")
            }
        }
    }

    fun stopListening() {
        mainHandler.post { stopInternal() }
    }

    fun destroy() {
        mainHandler.post { stopInternal() }
    }

    private fun stopInternal() {
        recognizer?.let { current ->
            runCatching { current.stopListening() }
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
        recognizer = null
    }

    private fun buildListener(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onFailure: (String) -> Unit
    ): RecognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                onFinal(text)
            } else {
                onFailure("No speech detected. Try speaking closer to the mic.")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                onPartial(text)
            }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during speech recognition."
                SpeechRecognizer.ERROR_NO_MATCH -> "Could not recognize speech. Please try again."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input heard."
                else -> "Voice capture failed (code $error)."
            }
            onFailure(message)
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
