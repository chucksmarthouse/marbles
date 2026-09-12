package com.aboratech.marbles

class Marble(var x: Float, var y: Float, val radius: Float) {
    var vx: Float = 0f
    var vy: Float = 0f

    fun place(newX: Float, newY: Float) {
        x = newX
        y = newY
        vx = 0f
        vy = 0f
    }
}
