# WeRead Panel：IntelliJ Platform 2026.3 API 审计

审计日期：2026-10-26（北京时间）。

## 结论与版本边界

已对 Marketplace 更新 `1152116` 的 **WeRead Panel 0.2.5** 重跑 Plugin Verifier 1.411，
在 `IU-263.6800.24` 和 `IU-263.7031.23` 上均复现：
**Compatible、4 个 deprecated、6 个 experimental**。

10 条告警全部来自同一个类 `com.weread.idea.WereadToolWindowFactory`，
对应 5 个 `ToolWindowFactory` 默认方法。每个方法分别产生“覆盖”和“调用”两条告警。
这些方法由 Kotlin 2.4.10 的默认兼容模式生成，不是源码中手写的业务调用。

公开仓库 `main` 的起点是 `73654bba988a01319738a25a17b32d67cc642faf`，仅含 0.1.0。
发布包已包含 0.2.5 的代码排版、JS 桥接和标题栏 Action；这些新增源码没有同步到该仓库。
本次代码修改应用于现有仓库；另用发布包完成只替换工厂类的对照实验，不能把它称为
“从完整 0.2.5 源码执行 Gradle 得到的正式发行版”。本次未合并或发布插件版本。

发布包来源：<https://plugins.jetbrains.com/files/33662/1152116/idea-weread-0.2.5.zip>

发布包 SHA-256：
`07b56f44ae0e50e3eb9261c7c60cf6bf0464117d22ff50e04265793bd993a265`

## 10 条告警的准确定位与分类

所有条目的源码定位均为发布包调试信息中的 `WereadToolWindowFactory.kt:11`，
即类声明行。该位置代表编译器生成的方法，不能解释为源码第 11 行写了 10 次 API 调用。
公开仓库修改前也在第 11 行声明该类。

| 编号 | API / JVM 方法 | Verifier 检测方式 | 分类 | 替代或处理 |
| --- | --- | --- | --- | --- |
| D1 | `ToolWindowFactory.isApplicable(Project)` | 工厂覆盖 deprecated 方法 | 可暂缓；本次消除 | 有自定义条件时覆盖 `suspend isApplicableAsync(Project)`；当前直接继承默认行为 |
| D2 | 同上 | 工厂桥接方法调用接口默认实现 | 可暂缓；本次消除 | 不生成兼容桥接方法 |
| D3 | `ToolWindowFactory.isDoNotActivateOnStart()` | 工厂覆盖 deprecated 方法 | 可暂缓；本次消除 | 有需求时用 XML `doNotActivateOnStart`；当前保持未配置及默认 `false` |
| D4 | 同上 | 工厂桥接方法调用接口默认实现 | 可暂缓；本次消除 | 不生成兼容桥接方法 |
| E1 | `ToolWindowFactory.getAnchor()` | 工厂覆盖 experimental 方法 | 需要确认兼容性；本次消除 | 既有 XML `anchor="right"`，不实现动态 anchor |
| E2 | 同上 | 工厂桥接方法调用接口默认实现 | 需要确认兼容性；本次消除 | 不生成兼容桥接方法 |
| E3 | `ToolWindowFactory.getIcon()` | 工厂覆盖 experimental 方法 | 需要确认兼容性；本次消除 | 既有 XML `icon="/icons/pluginIcon.svg"` |
| E4 | 同上 | 工厂桥接方法调用接口默认实现 | 需要确认兼容性；本次消除 | 不生成兼容桥接方法 |
| E5 | `ToolWindowFactory.manage(ToolWindow, ToolWindowManager, Continuation)` | 工厂覆盖 experimental suspend 方法 | 需要确认兼容性；本次消除 | 当前无自定义管理逻辑，继承默认实现；常规内容初始化仍在 `createToolWindowContent` |
| E6 | 同上 | 工厂桥接方法调用接口默认实现 | 需要确认兼容性；本次消除 | 不生成兼容桥接方法 |

**必须迁移：本组中没有阻断 2026.3 加载的项目。** 两个目标构建均 Compatible，
这两个 deprecated 方法也未在核对的 API 源码中标记 `ScheduledForRemoval`。
“可暂缓”不代表永久忽略；本次已用一个局部改动清除全部 10 条。

`manage` 的 JVM 签名含 `Continuation`，是 Kotlin `suspend` 编译后的形式。
普通反编译器可能隐藏这些生成方法；应使用 `javap -p -c -l` 查看实际字节码。
原始清单见 [verification-2026.3](verification-2026.3/)。

## 已实施的最小修改

只改 `src/main/kotlin/com/weread/idea/WereadToolWindowFactory.kt`：

```kotlin
import kotlin.jvm.JvmDefaultWithoutCompatibility

@JvmDefaultWithoutCompatibility
class WereadToolWindowFactory : ToolWindowFactory {
    // 保留已有 createToolWindowContent 实现
}
```

该注解在 `-jvm-default=enable` 下作用于当前类，阻止生成调用接口默认实现的兼容桥接方法。
当前 Kotlin 2.4.10 默认模式即 `enable`。修改后，该类仅声明构造器和
`createToolWindowContent`，其余行为通过 JVM 继承平台默认方法获得。

影响范围：一个最终实现类的字节码方法集合。工厂全限定类名、XML 注册、工具窗口创建、
浏览器服务、登录、导航、缩放、错误卡片和配置存储均未修改。
0.2.5 对照实验保留标题栏 `WereadCodeLayoutAction`；其内容创建方法在规范化常量池索引后
与原发布包的字节码一致，JAR 中仅 `WereadToolWindowFactory.class` 的内容发生变化。

