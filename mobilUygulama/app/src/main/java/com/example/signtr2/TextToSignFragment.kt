package com.example.signtr2

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream

class TextToSignFragment : Fragment() {

    private lateinit var etInput: EditText
    private lateinit var btnPlay: Button
    private lateinit var btnMic: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnRestart: Button
    private lateinit var videoView: VideoView
    private lateinit var tvStatus: TextView
    private lateinit var tvWordList: TextView

    private var lemmatizer: TurkishLemmatizer? = null
    private var videoAssets: Set<String> = emptySet()
    private var videoIndex: Map<String, String> = emptyMap()

    private var playlist: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var isPlaying: Boolean = false

    private var speechRecognizer: SpeechRecognizer? = null
    private val RECORD_AUDIO_REQ = 1001

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_text_to_sign, container, false)

        etInput = root.findViewById(R.id.etInput)
        btnPlay = root.findViewById(R.id.btnPlay)
        btnMic = root.findViewById(R.id.btnMic)
        btnPrev = root.findViewById(R.id.btnPrev)
        btnPlayPause = root.findViewById(R.id.btnPlayPause)
        btnNext = root.findViewById(R.id.btnNext)
        btnRestart = root.findViewById(R.id.btnRestart)
        videoView = root.findViewById(R.id.videoView)
        tvStatus = root.findViewById(R.id.tvStatus)
        tvWordList = root.findViewById(R.id.tvWordList)

        btnPlay.setOnClickListener { onPlayClicked() }
        btnMic.setOnClickListener { onMicClicked() }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnPrev.setOnClickListener { jumpTo(currentIndex - 1) }
        btnNext.setOnClickListener { jumpTo(currentIndex + 1) }
        btnRestart.setOnClickListener { jumpTo(0) }

        videoView.setOnCompletionListener {
            if (currentIndex + 1 < playlist.size) {
                jumpTo(currentIndex + 1)
            } else {
                isPlaying = false
                tvStatus.text = "Bitti."
            }
        }
        videoView.setOnErrorListener { _, _, _ ->
            tvStatus.text = "Video açılamadı, sonrakine geçiliyor..."
            if (currentIndex + 1 < playlist.size) {
                jumpTo(currentIndex + 1)
            }
            true
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        if (lemmatizer == null) {
            val labels = requireContext().assets.open("labels_kelime.txt")
                .bufferedReader().readLines()
                .filter { it.isNotBlank() }
            lemmatizer = TurkishLemmatizer(labels)
            buildVideoIndex()
        }
    }

    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) videoView.pause()
        isPlaying = false
        speechRecognizer?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoView.stopPlayback()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun buildVideoIndex() {
        val list = try {
            requireContext().assets.list("videos") ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
        videoAssets = list.toSet()
        val map = mutableMapOf<String, String>()
        for (name in list) {
            val noExt = name.substringBeforeLast('.', name)
            val ascii = TurkishLemmatizer.asciify(noExt)
            map[ascii] = name
        }
        videoIndex = map
    }

    private fun onPlayClicked() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "Önce bir cümle yazın", Toast.LENGTH_SHORT).show()
            return
        }
        startPlaylistFromText(text)
    }

    private fun startPlaylistFromText(text: String) {
        val lem = lemmatizer ?: return
        val rawWords = lem.cumleyiCoz(text)
        val withVideo = rawWords.filter { videoIndex.containsKey(TurkishLemmatizer.asciify(it)) }

        if (rawWords.isEmpty()) {
            tvWordList.text = "Tanınan kelime yok."
            tvStatus.text = ""
            playlist = emptyList()
            return
        }

        val display = rawWords.joinToString(" · ") { w ->
            if (videoIndex.containsKey(TurkishLemmatizer.asciify(w))) w else "($w)"
        }
        tvWordList.text = "Ham: $display"

        if (withVideo.isEmpty()) {
            tvStatus.text = "Hiçbir kelimenin videosu yok."
            playlist = emptyList()
            return
        }

        playlist = withVideo
        currentIndex = 0
        jumpTo(0)
    }

    private fun jumpTo(index: Int) {
        if (playlist.isEmpty()) return
        val safeIdx = index.coerceIn(0, playlist.size - 1)
        currentIndex = safeIdx
        val word = playlist[safeIdx]

        tvStatus.text = "Oynatılıyor: $word  (${safeIdx + 1}/${playlist.size})"

        val ascii = TurkishLemmatizer.asciify(word)
        val assetName = videoIndex[ascii]
        if (assetName == null) {
            if (safeIdx + 1 < playlist.size) jumpTo(safeIdx + 1)
            return
        }

        val cacheFile = copyAssetToCacheIfNeeded("videos/$assetName")
        if (cacheFile == null || !cacheFile.exists() || cacheFile.length() == 0L) {
            tvStatus.text = "Dosya kopyalanamadı: $assetName"
            if (safeIdx + 1 < playlist.size) jumpTo(safeIdx + 1)
            return
        }

        videoView.setOnPreparedListener { mp ->
            mp.setVolume(1f, 1f)
            mp.isLooping = false
            videoView.start()
            isPlaying = true
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }

        videoView.setVideoURI(Uri.fromFile(cacheFile))
        videoView.requestFocus()
    }

    private fun togglePlayPause() {
        if (playlist.isEmpty()) return
        if (videoView.isPlaying) {
            videoView.pause()
            isPlaying = false
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            videoView.start()
            isPlaying = true
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    private fun copyAssetToCacheIfNeeded(assetPath: String): File? {
        return try {
            val ctx = requireContext()
            val outFile = File(ctx.cacheDir, assetPath)
            if (outFile.exists() && outFile.length() > 0) return outFile
            outFile.parentFile?.mkdirs()
            ctx.assets.open(assetPath).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    private fun onMicClicked() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQ)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "Bu cihazda konuşma tanıma yok", Toast.LENGTH_SHORT).show()
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
        }
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Konuşun...")
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { tvStatus.text = "Dinliyor..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "İşleniyor..." }
            override fun onError(error: Int) { tvStatus.text = "Tanıma hatası ($error)" }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()
                if (!best.isNullOrBlank()) {
                    etInput.setText(best)
                    tvStatus.text = ""
                    startPlaylistFromText(best)
                } else {
                    tvStatus.text = "Hiçbir şey duyulamadı"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(requireContext(), "Mikrofon izni verilmedi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}