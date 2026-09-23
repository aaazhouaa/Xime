package com.kingzcheung.xime.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val PERMISSION_RECORD_AUDIO = android.Manifest.permission.RECORD_AUDIO
    const val PERMISSION_RECEIVE_SMS = android.Manifest.permission.RECEIVE_SMS
    val PERMISSION_MEDIA_IMAGES = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    const val REQUEST_CODE_RECORD_AUDIO = 1001
    const val REQUEST_CODE_RECEIVE_SMS = 1002

    fun getMediaPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return hasPermission(context, PERMISSION_RECORD_AUDIO)
    }

    fun hasSmsPermission(context: Context): Boolean {
        return hasPermission(context, PERMISSION_RECEIVE_SMS)
    }

    fun hasMediaImagesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasPermission(context, android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)) {
                return true
            }
        }
        return hasPermission(context, PERMISSION_MEDIA_IMAGES)
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 请求任意运行时权限。
     *
     * IME 进程内不能直接弹权限对话框，因此拉起 [com.kingzcheung.xime.MainActivity]
     * 承载 [androidx.activity.result.ActivityResultContracts.RequestPermission]。
     */
    fun requestPermission(context: Context, permission: String) {
        val intent = Intent(context, com.kingzcheung.xime.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("request_permission", permission)
        context.startActivity(intent)
    }

    fun requestRecordAudioPermission(context: Context) {
        requestPermission(context, PERMISSION_RECORD_AUDIO)
    }

    fun requestSmsPermission(context: Context) {
        requestPermission(context, PERMISSION_RECEIVE_SMS)
    }
}