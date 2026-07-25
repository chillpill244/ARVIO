package com.arflix.tv.data.repository
import com.arflix.tv.shared.repository.ProfileManager
import com.arflix.tv.shared.repository.AuthRepository


import coil3.ImageLoader
import java.io.File

interface PlatformEnvironment {
    val cacheDir: File
    val screenWidthDp: Int
    val density: Float
    
    val isTvDevice: Boolean
    val coilContext: coil3.PlatformContext
    val imageLoader: coil3.ImageLoader
    
    fun preloadImage(url: String, width: Int? = null, height: Int? = null)
    fun clearImageCache()
    
    fun setDeviceModeOverrideCache(mode: String?)
    
    // Updater
    fun checkSignatureConflict(apkFile: File): String?
    fun canRequestPackageInstalls(): Boolean
    fun launchInstall(apkFile: File)
    fun launchUnknownSourcesSettings()
    
    fun restartApp()
    fun getMemoryStatus(): MemoryStatus
    fun getDeviceIpAddress(): String?
}

data class MemoryStatus(val isLowRamDevice: Boolean, val memoryClass: Int)
