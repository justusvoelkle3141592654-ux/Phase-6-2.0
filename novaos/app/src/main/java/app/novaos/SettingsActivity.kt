package app.novaos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.novaos.ui.common.LocalContainer
import app.novaos.ui.settings.SettingsRoot

/** NovaOS Einstellungen – eigene App mit eigenem Icon (gleiche APK). */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val route = intent.getStringExtra(EXTRA_ROUTE)
        val arg = intent.getStringExtra(EXTRA_ARG)
        setContent {
            CompositionLocalProvider(LocalContainer provides nova) {
                SettingsRoot(route, arg, onExit = { finish() })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        nova.systemInfo.start()
        nova.controls.startListening()
    }

    override fun onStop() {
        super.onStop()
        nova.controls.stopListening()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ARG = "arg"

        fun intent(context: Context, route: String?, arg: String? = null): Intent =
            Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_ROUTE, route)
                .putExtra(EXTRA_ARG, arg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
