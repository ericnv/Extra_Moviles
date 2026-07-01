package com.example.extra_navavillareric

import org.schabi.newpipe.extractor.NewPipe

// NewPipe.init() registra un Downloader global de forma estática y solo debe llamarse
// una vez por proceso; esto centraliza esa inicialización perezosa y segura entre hilos.
object NewPipeBootstrap {
    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader.getInstance())
            initialized = true
        }
    }
}
