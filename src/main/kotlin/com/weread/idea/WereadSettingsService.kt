package com.weread.idea

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * 应用级设置服务：持久化插件自身的最小状态。
 *
 * 只记录「上次阅读的 URL」，用于下次打开时恢复阅读位置。
 * 不保存任何账号、Token、阅读内容等信息（登录状态完全由 JCEF Cookie 负责）。
 */
@Service(Service.Level.APP)
@State(name = "WereadSettings", storages = [Storage("idea-weread.xml")])
class WereadSettingsService : PersistentStateComponent<WereadSettingsService.State> {

    class State {
        var lastUrl: String = WereadBrowserService.HOME_URL
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /** 启动时用于恢复的 URL，非法值回退到首页。 */
    val lastUrl: String
        get() = state.lastUrl.takeIf { it.startsWith("https://weread.qq.com") }
            ?: WereadBrowserService.HOME_URL

    /** 仅在仍是微信读书域内时记录，避免把跳转出去的外链当作阅读位置。 */
    fun updateLastUrl(url: String) {
        if (url.startsWith("https://weread.qq.com")) {
            state.lastUrl = url
        }
    }
}
