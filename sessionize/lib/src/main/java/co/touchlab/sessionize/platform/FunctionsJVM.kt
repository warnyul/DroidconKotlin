package co.touchlab.sessionize.platform

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.sessionize.touchlab.lib.R
import com.russhwolf.settings.PlatformSettings
import com.russhwolf.settings.Settings
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger


actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual fun <B> backgroundTask(backJob: () -> B, mainJob: (B) -> Unit) {
    AndroidAppContext.backgroundTask(backJob, mainJob)
}

private val btfHandler = Handler(Looper.getMainLooper())

internal actual fun <B> backToFront(b: () -> B, job: (B) -> Unit) {
    btfHandler.post { job(b()) }
}

internal actual val mainThread: Boolean
    get() = Looper.getMainLooper() === Looper.myLooper()

object AndroidAppContext {
    lateinit var app: Application

    val executor = Executors.newSingleThreadExecutor()
    val networkExecutor = Executors.newSingleThreadExecutor()
    val handler = Handler(Looper.getMainLooper())

    fun <B> backgroundTask(backJob: () -> B, mainJob: (B) -> Unit) {
        executor.execute {
            val aref = AtomicReference<B>()
            try {
                aref.set(backJob())
                handler.post {
                    mainJob(aref.get())
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }

        }
    }

    fun backgroundTask(backJob: () -> Unit) {
        backgroundTaskRun(backJob, executor)
    }

    fun networkBackgroundTask(backJob: () -> Unit) {
        backgroundTaskRun(backJob, networkExecutor)
    }

    private fun backgroundTaskRun(backJob: () -> Unit, executor: ExecutorService) {
        executor.execute {
            try {
                backJob()
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

}

actual fun logException(t: Throwable) {
    t.printStackTrace()
}

actual fun settingsFactory(): Settings.Factory = PlatformSettings.Factory(AndroidAppContext.app)

actual fun createUuid(): String = UUID.randomUUID().toString()

actual fun createLocalNotification(title:String, message:String) {
    createNotificationChannel()

    val channelId = AndroidAppContext.app.getString(R.string.notification_channel_id)
    var builder = NotificationCompat.Builder(AndroidAppContext.app, channelId)
            .setSmallIcon(R.drawable.notification_tile_bg)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    with(NotificationManagerCompat.from(AndroidAppContext.app)) {
        // notificationId is a unique int for each notification that you must define
        this.notify(NotificationID.id, builder.build())
    }
}

private fun createNotificationChannel() {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = AndroidAppContext.app.getString(R.string.notification_channel_name)
        val descriptionText = AndroidAppContext.app.getString(R.string.notification_channel_description)
        val channelId = AndroidAppContext.app.getString(R.string.notification_channel_id)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, name, importance).apply {
            description = descriptionText
        }

        // Register the channel with the system
        val notificationManager: NotificationManager = AndroidAppContext.app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager


        if(!notificationManager.notificationChannels.contains(channel)) {
            notificationManager.createNotificationChannel(channel)
        }
    }
}

object NotificationID {
    private val c = AtomicInteger(0)
    val id: Int
        get() = c.incrementAndGet()
}