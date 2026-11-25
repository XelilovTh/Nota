package com.xelilovth.nota.data.media

import com.xelilovth.nota.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val playbackRepository: PlaybackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null

    private val _remainingSeconds = MutableStateFlow<Long?>(null)
    val remainingSeconds: StateFlow<Long?> = _remainingSeconds.asStateFlow()

    fun start(minutes: Int) {
        cancel()
        if (minutes <= 0) return
        var secondsLeft = minutes * 60L
        _remainingSeconds.value = secondsLeft
        timerJob = scope.launch {
            while (isActive && secondsLeft > 0L) {
                delay(1_000L)
                secondsLeft--
                _remainingSeconds.value = secondsLeft
            }
            if (isActive) {
                _remainingSeconds.value = null
                if (playbackRepository.playbackState.value.isPlaying) {
                    playbackRepository.togglePlayPause()
                }
            }
        }
    }

    fun cancel() {
        timerJob?.cancel()
        timerJob = null
        _remainingSeconds.value = null
    }
}
