package com.weread.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.Component

/**
 * Project 级服务：一个 Project 生命周期内只维护一个 JBCefBrowser 实例。
 *
 * 负责浏览器创建 / 销毁、导航、缩放、URL 状态记录。
 * 通过 [Disposable] 随 Project 关闭自动释放，避免 Chromium 子进程残留与内存泄漏。
 */
@Service(Service.Level.PROJECT)
class WereadBrowserService(private val project: Project) : Disposable {

    companion object {
        const val HOME_URL = "https://weread.qq.com/"

        /** CEF 缩放步长：每 ±1.0 约放大 / 缩小 20%。 */
        private const val ZOOM_STEP = 1.0

        fun getInstance(project: Project): WereadBrowserService = project.service()
    }

    /** 懒加载，确保 dispose 时不会因为从未使用而额外创建浏览器。 */
    private val browserLazy = lazy { if (JBCefApp.isSupported()) createBrowser() else null }

    val browser: JBCefBrowser?
        get() = browserLazy.value

    val component: Component?
        get() = browser?.component

    /** 当前地址，供「恢复上次页面」与导航按钮状态使用。 */
    @Volatile
    var currentUrl: String = HOME_URL
        private set

    private fun createBrowser(): JBCefBrowser {
        val settings = service<WereadSettingsService>()
        val initialUrl = settings.lastUrl
        val b = JBCefBrowser(initialUrl)

        // 记录地址变化，用于恢复阅读位置
        b.getJBCefClient().getCefClient().addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onAddressChange(browser: CefBrowser?, frame: CefFrame?, url: String?) {
                if (url != null && url.isNotBlank()) {
                    currentUrl = url
                    settings.updateLastUrl(url)
                }
            }
        })
        return b
    }

    // ---------- 导航 ----------

    fun goBack() {
        browser?.cefBrowser?.goBack()
    }

    fun goForward() {
        browser?.cefBrowser?.goForward()
    }

    fun reload() {
        browser?.cefBrowser?.reload()
    }

    fun loadHome() {
        browser?.loadURL(HOME_URL)
    }

    fun loadURL(url: String) {
        browser?.loadURL(url)
    }

    // ---------- 缩放 ----------

    fun zoomIn() = adjustZoom(ZOOM_STEP)

    fun zoomOut() = adjustZoom(-ZOOM_STEP)

    fun resetZoom() {
        browser?.cefBrowser?.setZoomLevel(0.0)
    }

    private fun adjustZoom(delta: Double) {
        val cef = browser?.cefBrowser ?: return
        cef.setZoomLevel(cef.zoomLevel + delta)
    }

    // ---------- 生命周期 ----------

    override fun dispose() {
        if (!browserLazy.isInitialized()) return
        val b = browserLazy.value ?: return
        runCatching { b.cefBrowser.close(true) }
        Disposer.dispose(b)
    }
}
