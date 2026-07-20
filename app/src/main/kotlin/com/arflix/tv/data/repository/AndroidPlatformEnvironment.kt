package com.arflix.tv.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import coil3.imageLoader
import coil3.request.ImageRequest
import com.arflix.tv.updater.ApkInstaller
import com.arflix.tv.util.DeviceIpAddress
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.detectDeviceType
import java.io.File

class AndroidPlatformEnvironment(
    private val context: Context
) : PlatformEnvironment {

    override val cacheDir: File
        get() = context.cacheDir
        
    override val screenWidthDp: Int
        get() = context.resources.configuration.screenWidthDp
        
    override val density: Float
        get() = context.resources.displayMetrics.density
        
    override val isTvDevice: Boolean
        get() = detectDeviceType(context) == DeviceType.TV
        
    override val coilContext: coil3.PlatformContext
        get() = context
        
    override val imageLoader: coil3.ImageLoader
        get() = context.imageLoader
        
    override fun preloadImage(url: String, width: Int?, height: Int?) {
        val requestBuilder = ImageRequest.Builder(context).data(url)
        if (width != null && height != null) {
            requestBuilder.size(width, height)
        }
        context.imageLoader.enqueue(requestBuilder.build())
    }
    
    override fun clearImageCache() {
        // Coil3 image loader cache clearance
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }
    
    override fun setDeviceModeOverrideCache(mode: String?) {
        com.arflix.tv.util.setDeviceModeOverrideCache(context, mode)
    }
    
    override fun checkSignatureConflict(apkFile: File): String? {
        return ApkInstaller.checkSignatureConflict(context, apkFile)
    }
    
    override fun canRequestPackageInstalls(): Boolean {
        return ApkInstaller.canRequestPackageInstalls(context)
    }
    
    override fun launchInstall(apkFile: File) {
        ApkInstaller.launchInstall(context, apkFile)
    }
    
    override fun launchUnknownSourcesSettings() {
        ApkInstaller.buildUnknownSourcesSettingsIntent(context)?.let {
            context.startActivity(it)
        }
    }
        
    override fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) {
            context.startActivity(intent)
        }
    }
    
    override fun getMemoryStatus(): MemoryStatus {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return MemoryStatus(am.isLowRamDevice, am.memoryClass)
    }
    
    override fun getDeviceIpAddress(): String? {
        return DeviceIpAddress.get(context)
    }
}
