package com.example.shortcutter

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class WebViewActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        val targetUrl = intent.getStringExtra("TARGET_URL") ?: ""

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Если при старте страницы сразу прилетел фавикон — сохраняем его
                if (favicon != null && url != null) {
                    saveFaviconAndTryToUpdateShortcut(url, favicon)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                val failingUrl = request?.url?.toString() ?: "заданному адресу"
                val customErrorHtml = """
                    <html>
                    <head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                    <body style='text-align:center; padding-top:80px; font-family:sans-serif; background-color:#0F0F12; color:#E5E7EB;'>
                        <h2 style='color:#EF4444; font-size:22px; font-weight:bold;'>Узел недоступен</h2>
                        <p style='color:#9CA3AF; font-size:14px; margin: 15px 30px;'>Не удалось установить соединение с сервером:<br><b style='color:#6366F1;'>$failingUrl</b></p>
                        <p style='color:#6B7280; font-size:13px; padding:0 20px;'>Проверьте статус хоста или подключение устройства к локальной сети Wi-Fi.</p>
                        <br><br>
                        <button onclick='window.location.reload();' style='padding:14px 35px; border:none; background:linear-gradient(to right, #6366F1, #A855F7); color:white; border-radius:25px; font-size:14px; font-weight:bold; letter-spacing:1px; cursor:pointer; box-shadow: 0 4px 10px rgba(99, 102, 241, 0.3);'>ПОВТОРИТЬ</button>
                    </body>
                    </html>
                """.trimIndent()

                view?.loadDataWithBaseURL(null, customErrorHtml, "text/html", "UTF-8", null)
            }
        }

        // Этот метод запрашивает фавикон у движка WebView
        webView.setWebChromeClient(object : android.webkit.WebChromeClient() {
            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                super.onReceivedIcon(view, icon)
                if (icon != null && view?.url != null) {
                    saveFaviconAndTryToUpdateShortcut(view.url!!, icon)
                }
            }
        })

        if (targetUrl.isNotEmpty()) {
            webView.loadUrl(targetUrl)
        } else {
            finish()
        }
    }

    // Сохранение иконки в скрытую системную папку приложения в .png
    private fun saveFaviconAndTryToUpdateShortcut(url: String, bitmap: Bitmap) {
        try {
            // Генерируем безопасное имя файла на основе хэша ссылки
            val fileName = "favicon_${url.hashCode()}.png"
            val file = File(getExternalFilesDir(null), fileName) // Папка Android/data/com.example.shortcutter/files/ (скрытая)
            
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            // Пытаемся обновить уже созданный ярлык новой скачанной иконкой PNG (для Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = getSystemService(ShortcutManager::class.java)
                if (shortcutManager != null) {
                    val shortcuts = shortcutManager.pinnedShortcuts
                    for (shortcut in shortcuts) {
                        if (shortcut.id == url) {
                            val updatedShortcut = ShortcutInfo.Builder(this, url)
                                .setShortLabel(shortcut.shortLabel ?: "Приложение")
                                .setIcon(Icon.createWithBitmap(bitmap))
                                .setIntent(shortcut.intent)
                                .build()
                            shortcutManager.updateShortcuts(listOf(updatedShortcut))
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
