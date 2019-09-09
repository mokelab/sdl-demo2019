package com.mokelab.demo.sdldemo2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartdevicelink.managers.CompletionListener
import com.smartdevicelink.managers.SdlManager
import com.smartdevicelink.managers.SdlManagerListener
import com.smartdevicelink.managers.file.filetypes.SdlArtwork
import com.smartdevicelink.managers.screen.SoftButtonObject
import com.smartdevicelink.managers.screen.SoftButtonState
import com.smartdevicelink.protocol.enums.FunctionID
import com.smartdevicelink.proxy.RPCNotification
import com.smartdevicelink.proxy.RPCResponse
import com.smartdevicelink.proxy.rpc.*
import com.smartdevicelink.proxy.rpc.enums.AppHMIType
import com.smartdevicelink.proxy.rpc.enums.FileType
import com.smartdevicelink.proxy.rpc.enums.HMILevel
import com.smartdevicelink.proxy.rpc.enums.PredefinedLayout
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener
import com.smartdevicelink.transport.TCPTransportConfig
import java.util.*

class MySDLService : Service() {

    private var sdlManager: SdlManager? = null

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Android 8.0(Oreo API 26) 以降では、通知を出すためのチャネル作成が必要
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SDL Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SDLSample")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("カーナビに接続中")
            .build()

        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (sdlManager == null) {
            this.startSdlManager()
        }
        // 以下、intentの内容に応じた処理を記述
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startSdlManager() {
        // Manticoreを起動する度にポート番号が変わるので、都度変更してください
        val port = 15735
        val ipAddr = "m.sdl.tools"
        val autoReconnect = true

        val transport = TCPTransportConfig(port, ipAddr, autoReconnect)

        // アプリの種類
        val appType = Vector<AppHMIType>()
        appType.add(AppHMIType.MEDIA)

        // アプリアイコン
        val iconFileName = "appIcon.png"
        val appIcon = SdlArtwork(iconFileName, FileType.GRAPHIC_PNG, R.drawable.moke, true)

        // SdlManagerの組み立て
        val appId = "testAppId"
        val appName = "MySDLApp"
        val builder = SdlManager.Builder(this, appId, appName, listener)
        builder.setAppTypes(appType)
        builder.setTransportType(transport)
        builder.setAppIcon(appIcon)
        sdlManager = builder.build()
        sdlManager?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        sdlManager?.dispose()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private val listener = object : SdlManagerListener {

        override fun onStart() {
            Log.v("SDL", "onStart()")
            addHmiStatusListener()
        }

        override fun onDestroy() {
            this@MySDLService.stopSelf()
        }

        override fun onError(info: String, e: Exception) {
            Log.v("SDL", "onError() $info")
        }
    }

    private fun addHmiStatusListener() {
        sdlManager?.addOnRPCNotificationListener(
            FunctionID.ON_HMI_STATUS,
            object : OnRPCNotificationListener() {
                override fun onNotified(notification: RPCNotification?) {
                    val status = notification as OnHMIStatus
                    if (status.hmiLevel == HMILevel.HMI_FULL && status.firstRun) {
                        setLayout()
                    }
                }
            })
    }

    private fun setLayout() {
        val request = SetDisplayLayout()
        request.displayLayout = PredefinedLayout.TEXTBUTTONS_WITH_GRAPHIC.toString()
        request.onRPCResponseListener = object: OnRPCResponseListener() {
            override fun onResponse(correlationId: Int, r: RPCResponse?) {
                val response = r as? SetDisplayLayoutResponse
                if (response == null) {
                    return
                }
                if (response.success) {
                    // レイアウトの変更が成功したので、値をセット
                    showUI()
                } else {
                    Log.v("SetLayout", "onError")
                }
            }
        }
        sdlManager?.sendRPC(request)
    }

    private fun showUI() {
        val manager = sdlManager?.screenManager
        if (manager == null) {
            return
        }

        // 画像の準備
        val fileName = "primary.png"
        val primaryGraphic = SdlArtwork(fileName, FileType.GRAPHIC_PNG, R.drawable.moke, true)

        // ボタンの準備
        val state = SoftButtonState("state1", "ON", null)
        val button = SoftButtonObject("button1", state, object: SoftButtonObject.OnEventListener {
            override fun onEvent(
                softButtonObject: SoftButtonObject?,
                onButtonEvent: OnButtonEvent?
            ) {

            }

            override fun onPress(
                softButtonObject: SoftButtonObject?,
                onButtonPress: OnButtonPress?
            ) {
                Log.v("sdl", "ボタンがタップされたよ")
            }
        })

        // UI更新トランザクションの開始
        manager.beginTransaction()

        manager.textField1 = "Hello SDL world"
        manager.textField2 = "アプリ作ろう"
        manager.primaryGraphic = primaryGraphic
        manager.softButtonObjects = listOf(button)

        manager.commit(object: CompletionListener {
            override fun onComplete(success: Boolean) {
                if (success) {
                    Log.v("showUI", "画面の設定に成功したよ")
                }
            }
        })
    }

    companion object {
        const val CHANNEL_ID = "demoChannel"
    }
}