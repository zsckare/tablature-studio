package com.tablaturestudio.playback

import com.tablaturestudio.core.Score

/** EN: Future audio implementation boundary. ES: Contrato para el futuro motor de audio. */
interface PlaybackEngine {
    fun load(score: Score)
    fun play()
    fun pause()
    fun stop()
}
