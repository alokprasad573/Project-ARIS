package com.aris.assistant

class S2Native {

    companion object {
        init {
            System.loadLibrary("s2native")
        }
    }

    external fun nativeInitialize(
        modelPath: String,
        tokenizerPath: String
    ): Boolean

    external fun nativeSynthesize(
        text: String,
        outputPath: String
    ): Boolean

    external fun nativeRelease()
}