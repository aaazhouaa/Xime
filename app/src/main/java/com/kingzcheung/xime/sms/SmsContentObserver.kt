package com.kingzcheung.xime.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import com.kingzcheung.xime.util.PermissionHelper

/**
 * 监听系统短信数据库收件箱变动的 ContentObserver。
 *
 * 在很多高版本 Android 系统（如 MIUI/澎湃 OS、EMUI、ColorOS、OriginOS）中，
 * 系统自带的短信应用或手机管家在收到短信广播时会直接拦截/阻断 (abortBroadcast)，
 * 导致第三方的 BroadcastReceiver 根本无法接收到 SMS_RECEIVED 广播。
 *
 * 通过 ContentObserver 监听 content://sms/inbox，并在发生变动时直接异步查询最新一条短信，
 * 只要拥有 SMS 相关权限，就能 100% 毫秒级提取出短信验证码，与广播接收形成双保险。
 */
class SmsContentObserver(
    private val context: Context,
    handler: Handler = Handler(Looper.getMainLooper())
) : ContentObserver(handler) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private val SMS_URI = Uri.parse("content://sms")
        private val SMS_INBOX_URI = Uri.parse("content://sms/inbox")
    }

    private var lastHandledSmsId: Long = -1L

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        val enabled = SettingsPreferences.isSmsCodeEnabled(context)
        val hasPermission = PermissionHelper.hasSmsPermission(context)
        FileLogger.i(TAG, "SmsContentObserver onChange triggered: uri=$uri, enabled=$enabled, hasPermission=$hasPermission")
        if (!enabled) return
        if (!hasPermission) return

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val queryUri = when {
                uri != null && uri.toString().startsWith("content://sms") -> uri
                else -> SMS_INBOX_URI
            }

            // 优先按 queryUri 查，若查不到则兜底查 content://sms 总表
            val cursor = context.contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            ) ?: context.contentResolver.query(
                SMS_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    if (id == lastHandledSmsId) return
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val sender = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""

                    FileLogger.i(TAG, "SmsContentObserver query success: sender=$sender, id=$id, date=$date, bodyLen=${body.length}")

                    // 仅处理最近 3 分钟内的短信
                    val now = System.currentTimeMillis()
                    if (Math.abs(now - date) > 180_000L) {
                        FileLogger.i(TAG, "Skipping outdated SMS: diff=${now - date}ms")
                        return
                    }

                    lastHandledSmsId = id

                    val customRegex = SmsCodePluginConfig.getRegex(context)
                    val code = customRegex?.let { r -> SmsCodeExtractor.extractWithRegex(body, r) }
                        ?: SmsCodeExtractor.extract(body)

                    FileLogger.i(TAG, "SmsContentObserver extracted code: '$code'")
                    if (code != null) {
                        SmsCodeStore.init(context)
                        SmsCodeStore.add(context, code, sender)

                        if (SettingsPreferences.isSmsAutoCopyEnabled(context)) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("sms_code", code))
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            FileLogger.w(TAG, "No permission to query SMS inbox", e)
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to read SMS inbox via ContentObserver", e)
        }
    }
}
