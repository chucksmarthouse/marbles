package com.aboratech.marbles

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gameView = GameView(this)
        setContentView(gameView)

        UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) { latestVersion ->
            AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage("Version $latestVersion is available. Update now?")
                .setPositiveButton("Update") { _, _ -> UpdateChecker.downloadAndInstall(this) }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        gameView.pause()
        super.onPause()
    }
}
