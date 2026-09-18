package com.aboratech.marbles

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.PopupMenu

class MainActivity : Activity() {
    private lateinit var gameView: GameView

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
    }

    override fun onPause() {
        gameView.pause()
        super.onPause()
    }

    companion object {
        private const val MENU_ABOUT = 1
    }
}