Kotlin 文档将给既有类添加该注解定义为二进制不兼容变更，因此不能无条件推广至对外 SDK。
这里的工厂是 `final` 的插件内部实现类，仓库未提供供外部继承的工厂 API；本次验证其在声明
最低版本和两个目标版本上的链接兼容性。完整运行行为仍应按下方清单实测。

不建议为本问题改写 JCEF API、添加 5 个手写空 override 或抑制 Verifier 告警。
项目级 `compilerOptions.jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)` 也可影响桥接生成，
但作用于所有类，超过本次最小修改范围；没有采用。

## 验证方法与结果范围

使用 IDE 自带 Kotlin 2.4.10 编译器、JBR 25 和 `-jvm-target 21 -jvm-default=enable`，
针对官方 `IU-263.7031.23` SDK 编译公开仓库的全部 6 个 Kotlin 文件，编译成功。
另外从公开工厂实现及发布包字节码重建 0.2.5 工厂的内容创建方法，保留标题栏 Action，
只添加相同注解后重新编译。未将反编译代码覆盖回仓库。

公开 0.1.0 的插件名称与中文开头的描述会触发最新 Verifier 的结构校验；
仅在本地审计包中规范化名称、描述及版本，未修改仓库 XML。
因此“公开源码审计包通过”不等于原 Gradle 发行包的全部结构校验已经通过。
0.2.5 工厂替换对照包则保持原发布包描述符和全部其他类不变。

原始 `./gradlew compileKotlin --console=plain` 在下载 Gradle 时因网络不可达失败。
本次编译及二进制验证通过独立编译器与 Verifier 完成；没有声称原 Gradle 构建流水线通过。

最终使用隔离缓存，在同一 Verifier 进程中执行 2 个审计包 × 3 个 IDE 的矩阵：

| IDE | 原发布包 0.2.5 | 修改后的公开源码审计包 | 仅替换工厂的 0.2.5 对照包 |
| --- | --- | --- | --- |
| 2026.2 `262.8665.258` | 本次未重跑原包 | Compatible，0 deprecated / 0 experimental | Compatible，0 / 0 |
| 2026.3 `263.6800.24` | Compatible，4 / 6 | Compatible，0 / 0 | Compatible，0 / 0 |
| 2026.3 EAP `263.7031.23` | Compatible，4 / 6 | Compatible，0 / 0 | Compatible，0 / 0 |

6 个最终判定均无 compatibility problem / compatibility warning / internal API 报告。
原始判定见同目录证据文件。API 告警清零要求对应输出中不再出现
`deprecated-usages.txt` / `experimental-api-usages.txt`，并确认没有兼容性问题或缺失依赖。

## 完整源码同步后的验证步骤

1. 在真正的 0.2.5 源码分支给同一个工厂类添加相同注解，保留代码排版标题栏等逻辑。
2. 运行 `./gradlew clean buildPlugin`，使用正常项目 JDK、网络和依赖配置完成正式构建。
   本次不升级 Kotlin、Gradle、IDE 基线，也不修改 `sinceBuild=262`。
3. 用 `javap -p -c -l` 检查最终 JAR 中的工厂：上述 5 个生成方法应消失，
   `createToolWindowContent` 应保留。若仍有桥接，检查 Kotlin 编译模式是否被其他配置覆盖。
4. 对同一个最终 ZIP 验证 2026.2 正式版和两个邮件构建：

```bash
java -Xmx3g -Dplugin.verifier.home.dir=/tmp/weread-verifier-release \
  -jar /path/to/verifier-cli-1.411-all.jar check-plugin \
  build/distributions/<next-version>.zip \
  /path/to/idea-IU-262.8665.258 \
  /path/to/idea-IU-263.6800.24 \
  /path/to/idea-IU-263.7031.23 \
  -verification-reports-dir build/reports/release-api-check -offline
```

并行运行不同 Verifier 进程时使用不同 `plugin.verifier.home.dir`，避免共享解包缓存相互清理。
每次执行使用独立报告目录，保留完整结果；不要仅依据 CLI 退出码认定通过。

5. 在三个目标 IDE 的独立 sandbox 中验证以下行为（本次未执行 UI 实测）：

   - 打开项目首次显示工具窗口；关闭后通过 Alt/Option+W 再次打开。
   - 图标及右侧锚点、首次启动激活行为与原版本一致。
   - 登录二维码、登录恢复、代码排版开关、标题栏 Action、前后翻页正常。
   - 后退、前进、刷新、首页和缩放正常；真实主 frame 失败显示错误卡片。
   - 关闭项目、重开项目及插件停用时无异常，浏览器资源正常释放。

6. 上述正式包通过后再提交 Marketplace，核对新版本验证结果为 Compatible 且告警清零。

## 一手参考

- [Marketplace 原更新页](https://plugins.jetbrains.com/plugin/33662-weread-panel/update/1152116#verification-results)
- [2026.3 EAP 目标源码：ToolWindowFactory](https://github.com/JetBrains/intellij-community/blob/idea/263.7031.23/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowFactory.kt)
- [XML 的 anchor/icon/doNotActivateOnStart 定义](https://github.com/JetBrains/intellij-community/blob/idea/263.7031.23/platform/platform-api/src/com/intellij/openapi/wm/ToolWindowEP.java)
- [Kotlin 注解语义与二进制兼容性说明](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.jvm/-jvm-default-without-compatibility/)
- [Kotlin 默认方法及兼容桥接模式](https://kotlinlang.org/docs/interfaces.html#jvm-default-method-generation-for-interface-functions)
- [Plugin Verifier CLI 与报告格式](https://github.com/JetBrains/intellij-plugin-verifier)
