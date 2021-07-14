package no.rikstv.exoplayertest

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.ui.DebugTextViewHelper
import com.google.android.exoplayer2.util.EventLogger
import no.rikstv.atv.databinding.ActivityMainBinding

const val TAG = "MainActivity"
const val MANIFEST =
    "" // See slack for ManifestUrl
const val LICENSE =
    "" // See slack for licenseUrl

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null
    private var player: SimpleExoPlayer? = null
    private var debugView: DebugTextViewHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater).apply {
            setContentView(root)
        }

        val mediaItem = MediaItem
            .Builder()
            .setUri(MANIFEST)
            .setDrmUuid(C.WIDEVINE_UUID)
            .setDrmLicenseUri(LICENSE)
            .build()

        initializePlayer(mediaItem)
    }

    private fun releasePlayer() {
        player?.apply {
            debugView?.stop()
            release()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT <= 23) {
            binding?.playerView?.onPause()
        }
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT > 23) {
            binding?.playerView?.onPause()
        }
        releasePlayer()
    }

    private fun initializePlayer(mediaItem: MediaItem) {
        Log.d(TAG, "initializePlayer")
        val trackSelector = CustomTrackSelector(this)
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build().apply {
                playWhenReady = true
                this.addListener(PlayerListener())
                // Filter Logcat by EventLogger to view player specific logs
                this.addAnalyticsListener(EventLogger(trackSelector))
                debugView = DebugTextViewHelper(this, binding!!.debugView)
            }

        player?.setMediaItem(mediaItem)
        player?.prepare()
        binding?.playerView?.player = player
        debugView?.start()
    }
}
