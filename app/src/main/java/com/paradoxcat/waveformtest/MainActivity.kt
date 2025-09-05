package com.paradoxcat.waveformtest

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView
import com.paradoxcat.waveformtest.view.WaveformSlideBar
import com.paradoxcat.waveformtest.viewmodels.uiStates.WaveformUiState
import com.paradoxcat.waveformtest.viewmodels.uiStates.WaveformViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    private val viewModel: WaveformViewModel by viewModels()

    private lateinit var waveformView: WaveformSlideBar
    private lateinit var playButton: Button
    private lateinit var loadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var debugText: TextView
    private lateinit var timelineSeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var loadingAnimation: LottieAnimationView

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            if (isWavFile(it)) {
                viewModel.loadAudioFile(it)
            } else {
                showInvalidFileDialog()
            }
        }
    }


   override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        observeViewModel()
        setupListeners()
    }


    private fun initViews() {
        waveformView = findViewById(R.id.waveformView)
        playButton = findViewById(R.id.playButton)
        loadButton = findViewById(R.id.loadButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        debugText = findViewById(R.id.debugText)
        timelineSeekBar = findViewById(R.id.timelineSeekBar)
        currentTimeText = findViewById(R.id.currentTimeText)
        totalTimeText = findViewById(R.id.totalTimeText)
        loadingAnimation = findViewById(R.id.loadingAnimation)
    }


    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            updateUI(state)
        }

        viewModel.debugInfo.observe(this) { info ->
            debugText.text = info
        }
    }

    private fun setupListeners() {
        setupButtonListeners()
        setupSeekBarListeners()
        setupWaveformTouchListener()
    }

    private fun setupButtonListeners() {
        loadButton.setOnClickListener { launchFilePicker() }
        playButton.setOnClickListener { viewModel.togglePlayPause() }
    }

    private fun launchFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun isWavFile(uri: Uri): Boolean {
        val fileName = getFileName(uri)
        return fileName.matches(Regex(".*\\.wav$", RegexOption.IGNORE_CASE))
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex) ?: fileName
            }
        }
        return fileName
    }

    private fun showInvalidFileDialog() {
        AlertDialog.Builder(this)
            .setTitle("Invalid File Format")
            .setMessage("Only .wav files are supported. Please select a valid .wav audio file.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun setupSeekBarListeners() {
        timelineSeekBar.setOnSeekBarChangeListener(createTimelineSeekBarListener())
    }

    private fun createTimelineSeekBarListener(): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val percentage = progress / 100f
                    viewModel.seekToPercentage(percentage)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    

    private fun setupWaveformTouchListener() {
        waveformView.setOnTouchListener { view, event ->
            handleWaveformTouch(view, event)
        }
    }

    private fun handleWaveformTouch(view: View, event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && event.pointerCount == 1) {
            val percentage = (event.x / view.width).coerceIn(0f, 1f)
            viewModel.seekToPercentage(percentage)
        }
        return false
    }


    private fun updateUI(state: WaveformUiState) {
        renderLoading(state)
        renderControls(state)
        renderTimeAndSeek(state)
        renderStatus(state)
        renderWaveform(state)
    }

    private fun renderLoading(state: WaveformUiState) {
        val isLoading = state.isLoading
        loadingAnimation.visibility = if (isLoading) View.VISIBLE else View.GONE
        progressBar.visibility = View.GONE
        
        if (isLoading) {
            loadingAnimation.playAnimation()
        } else {
            loadingAnimation.cancelAnimation()
        }
    }

    private fun renderControls(state: WaveformUiState) {
        loadButton.isEnabled = !state.isLoading
        playButton.isEnabled = state.hasData && !state.isLoading
        playButton.text = getPlayButtonText(state.isPlaying)
    }

    private fun getPlayButtonText(isPlaying: Boolean): String {
        return if (isPlaying) getString(R.string.pause) else getString(R.string.play)
    }

    private fun renderTimeAndSeek(state: WaveformUiState) {
        if (state.hasData) {
            setupTimelineForAudioData(state)
        } else {
            resetTimelineForNoData()
        }
    }

    private fun setupTimelineForAudioData(state: WaveformUiState) {
        timelineSeekBar.isEnabled = true
        timelineSeekBar.progress = (state.progressPercentage * 100).toInt()
        currentTimeText.text = state.formatCurrentTime()
        totalTimeText.text = state.formatTotalTime()
        waveformView.setProgress(state.progressPercentage)
    }

    private fun resetTimelineForNoData() {
        timelineSeekBar.isEnabled = false
        timelineSeekBar.progress = 0
        currentTimeText.text = getString(R.string.time_zero)
        totalTimeText.text = getString(R.string.time_zero)
        waveformView.setProgress(0f)
    }

    private fun renderStatus(state: WaveformUiState) {
        when {
            state.isLoading -> showLoadingStatus()
            state.hasError -> showErrorStatus(state.errorMessage)
            state.hasData -> showSuccessStatus(state.fileName)
            else -> showDefaultStatus()
        }
    }

    private fun showLoadingStatus() {
        statusText.text = getString(R.string.loading_message)
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
    }

    private fun showErrorStatus(errorMessage: String?) {
        statusText.text = errorMessage
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        scheduleErrorClear()
    }

    private fun showSuccessStatus(fileName: String?) {
        statusText.text = getString(R.string.file_uploaded, fileName)
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
    }

    private fun showDefaultStatus() {
        statusText.text = getString(R.string.upload_audio_prompt)
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
    }

    private fun scheduleErrorClear() {
        Handler(Looper.getMainLooper()).postDelayed({ viewModel.clearError() }, 5000)
    }

    private fun renderWaveform(state: WaveformUiState) {
        state.audioData?.let { waveformView.setData(it) }
    }
}





