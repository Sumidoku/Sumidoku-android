package org.sumidoku.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.AndroidEntryPoint
import org.sumidoku.android.feature.reader.ReaderVolumeKeyDispatcher

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* denied is fine: background checks still run silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateCheckWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            SumidokuRoot()
        }
    }

    /**
     * Volume keys are intercepted at the window before the system can turn
     * them into a volume change, so the reader may use the rocker as a page
     * turner while the system volume and its HUD stay untouched. No reader
     * body collects them outside the reader, and the reader only when its
     * switch is on, so the keys keep their normal meaning everywhere else.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderVolumeKeyDispatcher.dispatch(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
