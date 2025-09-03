package com.paradoxcat.waveformtest

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
        uri?.let { viewModel.loadAudioFile(it) }
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
        loadButton.setOnClickListener {
            filePickerLauncher.launch("audio/*")
        }

        playButton.setOnClickListener {
            viewModel.togglePlayPause()
        }

        timelineSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val percentage = progress / 100f
                    viewModel.seekToPercentage(percentage)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        waveformView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val percentage = event.x / view.width
                viewModel.seekToPercentage(percentage)
                true
            } else false
        }
    }


    private fun updateUI(state: WaveformUiState) {
        if (state.isLoading) {
            loadingAnimation.visibility = View.VISIBLE
            loadingAnimation.playAnimation()
            progressBar.visibility = View.GONE
        } else {
            loadingAnimation.visibility = View.GONE
            loadingAnimation.cancelAnimation()
            progressBar.visibility = View.GONE
        }

        loadButton.isEnabled = !state.isLoading
        playButton.isEnabled = state.hasData && !state.isLoading

        playButton.text = if (state.isPlaying) getString(R.string.pause) else getString(R.string.play)

        if (state.hasData) {
            timelineSeekBar.isEnabled = true
            timelineSeekBar.progress = (state.progressPercentage * 100).toInt()
            currentTimeText.text = state.formatCurrentTime()
            totalTimeText.text = state.formatTotalTime()
        } else {
            timelineSeekBar.isEnabled = false
            timelineSeekBar.progress = 0
            currentTimeText.text = getString(R.string.time_zero)
            totalTimeText.text = getString(R.string.time_zero)
        }

        when {
            state.isLoading -> {
                statusText.text = getString(R.string.loading_message)
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
            }

            state.hasError -> {
                statusText.text = state.errorMessage
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

               Handler(Looper.getMainLooper()).postDelayed({
                    viewModel.clearError()
                }, 5000)
            }

            state.hasData -> {
                statusText.text = getString(R.string.file_uploaded, state.fileName)
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

                state.audioData?.let { audioData ->
                    waveformView.setData(audioData)
                }
            }

            else -> {
                statusText.text = getString(R.string.upload_audio_prompt)
                statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }
}





