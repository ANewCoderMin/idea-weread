package com.weread.idea

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 页面缩放 Action（放大 / 缩小 / 恢复）。
 *
 * 仅在「微信读书」工具窗口可见时启用，避免与 IDEA 编辑器自身的缩放快捷键冲突。
 */
abstract class WereadZoomAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = isWereadVisible(e.project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        apply(project)
    }

    protected abstract fun apply(project: Project)

    private fun isWereadVisible(project: Project?): Boolean {
        val tw = project?.let { ToolWindowManager.getInstance(it).getToolWindow("Weread") }
        return tw?.isVisible == true
    }
}

class WereadZoomInAction : WereadZoomAction() {
    override fun apply(project: Project) {
        WereadBrowserService.getInstance(project).zoomIn()
    }
}

class WereadZoomOutAction : WereadZoomAction() {
    override fun apply(project: Project) {
        WereadBrowserService.getInstance(project).zoomOut()
    }
}

class WereadZoomResetAction : WereadZoomAction() {
    override fun apply(project: Project) {
        WereadBrowserService.getInstance(project).resetZoom()
    }
}
