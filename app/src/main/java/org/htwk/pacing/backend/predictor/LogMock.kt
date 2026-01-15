package org.htwk.pacing.backend.predictor


object Log {
    fun i(tag: String, msg: String) {
        println("$tag: $msg")
        //android.util.Log.i(tag, msg)
    }
}
