plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.weread.idea"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
        // 2026.2 起 JCEF 从核心平台拆分，需显式声明依赖
        bundledModule("intellij.platform.ui.jcef")
        bundledModule("intellij.libraries.jcef")
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
        changeNotes = """
            v0.1.0
            - 微信读书网页版内嵌右侧工具窗口
            - 后退 / 前进 / 刷新 / 首页导航
            - 页面缩放（Ctrl/Cmd + +/-/0）
            - Alt/Option + W 快捷开关
            - 阅读模式
        """.trimIndent()
    }

    signing {
        certificateChainFile = file("chain.crt")
        privateKeyFile = file("private.pem")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }
}
