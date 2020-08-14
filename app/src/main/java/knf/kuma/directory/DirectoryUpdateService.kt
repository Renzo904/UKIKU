package knf.kuma.directory

import android.app.IntentService
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import knf.kuma.R
import knf.kuma.commons.*
import knf.kuma.database.CacheDB
import knf.kuma.database.dao.AnimeDAO
import knf.kuma.download.foreground
import knf.kuma.pojos.DirectoryPage
import org.jsoup.HttpStatusException
import pl.droidsonroids.jspoon.Jspoon
import kotlin.properties.Delegates

class DirectoryUpdateService : IntentService("Directory re-update") {
    private val CURRENT_TIME = System.currentTimeMillis()
    private var manager: NotificationManager? = null
    private var count = 0
    private var page = 0
    private var maxAnimes = 3200
    private var needCookies by Delegates.notNull<Boolean>()

    private val startNotification: Notification
        get() {
            val notification = NotificationCompat.Builder(this, CHANNEL)
                    .setOngoing(true)
                    .setSubText("Actualizando directorio")
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSmallIcon(R.drawable.ic_dir_update)
                    .setColor(ContextCompat.getColor(this, EAHelper.getThemeColor()))
                    .setWhen(CURRENT_TIME)
            return notification.build()
        }

    override fun onCreate() {
        foreground(NOT_CODE, startNotification)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground(NOT_CODE, startNotification)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        foreground(NOT_CODE, startNotification)
        if (!Network.isConnected) {
            stopSelf()
            cancelForeground()
            return
        }
        isRunning = true
        manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val animeDAO = CacheDB.INSTANCE.animeDAO()
        needCookies = BypassUtil.isCloudflareActive()
        val jspoon = Jspoon.create()
        calculateMax()
        doFullSearch(jspoon, animeDAO)
        cancelForeground()
    }

    private fun calculateMax() {
        noCrash {
            val main = jsoupCookiesDir("https://animeflv.net/browse",needCookies).get()
            val lastPage = main.select("ul.pagination li:matches(\\d+)").last().text().trim().toInt()
            val last = jsoupCookiesDir("https://animeflv.net/browse?page=$lastPage",needCookies).get()
            maxAnimes = (24 * (lastPage - 1)) + last.select("article").size
            Log.e("Updatedir","Max pages = $maxAnimes")
        }
    }

    private fun doFullSearch(jspoon: Jspoon, animeDAO: AnimeDAO) {
        page = 1
        var finished = false
        while (!finished) {
            if (!Network.isConnected) {
                Log.e("Directory Getter", "Processed $page pages before disconnection")
                stopSelf()
                cancelForeground()
                return
            }
            try {
                if (needCookies)
                    Thread.sleep(6000)
                else
                    Thread.sleep(1000)
                val document = jsoupCookiesDir("https://animeflv.net/browse?order=added&page=$page", needCookies).get()
                if (document.select("article").size != 0) {
                    val animeObjects = jspoon.adapter(DirectoryPage::class.java).fromHtml(document.outerHtml()).getAnimesRecreate(jspoon, object : DirectoryPage.UpdateInterface {
                        override fun onAdd() {
                            count++
                            updateNotification()
                        }

                        override fun onError() {
                            Log.e("Directory Getter", "At page: $page")
                        }
                    }, needCookies)
                    if (animeObjects.isNotEmpty())
                        animeDAO.insertAll(animeObjects)
                    page++
                } else {
                    finished = true
                    Log.e("Directory Getter", "Processed ${page - 1} pages")
                }
            } catch (e: HttpStatusException) {
                if (e.statusCode == 403 || e.statusCode == 503) {
                    Log.e("Directory Getter", "Processed $page pages before interrupted")
                    finished = true
                }
            } catch (e: Exception) {
                Log.e("Directory Getter", "Page error: $page | ${e.message}")
                page++
            }

        }
        cancelForeground()
    }

    private fun cancelForeground() {
        isRunning = false
        stopForeground(true)
        manager?.cancel(NOT_CODE)
    }

    private fun updateNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL).apply {
            setOngoing(true)
            priority = NotificationCompat.PRIORITY_MIN
            setSmallIcon(R.drawable.ic_dir_update)
            color = ContextCompat.getColor(this@DirectoryUpdateService, EAHelper.getThemeColor())
            setWhen(CURRENT_TIME)
            setSound(null)
            if (PrefsUtil.collapseDirectoryNotification)
                setSubText("Actualizando directorio: $count/$maxAnimes~")
            else {
                setContentTitle("Actualizando directorio")
                setContentText("Actualizados: $count/$maxAnimes~")
                if (maxAnimes > 0)
                    setProgress(maxAnimes, count, false)
            }
        }
        manager?.notify(NOT_CODE, notification.build())
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        cancelForeground()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        var NOT_CODE = 5599
        var CHANNEL = "directory_update"
        var isRunning = false
            private set

        fun run(context: Context) {
            if (!isRunning)
                ContextCompat.startForegroundService(context, Intent(context, DirectoryUpdateService::class.java))
        }
    }
}
