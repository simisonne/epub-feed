package com.epubfeed

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EpubFeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required once per process before any PDFBox text extraction.
        PDFBoxResourceLoader.init(applicationContext)
    }
}
