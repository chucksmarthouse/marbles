package com.aboratech.marbles

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    private lateinit var gameView: GameView
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* ignored: a failed/cancelled update just leaves the current version running */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameView = GameView(this)

        val menuButton = Button(this).apply {
            text = "⋮"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 22f
            setOnClickListener { showMenu(it) }
        }

        val root = FrameLayout(this).apply {
            addView(
                gameView,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
            addView(
                menuButton,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.TOP or Gravity.END
                },
            )
        }
        setContentView(root)

        checkForUpdate()
    }

    private fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                AlertDialog.Builder(this)
                    .setTitle("Update available")
                    .setMessage("A new version of Marbles is available. Update now?")
                    .setPositiveButton("Update") { _, _ -> startUpdate(info) }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }
    }

    private fun startUpdate(info: AppUpdateInfo) {
        appUpdateManager.startUpdateFlowForResult(
            info,
            updateResultLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
        )
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_ABOUT, 0, "About")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_ABOUT -> {
                    showAbout()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("About Marbles")
            .setMessage(
                "Marbles v${BuildConfig.VERSION_NAME}\n\n" +
                    "Tilt your phone to roll the marble through the maze, avoiding the holes, to reach the goal.\n\n" +
                    "https://github.com/chucksmarthouse/marbles",
            )
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()

        // An IMMEDIATE update can get stuck mid-flow (e.g. the user backgrounds
        // the app during download) -- resume it if so.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdate(info)
            }
        }
    }

    override fun onPause() {
        gameView.pause()
        super.onPause()
    }

    companion object {
        private const val MENU_ABOUT = 1
    }
}
