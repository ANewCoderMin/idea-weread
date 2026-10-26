package com.weread.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlin.jvm.JvmDefaultWithoutCompatibility

/**
 * 注册「微信读书」工具窗口，内容为 [WereadBrowserPanel]。
 * 直接继承平台默认实现，避免生成引用过时或实验 API 的兼容桥接方法。
 */
@JvmDefaultWithoutCompatibility
class WereadToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WereadBrowserPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
