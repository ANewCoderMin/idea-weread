package com.weread.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * 微信读书工具窗口的内容面板：
 * 顶部导航栏（后退/前进/刷新/首页/缩放/阅读模式）+ 中央 JCEF 浏览器。
 * 阅读模式下隐藏导航栏；页面加载失败时切换到错误提示卡片。
 */
class WereadBrowserPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val service = WereadBrowserService.getInstance(project)

    private var readerMode = false

    private val normalBar = buildNormalBar()
    private val readerBar = buildReaderBar()
    private val toolbarCards = JPanel(CardLayout()).apply {
        add(normalBar, "normal")
        add(readerBar, "reader")
    }

    private val browser = service.browser
    private val browserComponent = service.component

    private val content = JPanel(CardLayout())

    init {
        if (browser == null || browserComponent == null) {
            setContent(unsupportedPanel())
        } else {
            content.add(browserComponent, "browser")
            content.add(buildErrorPanel(), "error")
            registerLoadHandler(browser)
            setToolbar(toolbarCards)
            setContent(content)
        }
    }

    // ---------- 工具栏 ----------

    private fun buildNormalBar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
            isOpaque = false
        }
        bar.add(navButton("←", "后退") { service.goBack() })
        bar.add(navButton("→", "前进") { service.goForward() })
        bar.add(navButton("⟳", "刷新") { service.reload() })
        bar.add(navButton("⌂", "首页") { service.loadHome() })
        bar.add(navButton("−", "缩小 (Ctrl/Cmd + -)") { service.zoomOut() })
        bar.add(navButton("100%", "恢复 100% (Ctrl/Cmd + 0)") { service.resetZoom() })
        bar.add(navButton("＋", "放大 (Ctrl/Cmd + +)") { service.zoomIn() })
        bar.add(navButton("⛶", "阅读模式") { toggleReaderMode() })
        return bar
    }

    private fun buildReaderBar(): JPanel {
        val bar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.empty(1)
        }
        bar.add(navButton("✕ 退出阅读模式", "退出阅读模式") { toggleReaderMode() }, BorderLayout.WEST)
        return bar
    }

    private fun navButton(text: String, tooltip: String, onClick: () -> Unit): JButton {
        return JButton(text).apply {
            this.toolTipText = tooltip
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty(4, 8)
            addActionListener { onClick() }
        }
    }

    private fun toggleReaderMode() {
        readerMode = !readerMode
        showCard(toolbarCards, if (readerMode) "reader" else "normal")
    }

    // ---------- 降级 / 错误面板 ----------

    private fun unsupportedPanel(): JPanel {
        val p = JPanel(BorderLayout())
        val label = JBLabel(
            "<html><div style='text-align:center;padding:16px;color:#808080'>" +
                "当前环境不支持 JCEF。<br>请使用自带 JetBrains Runtime 的 IntelliJ IDEA 运行。</div></html>",
            SwingConstants.CENTER
        )
        p.add(label, BorderLayout.CENTER)
        return p
    }

    private fun buildErrorPanel(): JPanel {
        val p = JPanel(BorderLayout())
        val label = JBLabel(
            "<html><div style='text-align:center;padding:16px;'>页面加载失败</div></html>",
            SwingConstants.CENTER
        )
        label.foreground = JBColor(0x808080, 0x8C8C8C)

        val buttons = JPanel(FlowLayout(FlowLayout.CENTER, 8, 8)).apply { isOpaque = false }
        buttons.add(JButton("重新加载").apply {
            addActionListener {
                service.reload()
                showCard(content, "browser")
            }
        })
        buttons.add(JButton("返回首页").apply {
            addActionListener {
                service.loadHome()
                showCard(content, "browser")
            }
        })

        p.add(label, BorderLayout.CENTER)
        p.add(buttons, BorderLayout.SOUTH)
        return p
    }

    private fun registerLoadHandler(browser: com.intellij.ui.jcef.JBCefBrowser) {
        browser.getJBCefClient().getCefClient().addLoadHandler(object : CefLoadHandlerAdapter() {

            override fun onLoadStart(
                browser: CefBrowser,
                frame: CefFrame?,
                transitionType: org.cef.network.CefRequest.TransitionType?
            ) {
                // 主 frame 开始新导航时切回浏览器，清除可能残留的错误卡片
                if (frame != null && !frame.isMain) return
                ApplicationManager.getApplication().invokeLater {
                    showCard(content, "browser")
                }
            }

            override fun onLoadError(
                browser: CefBrowser,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode,
                errorText: String?,
                failedUrl: String?
            ) {
                // 仅处理主 frame 的真实失败；忽略子 frame/iframe 与导航中断(ERR_ABORTED)
                if (frame != null && !frame.isMain) return
                if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
                ApplicationManager.getApplication().invokeLater {
                    showCard(content, "error")
                }
            }
        })
    }

    private fun showCard(panel: JPanel, name: String) {
        (panel.layout as? CardLayout)?.show(panel, name)
    }
}
