package com.example.signtr2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ToggleButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class SignToTextFragment : Fragment() {

    private val nlg = NLG()

    private var tfliteKelime: Interpreter? = null
    private var tfliteHarf: Interpreter? = null
    private lateinit var labelsKelime: List<String>
    private lateinit var labelsHarf: List<String>

    private var holistic: HolisticLandmarker? = null
    private var tts: TextToSpeech? = null

    private val sequence = mutableListOf<FloatArray>()
    private var bosKareSayisi = 0

    private var bakmiyorSayaci = 0
    private var ekranaBakiyor = false

    private var tempWord = ""
    private var frameCounter = 0

    private data class SentenceItem(val text: String, val isHarf: Boolean)
    private val sentenceItems = mutableListOf<SentenceItem>()

    private data class PendingPrediction(
        val topLabels: List<String>,
        val topProbs: List<Float>
    )
    private var pendingKelime: PendingPrediction? = null
    private var latestResult: HolisticLandmarkerResult? = null
    private var skeletonEnabled = true

    private var executor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var tvMod: TextView
    private lateinit var tvTop1: TextView
    private lateinit var tvTop1Click: TextView
    private lateinit var tvTop2Click: TextView
    private lateinit var tvTop3Click: TextView
    private lateinit var tvSentence: TextView
    private lateinit var vStatusLight: View
    private lateinit var ivSkeleton: ImageView
    private lateinit var skeletonContainer: View
    private lateinit var btnSkeletonToggle: ToggleButton
    private lateinit var llRawWords: LinearLayout
    private lateinit var viewFinder: PreviewView

    private val harfInputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(4 * 128 * 128 * 3).order(ByteOrder.nativeOrder())
    private val skeletonPixels = IntArray(128 * 128)

    private val HAND_CONNECTIONS = arrayOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
        Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
        Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12),
        Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16),
        Pair(13, 17), Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20)
    )

    private val POSE_CONNECTIONS = arrayOf(
        Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14),
        Pair(14, 16), Pair(11, 23), Pair(12, 24), Pair(23, 24),
        Pair(0, 11), Pair(0, 12)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_sign_to_text, container, false)

        viewFinder = root.findViewById(R.id.viewFinder)
        tvMod = root.findViewById(R.id.tvMod)
        tvTop1 = root.findViewById(R.id.tvTop1)
        tvTop1Click = root.findViewById(R.id.tvTop1Click)
        tvTop2Click = root.findViewById(R.id.tvTop2Click)
        tvTop3Click = root.findViewById(R.id.tvTop3Click)
        tvSentence = root.findViewById(R.id.tvSentence)
        vStatusLight = root.findViewById(R.id.vStatusLight)
        ivSkeleton = root.findViewById(R.id.ivSkeleton)
        skeletonContainer = root.findViewById(R.id.skeletonContainer)
        btnSkeletonToggle = root.findViewById(R.id.btnSkeletonToggle)
        llRawWords = root.findViewById(R.id.llRawWords)

        tvTop1Click.setOnClickListener { onTopKClicked(0) }
        tvTop2Click.setOnClickListener { onTopKClicked(1) }
        tvTop3Click.setOnClickListener { onTopKClicked(2) }

        btnSkeletonToggle.setOnCheckedChangeListener { _, isChecked ->
            skeletonEnabled = isChecked
            skeletonContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        root.findViewById<Button>(R.id.btnReset).setOnClickListener {
            sentenceItems.clear()
            sequence.clear()
            tempWord = ""
            frameCounter = 0
            bosKareSayisi = 0
            pendingKelime = null
            tvSentence.text = ""
            tvTop1.text = "-"
            tvTop1Click.text = "-"
            tvTop2Click.text = "-"
            tvTop3Click.text = "-"
            llRawWords.removeAllViews()
            tts?.stop()
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        setupTTS()
        if (executor == null) executor = Executors.newSingleThreadExecutor()
        if (holistic == null) setupHolistic()
        if (tfliteKelime == null || tfliteHarf == null) loadTFLiteModels()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.shutdown()
        tts = null
        holistic?.close()
        holistic = null
        tfliteKelime?.close()
        tfliteHarf?.close()
        tfliteKelime = null
        tfliteHarf = null
        executor?.shutdown()
        executor = null
        latestResult = null
    }

    private fun setupTTS() {
        if (tts == null) {
            tts = TextToSpeech(requireContext()) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale("tr", "TR")
            }
        }
    }

    private fun speak(text: String) {
        if (text.isNotBlank()) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun startCamera() {
        val ctx = context ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(executor!!) { proxy -> processImage(proxy) } }

            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analyzer
            )
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val originalBitmap = imageProxy.toBitmap()
        if (originalBitmap != null) {
            val matrix = Matrix().apply {
                postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                postRotate(90f)
            }
            val frameBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, false
            )
            runLocalInference(frameBitmap)
        }
        imageProxy.close()
    }

    private fun runLocalInference(bitmap: Bitmap) {
        val det = holistic ?: return
        val mpImage = BitmapImageBuilder(bitmap).build()
        det.detectAsync(mpImage, System.currentTimeMillis())

        val res = latestResult ?: return

        val poseList = res.poseLandmarks()
        val leftHand = res.leftHandLandmarks()
        val rightHand = res.rightHandLandmarks()
        val faceList = res.faceLandmarks()

        val elVar = (leftHand != null && leftHand.isNotEmpty()) || (rightHand != null && rightHand.isNotEmpty())
        val yakinMi = elYakindaMi(leftHand, rightHand)

        val bakiyor = ekranaBakiyorMu(faceList, res)
        if (bakiyor) {
            bakmiyorSayaci = 0
            ekranaBakiyor = true
        } else {
            bakmiyorSayaci++
            if (bakmiyorSayaci >= 8) ekranaBakiyor = false
        }

        activity?.runOnUiThread {
            if (ekranaBakiyor) {
                vStatusLight.setBackgroundColor(Color.GREEN)
            } else {
                vStatusLight.setBackgroundColor(Color.RED)
            }
        }

        if (elVar && yakinMi) {
            sequence.clear()
            bosKareSayisi = 0
            runHarfInference(extractHarfLandmarks(leftHand, rightHand))
        } else if (elVar && !yakinMi) {
            if (!ekranaBakiyor) {
                sequence.clear()
                bosKareSayisi = 0
                activity?.runOnUiThread { tvMod.text = " " }
                return
            }
            val raw = extractLandmarks(poseList, leftHand, rightHand)
            sequence.add(normalize201(raw))
            bosKareSayisi = 0

            if (skeletonEnabled) {
                val skel = drawFullSkeleton(raw)
                activity?.runOnUiThread { ivSkeleton.setImageBitmap(skel) }
            }

            if (sequence.size >= 40) {
                runKelimeInference()
                sequence.clear()
            }
        } else if (sequence.isNotEmpty()) {
            if (!ekranaBakiyor) {
                sequence.clear()
                bosKareSayisi = 0
                return
            }
            bosKareSayisi++
            if (bosKareSayisi >= 10) {
                runKelimeInference()
                sequence.clear()
                bosKareSayisi = 0
            }
        }
    }

    private fun runKelimeInference() {
        val interp = tfliteKelime ?: return
        if (sequence.isEmpty()) return

        val sampled = Array(30) { FloatArray(201) }
        for (i in 0 until 30) {
            sampled[i] = sequence[i * (sequence.size - 1) / 29]
        }

        val output = Array(1) { FloatArray(labelsKelime.size) }
        interp.run(Array(1) { sampled }, output)
        updateUI(output[0], labelsKelime, isHarf = false)
    }

    private fun runHarfInference(harfData: FloatArray) {
        val interp = tfliteHarf ?: return

        val bitmap = drawHandSkeleton(harfData)
        if (skeletonEnabled) {
            activity?.runOnUiThread { ivSkeleton.setImageBitmap(bitmap) }
        }

        bitmap.getPixels(skeletonPixels, 0, 128, 0, 0, 128, 128)
        harfInputBuffer.clear()
        for (i in skeletonPixels.indices) {
            val px = skeletonPixels[i]
            harfInputBuffer.putFloat(Color.blue(px).toFloat())
            harfInputBuffer.putFloat(Color.green(px).toFloat())
            harfInputBuffer.putFloat(Color.red(px).toFloat())
        }
        if (!skeletonEnabled) bitmap.recycle()

        val output = Array(1) { FloatArray(labelsHarf.size) }
        interp.run(harfInputBuffer, output)
        updateUI(output[0], labelsHarf, isHarf = true)
    }

    private fun updateUI(probabilities: FloatArray, labels: List<String>, isHarf: Boolean) {
        val topIndices = probabilities.indices.sortedByDescending { probabilities[it] }.take(3)
        val topIdx = topIndices.firstOrNull() ?: -1
        val topProb = if (topIdx >= 0) probabilities[topIdx] else 0f

        val topLabels = topIndices.map { labels[it] }
        val topProbs = topIndices.map { probabilities[it] }
        val modText = if (isHarf) "Şu an: HARF" else "Şu an: KELİME"

        if (topIdx == -1 || topProb < 0.70f) {
            if (isHarf) {
                tempWord = ""
                frameCounter = 0
            }
            activity?.runOnUiThread {
                tvMod.text = modText
                tvTop1.text = "Tahmin: -"
                updateTopKButtons(emptyList(), emptyList())
            }
            return
        }

        val bestWord = topLabels[0]
        val bestPct = (topProb * 100).toInt()

        activity?.runOnUiThread {
            tvMod.text = modText
            tvTop1.text = "Tahmin: $bestWord"
            updateTopKButtons(topLabels, topProbs)

            if (isHarf) {
                if (bestWord == tempWord) frameCounter++ else { tempWord = bestWord; frameCounter = 1 }
                if (frameCounter >= 3 && (sentenceItems.isEmpty() || !(sentenceItems.last().isHarf && sentenceItems.last().text == bestWord))) {
                    addItem(SentenceItem(bestWord, isHarf = true))
                    frameCounter = 0
                }
            } else {
                val prev = pendingKelime
                if (prev != null) {
                    val prevTop = prev.topLabels.firstOrNull()
                    if (prevTop != null && (sentenceItems.isEmpty() || sentenceItems.last().text != prevTop || sentenceItems.last().isHarf)) {
                        addItem(SentenceItem(prevTop, isHarf = false))
                    }
                }
                pendingKelime = PendingPrediction(topLabels, topProbs)
            }
        }
    }

    private fun onTopKClicked(idx: Int) {
        val pending = pendingKelime ?: return
        if (idx >= pending.topLabels.size) return
        val chosen = pending.topLabels[idx]

        if (sentenceItems.isNotEmpty() && !sentenceItems.last().isHarf && sentenceItems.last().text == chosen) {
            pendingKelime = null
            return
        }

        addItem(SentenceItem(chosen, isHarf = false))
        pendingKelime = null
    }

    private fun addItem(item: SentenceItem) {
        sentenceItems.add(item)
        refreshRawWordsView()
        refreshSentenceAndSpeak()
    }

    private fun refreshRawWordsView() {
        llRawWords.removeAllViews()
        val density = resources.displayMetrics.density

        sentenceItems.forEachIndexed { index, item ->
            val tv = TextView(requireContext())
            tv.text = if (item.isHarf) "[${item.text}]" else item.text
            tv.setTextColor(Color.WHITE)
            tv.textSize = 14f
            tv.setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            tv.setBackgroundColor(if (item.isHarf) 0xFF4A148C.toInt() else 0xFF1565C0.toInt())

            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            tv.layoutParams = lp
            tv.isClickable = true

            tv.setOnClickListener {
                if (index < sentenceItems.size) {
                    sentenceItems.removeAt(index)
                    refreshRawWordsView()
                    refreshSentenceAndSpeak()
                }
            }
            llRawWords.addView(tv)
        }
    }

    private fun refreshSentenceAndSpeak() {
        if (sentenceItems.isEmpty()) {
            tvSentence.text = ""
            return
        }

        val tokens = mutableListOf<Pair<String, Boolean>>()
        var i = 0
        while (i < sentenceItems.size) {
            val cur = sentenceItems[i]
            if (cur.isHarf) {
                val sb = StringBuilder()
                while (i < sentenceItems.size && sentenceItems[i].isHarf) {
                    sb.append(sentenceItems[i].text)
                    i++
                }
                tokens.add(sb.toString() to true)
            } else {
                tokens.add(cur.text to false)
                i++
            }
        }

        val words = tokens.filter { !it.second }.map { it.first }
        val nlgResult = if (words.isNotEmpty()) nlg.cumleKur(words) else ""
        val harfBloklari = tokens.filter { it.second }.joinToString(" ") { it.first }

        val final = listOf(nlgResult, harfBloklari).filter { it.isNotBlank() }.joinToString(" ")

        tvSentence.text = final
        speak(final)
    }

    private fun updateTopKButtons(topLabels: List<String>, topProbs: List<Float>) {
        val buttons = listOf(tvTop1Click, tvTop2Click, tvTop3Click)
        for (k in buttons.indices) {
            if (k < topLabels.size) {
                val pct = (topProbs[k] * 100).toInt()
                buttons[k].text = "${topLabels[k]}\n"
            } else {
                buttons[k].text = "-"
            }
        }
    }

    private fun kafaYonuTahmini(face: List<NormalizedLandmark>?): Float {
        if (face == null || face.size < 264) return 999f
        val burunX = face[1].x(); val burunY = face[1].y()
        val solX = face[33].x(); val solY = face[33].y()
        val sagX = face[263].x(); val sagY = face[263].y()

        val gozOrtaX = (solX + sagX) / 2f
        val gozGenislik = sqrt(((sagX - solX) * (sagX - solX) + (sagY - solY) * (sagY - solY)).toDouble()).toFloat()
        if (gozGenislik < 1e-6f) return 999f

        val yatay = (burunX - gozOrtaX) / gozGenislik
        return abs(yatay * 90f)
    }

    private fun ekranaBakiyorMu(face: List<NormalizedLandmark>?, res: HolisticLandmarkerResult): Boolean {
        if (face == null || face.isEmpty()) return false
        val derece = kafaYonuTahmini(face)
        if (derece > 25f) return false

        val opt = res.faceBlendshapes()
        if (!opt.isPresent) return true

        val categoriesList = opt.get()
        if (categoriesList.isEmpty()) return true

        val bs = HashMap<String, Float>()
        for (c in categoriesList) {
            bs[c.categoryName()] = c.score()
        }

        val solBlink = bs["eyeBlinkLeft"] ?: 0f
        val sagBlink = bs["eyeBlinkRight"] ?: 0f
        if (solBlink > 0.55f && sagBlink > 0.55f) return false

        val maxBakis = maxOf(
            bs["eyeLookInLeft"] ?: 0f, bs["eyeLookOutLeft"] ?: 0f,
            bs["eyeLookUpLeft"] ?: 0f, bs["eyeLookDownLeft"] ?: 0f,
            bs["eyeLookInRight"] ?: 0f, bs["eyeLookOutRight"] ?: 0f,
            bs["eyeLookUpRight"] ?: 0f, bs["eyeLookDownRight"] ?: 0f
        )

        return maxBakis <= 0.35f
    }

    private fun elYakindaMi(left: List<NormalizedLandmark>?, right: List<NormalizedLandmark>?): Boolean {
        listOfNotNull(left, right).forEach { lms ->
            if (lms.isNotEmpty()) {
                val xs = lms.map { it.x() }
                val ys = lms.map { it.y() }
                val area = (xs.max() - xs.min()) * (ys.max() - ys.min())
                if (area > 0.10f) return true
            }
        }
        return false
    }

    private fun setupHolistic() {
        val ctx = context ?: return
        holistic = HolisticLandmarker.createFromOptions(
            ctx,
            HolisticLandmarker.HolisticLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("holistic_landmarker.task").build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setOutputFaceBlendshapes(true)
                .setResultListener { result, _ -> latestResult = result }
                .build()
        )
    }

    private fun loadTFLiteModels() {
        val ctx = context ?: return
        val load = { assetName: String ->
            val afd = ctx.assets.openFd(assetName)
            val channel = FileInputStream(afd.fileDescriptor).channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            afd.close()
            Interpreter(buffer)
        }
        tfliteKelime = load("en_iyi_model.tflite")
        labelsKelime = ctx.assets.open("labels_kelime.txt").bufferedReader().readLines()
        tfliteHarf = load("harf_modeli.tflite")
        labelsHarf = ctx.assets.open("labels_harf.txt").bufferedReader().readLines()
    }

    private fun extractLandmarks(pose: List<NormalizedLandmark>?, leftHand: List<NormalizedLandmark>?, rightHand: List<NormalizedLandmark>?): FloatArray {
        val data = FloatArray(201)
        pose?.take(25)?.forEachIndexed { i, lm ->
            data[i * 3] = lm.x(); data[i * 3 + 1] = lm.y(); data[i * 3 + 2] = lm.z()
        }
        leftHand?.forEachIndexed { j, lm ->
            data[75 + j * 3] = lm.x(); data[75 + j * 3 + 1] = lm.y(); data[75 + j * 3 + 2] = lm.z()
        }
        rightHand?.forEachIndexed { j, lm ->
            data[138 + j * 3] = lm.x(); data[138 + j * 3 + 1] = lm.y(); data[138 + j * 3 + 2] = lm.z()
        }
        return data
    }

    private fun normalize201(raw: FloatArray): FloatArray {
        val norm = FloatArray(201)
        if (raw.all { it == 0f }) return norm
        val sx = raw[33] - raw[36]
        val sy = raw[34] - raw[37]
        val omuz = max(sqrt((sx * sx + sy * sy).toDouble()).toFloat(), 1e-6f)
        for (i in 0 until 67) {
            norm[i * 3] = (raw[i * 3] - raw[0]) / omuz
            norm[i * 3 + 1] = (raw[i * 3 + 1] - raw[1]) / omuz
            norm[i * 3 + 2] = (raw[i * 3 + 2] - raw[2]) / omuz
        }
        return norm
    }

    private fun extractHarfLandmarks(leftHand: List<NormalizedLandmark>?, rightHand: List<NormalizedLandmark>?): FloatArray {
        val data = FloatArray(126)
        rightHand?.forEachIndexed { j, lm ->
            data[j * 3] = lm.x(); data[j * 3 + 1] = lm.y(); data[j * 3 + 2] = lm.z()
        }
        leftHand?.forEachIndexed { j, lm ->
            data[63 + j * 3] = lm.x(); data[63 + j * 3 + 1] = lm.y(); data[63 + j * 3 + 2] = lm.z()
        }
        return data
    }

    private fun drawHandSkeleton(lms: FloatArray): Bitmap {
        val imgSize = 128
        val padding = imgSize * 0.05f
        val imgScale = imgSize - (2 * padding)

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var hasValidPoint = false

        for (i in 0 until 42) {
            val x = lms[i * 3]; val y = lms[i * 3 + 1]
            if (x != 0f || y != 0f) {
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
                hasValidPoint = true
            }
        }

        val bitmap = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.BLACK) }
        if (!hasValidPoint) return bitmap

        var scaleX = maxX - minX; var scaleY = maxY - minY
        if (scaleX == 0f) scaleX = 1f; if (scaleY == 0f) scaleY = 1f

        val finalCoords = Array(42) { FloatArray(2) { -1f } }
        for (i in 0 until 42) {
            val x = lms[i * 3]; val y = lms[i * 3 + 1]
            if (x != 0f || y != 0f) {
                val scaledX = (x - minX) / scaleX
                val scaledY = (y - minY) / scaleY
                finalCoords[i][0] = scaledX * imgScale + padding
                finalCoords[i][1] = scaledY * imgScale + padding
            }
        }

        val paintRight = Paint().apply { color = Color.GREEN; strokeWidth = 2f; isAntiAlias = true }
        val paintLeft = Paint().apply { color = Color.RED; strokeWidth = 2f; isAntiAlias = true }

        for (conn in HAND_CONNECTIONS) {
            val p1 = finalCoords[conn.first]; val p2 = finalCoords[conn.second]
            if (p1[0] > 0 && p1[1] > 0 && p2[0] > 0 && p2[1] > 0)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintRight)
        }
        for (conn in HAND_CONNECTIONS) {
            val p1 = finalCoords[conn.first + 21]; val p2 = finalCoords[conn.second + 21]
            if (p1[0] > 0 && p1[1] > 0 && p2[0] > 0 && p2[1] > 0)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintLeft)
        }
        return bitmap
    }

    private fun drawFullSkeleton(raw: FloatArray): Bitmap {
        val imgSize = 256
        val padding = imgSize * 0.08f
        val imgScale = imgSize - (2 * padding)

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var hasValid = false

        for (i in 0 until 67) {
            val x = raw[i * 3]; val y = raw[i * 3 + 1]
            if (x != 0f || y != 0f) {
                if (x < minX) minX = x; if (y < minY) minY = y
                if (x > maxX) maxX = x; if (y > maxY) maxY = y
                hasValid = true
            }
        }

        val bitmap = Bitmap.createBitmap(imgSize, imgSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.BLACK) }
        if (!hasValid) return bitmap

        var scaleX = maxX - minX; var scaleY = maxY - minY
        if (scaleX == 0f) scaleX = 1f; if (scaleY == 0f) scaleY = 1f
        val scaleMax = max(scaleX, scaleY)

        val coords = Array(67) { FloatArray(2) { -1f } }
        for (i in 0 until 67) {
            val x = raw[i * 3]; val y = raw[i * 3 + 1]
            if (x != 0f || y != 0f) {
                val sx = (x - minX) / scaleMax
                val sy = (y - minY) / scaleMax
                coords[i][0] = sx * imgScale + padding
                coords[i][1] = sy * imgScale + padding
            }
        }

        val paintPose = Paint().apply { color = Color.YELLOW; strokeWidth = 3f; isAntiAlias = true }
        val paintLeftHand = Paint().apply { color = Color.RED; strokeWidth = 2f; isAntiAlias = true }
        val paintRightHand = Paint().apply { color = Color.GREEN; strokeWidth = 2f; isAntiAlias = true }
        val paintPoint = Paint().apply { color = Color.WHITE; isAntiAlias = true }

        for (conn in POSE_CONNECTIONS) {
            val p1 = coords[conn.first]; val p2 = coords[conn.second]
            if (p1[0] > 0 && p1[1] > 0 && p2[0] > 0 && p2[1] > 0)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintPose)
        }
        for (conn in HAND_CONNECTIONS) {
            val p1 = coords[conn.first + 25]; val p2 = coords[conn.second + 25]
            if (p1[0] > 0 && p1[1] > 0 && p2[0] > 0 && p2[1] > 0)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintLeftHand)
        }
        for (conn in HAND_CONNECTIONS) {
            val p1 = coords[conn.first + 46]; val p2 = coords[conn.second + 46]
            if (p1[0] > 0 && p1[1] > 0 && p2[0] > 0 && p2[1] > 0)
                canvas.drawLine(p1[0], p1[1], p2[0], p2[1], paintRightHand)
        }
        for (i in 0 until 67) {
            if (coords[i][0] > 0 && coords[i][1] > 0)
                canvas.drawCircle(coords[i][0], coords[i][1], 2f, paintPoint)
        }
        return bitmap
    }
}