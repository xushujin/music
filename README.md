# 轻响 · 本地音乐播放器

原生 Android 播放器，Kotlin + Jetpack Compose，**零第三方 UI 框架、零 DI 框架、零数据库**。
只做两件事：**按目录建播放列表** + **同步歌词（全屏 / 换色 / 时间微调）**。

---

## 一、功能清单

### 1. 添加目录 → 自动建播放列表

- 通过系统文件选择器（SAF）选择目录，**不需要申请任何存储权限**
- 播放列表名字 = 目录名字；一个目录 = 一个播放列表
- 支持添加多个目录，递归扫描子目录（最深 8 层）
- 递归时在同级目录里自动配对同名 `.lrc`
- 音频格式：mp3 / m4a / mp4a / aac / flac / wav / wave / ogg / oga / opus / wma / ape / aif / aiff / mka / amr / 3gp / dsf / dff
- 手动「重新扫描」重新遍历目录（不读音频标签，纯列目录，速度取决于文件数）
- **不读任何音频标签**：歌曲标题 = 文件名（去扩展名），艺术家/专辑留空，时长由播放器播放时实时提供

### 2. 收藏

- 播放页顶栏、以及歌曲列表的每一行都有红心，点一下加入 / 移出收藏
- 首页顶部有「收藏」入口（带数量），点进去是收藏列表
- 收藏列表**本身就是一个播放队列**：点任意一首，接着放的就是收藏里的歌
- 收藏只存歌曲 id，所以目录被移除后失效的收藏会**自动从列表里消失**，不会留下死条目
- 排序与目录内一致（按标题）

### 3. 歌词

- 识别同名 `.lrc`（`song.mp3` ↔ `song.lrc`）
- 编码自动识别：UTF-8（含 BOM）/ UTF-16 / **GBK 回退**（中文歌词乱码问题的根因）
- 格式支持：`[mm:ss]` `[mm:ss.x]` `[mm:ss.xx]` `[mm:ss.xxx]` `[mm:ss:xx]`、一行多时间戳、`[ti:] [ar:] [al:] [offset:]`
- **双语歌词合并**：同一时间戳出现两次时，第二行作为翻译显示在原文下方
- 全屏歌词：当前行高亮 + 自动居中滚动，点击任意行跳转播放位置
- 手动拖动时暂停自动滚动 2.5 秒，不跟你抢
- **换颜色**：8 个预设色（墨/黄/粉/青/绿/紫/橙/红），全局生效
- **时间微调**：±0.1 / ±0.5 / ±1.0 秒 + 归零，**按歌曲分别记忆**（不同歌的偏移量不通用）
- 字号可调（14–44sp）
- 歌词是后补的？**进入全屏歌词就会自动重读一次** `.lrc`，不用重扫目录；
  没找到歌词时，页面中间的提示里也有「重新加载歌词」按钮

### 4. 播放

- Media3 ExoPlayer + MediaSessionService：**后台播放 + 系统通知栏控制 + 锁屏控制**（media3 自带，无需手写通知）
  - 注：App 不申请通知权限，Android 13+ 上通知默认不显示，需到系统设置手动打开（见 v1.2 实测）；后台播放本身不受影响
- 播放模式：顺序 / 列表循环 / 单曲循环 / 随机
- **定时退出**：顶栏图标进入设置，预设 15 / 30 / 60 / 90 / 120 分钟，或自定义 1–600 分钟；
  到点自动**停止播放并退出应用**。启用后顶栏图标变成显示剩余时间的小胶囊，可随时取消
- 上一首：播放超过 3 秒时先回到本曲开头（主流播放器习惯）
- 音频焦点处理、拔耳机自动暂停、本地唤醒锁

### 5. 设置与关于

首页右上角齿轮进入。分四块：

- **播放**：启动时恢复上次播放（**只装载、不自动播放**，避免一开 App 就出声）
- **定时退出**：到点动作（退出应用 / 仅暂停）
- **数据**：重新扫描全部目录 / 清空收藏 / 重置歌词时间微调
- **关于**：版本号（读 `BuildConfig.VERSION_NAME`）、一句话说明、Apache-2.0 许可

> **故意没有「深色 / 浅色」切换**：拟态风格依赖"元素与背景同色、靠阴影分层"，
> 深色背景上阴影会糊成一片，所以 App 固定浅色（见「五、设计取舍」）。

---

## 二、工程结构

```
music/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml          # 版本集中管理
├── gradlew / gradlew.bat / gradle/wrapper/
├── LICENSE                            # Apache-2.0
├── .github/workflows/build-apk.yml    # 打 tag 自动编译 + 挂 GitHub Release
├── fdroid/com.azhan.music.yml         # F-Droid 上架配方（需提交到 fdroiddata）
├── dist/                              # 本地交付的 APK（已 gitignore）
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                            # 主题 + 拟态自适应图标（纯矢量，无二进制图）
    │                                   #   底层左上亮/右下暗渐变，上层凸起圆盘 + 强调色音符
    │                                   #   配色取自 Neu.*（#E7EAF0 / #BFC6D4 / #5B6CFF）
    └── java/com/azhan/music/
        ├── MusicApp.kt                 # 手写依赖容器（唯一的"注入"处）
        ├── MainActivity.kt
        ├── data/
        │   ├── Models.kt               # Song / Playlist / LyricPrefs / AppPrefs
        │   ├── MusicStore.kt           # DataStore + JSON 持久化（4 个 key 分开存）
        │   ├── MusicScanner.kt         # SAF 递归遍历 + 按文件名配对歌词
        │   ├── LibraryRepository.kt    # 曲库状态机（StateFlow）+ 收藏
        │   └── LyricPrefsState.kt      # 歌词偏好（Compose state，写入去抖）
        ├── lyrics/
        │   ├── LrcParser.kt            # LRC 解析 + 双语合并
        │   ├── TextDecoder.kt          # 编码嗅探（UTF-8 → GBK）
        │   └── LyricsRepository.kt     # 歌词定位 + LruCache
        ├── player/
        │   ├── PlaybackService.kt      # MediaSessionService
        │   └── PlaybackController.kt   # MediaController → Compose state + 定时退出
        └── ui/
            ├── AppRoot.kt              # 导航（6 个页面，字符串路由）+ 启动恢复上次播放
            ├── theme/Theme.kt          # 固定浅色拟态主题
            ├── theme/Neumorphism.kt    # 拟态设计系统（NeuBox / NeuChip / NeuSwitch / NeuSegmented / NeuSlider）
            ├── common/                 # 时间格式化、歌词记忆、迷你播放条、歌曲行 SongRow
            ├── home/HomeScreen.kt      # 目录列表 + 收藏入口 + 设置入口
            ├── favorites/FavoritesScreen.kt  # 收藏列表
            ├── settings/SettingsScreen.kt    # 全局配置 + 关于
            ├── playlist/PlaylistScreen.kt
            ├── player/PlayerScreen.kt  # 播放页：文件名 + 整块歌词 + 定时退出面板（无封面占位）
            └── lyrics/LyricsScreen.kt  # 全屏歌词 + 控制面板
```

---

## 二·五、实测验证记录

> 这里的 v1.x 是**文档迭代标签**，不等于 APK 的 `versionName`（当前是 `1.2.1` / `versionCode 4`）。

### v1.3～v1.6 · 近期改动（**仅编译验证，未做真机/模拟器复核**）

以下改动均通过 `./gradlew :app:assembleRelease`（BUILD SUCCESSFUL）+ `apksigner` 签名校验，
但**没有在模拟器上做过视觉复核**，请以实际装机效果为准。

| 变更 | 说明 |
|---|---|
| 歌词/歌曲匹配改为纯文件名 | 标题 = 文件名去扩展名，不再读音频标签；删除 `MetadataReader` 与 `meta_cache` |
| 播放页去掉中间封面方块 | 文件名置顶 + 歌词区 `weight(1f)` 占满中间；点歌词区进全屏 |
| 播放页移除「全屏歌词」按钮 | 歌词区本身可点，按钮是重复入口 |
| 全屏歌词避开顶部按钮栏 | 歌词视口顶部预留 72dp，滚动时不再与返回键/标题重叠 |
| 移除顶栏「刷新」按钮 | 改为进入全屏时强制重读 `.lrc`，改完歌词重新进来即可看到 |
| 移除颜色滑条 | 只保留 8 个预设色块（原滑条在白色预设下位置与实际颜色不符） |
| GitHub Actions 自动编译 | **打 tag 才触发** `assembleRelease`；产物挂到 GitHub Release + 上传为 Artifact |
| 新增定时退出 | 顶栏入口；预设 15/30/60/90/120 分钟 + 自定义；到点停止播放并退出 App |
| 新增收藏列表 | 播放页/歌曲行红心；首页收藏入口；收藏列表可作播放队列；失效收藏自动隐藏 |
| 新增设置页 | 全局配置（恢复上次播放 / 定时退出到点动作 / 数据清理）+ 关于；顶栏齿轮进入 |

> 另：不读音频标签后，**列表页在歌曲播放前不显示时长**（时长由播放器播放时实时提供）。

### v1.2 · 去掉通知权限申请

启动时不再弹 `POST_NOTIFICATIONS` 权限框。Android 13+ 上默认不显示播放通知，
但**后台播放照常工作**（没有该权限时 `startForeground()` 仍会成功，只是通知被系统丢掉，不会崩溃）。

在模拟器上撤销权限（`pm revoke`）后冷启动实测：无对话框，`state=PLAYING`，
position 2792ms → 5802ms 正常推进，FATAL 计数 0。

AndroidManifest 里**保留**了权限声明：想要通知栏播放控制的用户可以在
「系统设置 → 通知 → 轻响」手动打开，通知会立刻恢复。

### v1.1 · 拟态（Neumorphism）风格 + 全屏歌词面板默认收起

**在 Android 17 模拟器上重装验证通过。**（装机包路径与体积见下方「三、构建」，此处不再重复。）

| 变更 | 验证 |
|---|---|
| 全部页面换为拟态风格（凸起卡 + 凹陷槽） | 卡片有清晰外光晕、圆形按钮能看出凸起/凹陷、配色与背景同色系 |
| 凹槽 + 凸起滑块的自制滑杆 | 拖动 / 点击跳转都正常 |
| 全屏歌词进入时**不自动弹出**调整窗 | n5 截图：只有顶栏和歌词；n6 截图：点右上「调整」后才展开 |
| 调整窗唤起后仍可在任何空白处点一下收起 | 由现有的「点空白处切换」行为覆盖 |

**实现要点**（踩过坑的细节）：
- Android 硬件加速画布**不支持** `setShadowLayer` / `BlurMaskFilter`（只对文字生效），
  所以拟态阴影是**多层半透明圆角矩形叠加**出来的（10 层递进，每层 alpha 0.05）。
  这是 GPU 安全的，效果可看 dist/screenshots/。
- 自带 Material 的 Slider 在拟态界面里很违和，所以**自己写了一个 NeuSlider**：凹陷轨道 +
  渐变进度 + 凸起圆形滑块，`detectTapGestures` + `detectHorizontalDragGestures` 双重手势。
- 拟态天然依赖"元素与背景同色、靠阴影分层"，深色背景上阴影会糊成一片，所以 App
  **不跟随系统深色模式**，固定浅色。
- 全屏歌词页**也用同一套浅色拟态**（早期版本单独用深色配色，因与播放页观感不一致已改回浅色）。
  歌词文字颜色经 `readableColor()` 保留色相但压暗提饱和，保证在浅底上有足够对比度。

### v1.0 · 完整流程实测

| 验证项 | 结果 |
|---|---|
| 安装 + 启动 | ✅ 无 FATAL，进程存活 |
| 后台播放服务 | ✅ 日志出现 `Background started FGS ... MediaSessionService cmp=com.azhan.music/.player.PlaybackService` |
| 添加目录（SAF） | ✅ 选 `/sdcard/Music` 授权后，自动生成名为 `Music` 的播放列表 |
| 目录扫描 | ✅ 扫到 2 个音频文件（**v1.0 时期会读标签显示时长 00:20；现版本不读标签，列表不再显示时长**） |
| `.lrc` 配对 | ✅ 两首歌列表里都显示「歌词」标记 |
| 播放 | ✅ `playbackState=PLAYING`，position 从 2788ms 正常推进到 5804ms |
| 自动切下一首 | ✅ 第一首播完自动切到第二首 |
| 歌词同步 | ✅ 7 秒时高亮「满天都是小星星」(4.5s)、预告「挂在天上放光明」(8.0s) |
| **GBK 歌词解码** | ✅ GBK 编码的 `.lrc` 正确显示「一群嗜血的蚂蚁」，无乱码 |
| 全屏歌词 | ✅ 沉浸模式生效（系统提示 "Viewing full screen"），当前行高亮居中 |
| 换颜色 | ✅ 点色块后当前行立即变绿，选中态有白边 |
| 时间微调 | ✅ 连点两次 +0.5 → 显示 `+1.0s` |
| 字号调节 | ✅ A+ 后从 22 变 24 |
| **持久化** | ✅ 强杀进程重启后，播放列表与歌词设置都还在（验证 R8 没破坏 kotlinx.serialization） |

---

## 三、构建

> 本机已装好完整工具链（JDK + Android SDK），位于 `~/.android-build/`。
> 下面的命令可以直接复制运行。

### 命令行构建

```bash
cd /Users/wisdom/workbuddy-ai/music

export JAVA_HOME="$HOME/.android-build/jdk/jdk-21.0.12.1+1/Contents/Home"
export ANDROID_HOME="$HOME/.android-build/android-sdk"
export PATH="$JAVA_HOME/bin:$PATH"

# 体积小、运行快的正式包（推荐装机用）
./gradlew :app:assembleRelease

# 或调试包（含完整符号、体积大，便于看崩溃堆栈）
./gradlew :app:assembleDebug
```

产物：

| 包 | 路径 | 体积 | dex |
|---|---|---|---|
| release（已签名，推荐） | `dist/轻响-LightMusic-1.2.1-release.apk` | **2.52 MB** | 1 |
| debug（便于看崩溃堆栈） | `dist/轻响-LightMusic-1.2.1-debug.apk` | 23.7 MB | 15 |

（原始产物也在 `app/build/outputs/apk/{release,debug}/` 下。）

装到手机：

```bash
adb install -r dist/轻响-LightMusic-1.2.1-release.apk
```

也可以直接把 APK 传到手机上点击安装（需要允许"安装未知来源应用"）。

### 签名

release 包用 `keystore/lightmusic-release.jks` 签名：

- alias：`lightmusic`
- store / key 密码：`lightmusic`

**个人自用够用；要上架应用商店请换成你自己的 keystore 并妥善保管**（丢了就没法覆盖升级）。

> 密钥**不入库**：`.gitignore` 已忽略 `keystore/`、`*.jks`、`local.properties`、
> `build/`、`app/build/`、`dist/`、`.workbuddy-ai/`、`*.apk`。

### GitHub Actions 自动编译

仓库自带 `.github/workflows/build-apk.yml`：**打 tag 才触发**（也可在 Actions 页手动触发）。
触发后在 `ubuntu-latest` 上装 JDK 21 + Android SDK（`platforms;android-37.0` / `build-tools;36.0.0`），
执行 `./gradlew assembleRelease`。产物有两个去处：

- **GitHub Release**（仅 tag 构建）：自动创建/更新对应 tag 的 Release，
  并把 APK 作为附件挂上去，文件名形如 `LightMusic-v1.2.1.apk`
  （**附件名必须纯 ASCII**：GitHub 会删掉 Release 资源名里的非 ASCII 字符，
  用中文名会被削成 `-LightMusic-v1.2.1.apk` 这种带前导横线的怪名字）。
  已存在同名 Release 时会**覆盖**附件（`gh release upload --clobber`），可安全重跑。
- **Artifact**（tag 与手动触发都有）：名为 `lightmusic-release-apk`，保留 90 天。

```bash
git tag v1.2.1          # 需以 v 开头，如 v1.2.1 / v1.0.1 / v2.0.0
git push origin v1.2.1  # 推上去即触发编译
```

> 普通 `git push` 到 `main` **不会**触发编译（避免每次提交都跑一次构建）。
> 另注：GitHub 的 `if:` 表达式里不能用 `secrets` 上下文，所以密钥放在 **job 级 `env`**，
> `if` 里判断 `env.KEYSTORE_BASE64`。

**签名密钥是必需的**：CI 与本地必须用**同一把钥匙**，否则产出的 APK 无法覆盖安装
（不同签名的 APK 不能互相覆盖，会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

在仓库 `Settings → Secrets and variables → Actions` 添加 `KEYSTORE_BASE64`：

```bash
base64 -i keystore/lightmusic-release.jks | pbcopy   # 直接进剪贴板，粘到 Secret 里
```

别名与密码**不用配**（工作流默认用 `lightmusic`，与本地一致）；想显式配可再加
`KEY_ALIAS` / `KEYSTORE_PASSWORD` / `KEY_PASSWORD`。

**没配 `KEYSTORE_BASE64` 时工作流会直接失败**——不再用临时密钥签一个装不上去的包。
构建日志里会打印产物的签名指纹（`Show APK signer`），遇到签名冲突对比指纹即可定位。

`app/build.gradle.kts` 的签名来源分三档：

1. 环境变量 `KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`（CI 注入）
2. 仓库内 `keystore/lightmusic-release.jks`（本地构建走这条）
3. **两者都没有时（如 F-Droid 从干净检出构建）→ 不签名**，产出未签名的 release 包，
   而不是让构建失败（F-Droid 会用它自己的密钥重新签名）

### Android Studio

`File → Open` 选这个目录即可。`local.properties` 里已经指向本机 SDK。

### 首次构建耗时

首次约 3–8 分钟（下载依赖），之后增量构建 5–60 秒。

---

## 四、版本矩阵（均为当前稳定版）

| 组件 | 版本 | 说明 |
|---|---|---|
| AGP | 9.4.1 | 需 Gradle ≥ 9.6 |
| Gradle | 9.8.1 | wrapper 已内置 |
| Kotlin | 2.4.21 | **AGP 9 起 Kotlin 支持内建，不再应用 `org.jetbrains.kotlin.android`** |
| Compose BOM | 2026.09.00 | material3 + 图标库 |
| Media3 | 1.11.1 | exoplayer + session |
| DataStore | 1.2.1 | Preferences |
| compileSdk / targetSdk | 37 | 依赖链强制要求（navigation 2.10 / compose 1.12 等 16 个库要求 ≥ 37） |
| minSdk | 26 | Android 8.0+，为了自适应图标与通知渠道 |
| JDK | 21 | AGP 9 最低 17，用 21 更稳 |

> 注：API 37 平台包在 SDK 里的路径是 `platforms;android-37.0`（新版改用主次版本号命名）。

---

## 五、设计取舍（为什么这么写）

**用 SAF 而不是 MediaStore。** MediaStore 是"全机扫描"模型，没法表达"我只要这几个目录"，而且要 `READ_MEDIA_AUDIO` 权限。SAF 拿到的是持久化读权限，目录边界清晰，还能在 Android 14+ 的部分存储限制下正常工作。

**冷启动不扫描。** 扫描结果落盘，启动时只读 JSON。3000 首的曲库启动耗时基本等于读一个几百 KB 的文件。代价是曲库不会自动跟随文件系统变化 —— 所以留了「重新扫描」按钮。

**完全不读音频标签。** 早期版本会用 `MediaMetadataRetriever` 读标题/艺术家/专辑/时长，并做 `uri|size|modified` 缓存；现已全部删除（`MetadataReader` 已移除）。原因：① 逐个开 `MediaMetadataRetriever` 是扫描慢的主要来源；② 歌曲信息与文件名不一致时，歌词配对会产生"看着有歌词却配不上"的困惑。现在的规则非常简单：**一切都以文件名和文件夹名为准** —— 标题 = 文件名去扩展名，`.lrc` 与音频文件同名即配对，播放列表名 = 目录名。代价是列表页在播放前看不到时长（时长改由播放器实时提供）。

**位置刷新用 `derivedStateOf` 收口。** 播放位置 80ms 更新一次，如果直接读会让整个界面每秒重组 12 次。这里把"当前歌词行号"包成 derivedState，只有**行号变化**时才触发重组，行内动画用 `animateFloatAsState` 平滑。

**没有 ViewModel。** 曲库是 App 级单例，状态在 `StateFlow` 里；歌词偏好和播放状态是 Compose state。UI 直接读，少一层转发。

**没有 Room。** 曲库是"整体读、整体写、量级几千条"的场景，DataStore + JSON 足够，省掉 KSP 编译开销和一套 schema 迁移。

**歌词定位不用整树扫描。** 扫描时把配对好的 `lrcUri` 直接存下来；用户后补歌词时，顺着 `relPath` 逐级 `findFile` 只查一层目录，不重扫整棵树。

---

## 六、已知限制

- 曲库不监听文件系统变化，新增/删除歌曲需要手动「重新扫描」
- 不支持内嵌歌词（仅 `.lrc` 外挂文件）、不支持逐字歌词（`.krc` / `.qrc`）
- **不读任何音频标签**：没有专辑封面、艺术家、专辑、时长（列表里播放前不显示时长）；
  早期版本读标签会明显拖慢扫描，且标签与文件名不一致时歌词配对容易困惑，故有意省掉
- 没有搜索、没有排序方式切换
- 歌词偏移按文件 uri 记忆，歌曲文件被移动/改名后会丢失
- 颜色只有 8 个预设，不能自定义任意色相（原色相滑条因定位不准已移除）
- 通知栏控制在 Android 13+ 默认不可用（不申请通知权限，需到系统设置手动打开，见 v1.2）

## 七、后续可扩展方向

按改动成本从低到高：

1. 曲库搜索框（`LazyColumn` 前加过滤，几行代码）
2. 播放队列界面（`controller.getMediaItemAt(i)` 直接可用）
3. 倍速播放（ExoPlayer 原生支持 `setPlaybackSpeed`）
4. 歌词字体/行距自定义、歌词竖排等纯 UI 增强（不涉及读标签，与现有取舍不冲突）

> 以下两项早期曾列入计划，但**与现在「不读音频标签、只认文件名和文件夹名」的取舍相悖**，
> 要做就得重新引入 `MediaMetadataRetriever` 并接受扫描变慢，当前**不建议**：
> - 播放页加专辑封面（`embeddedPicture`，只给当前曲目加载）
> - 内嵌歌词支持（读 `METADATA_KEY_LYRICS` 或解析 ID3 USLT 帧）

---

## 八、关于图标库的说明

`material-icons-extended` 已被 androidx 冻结在 1.7.8（模块已从主干移除，但 Compose BOM 仍 pin 住这个版本，可正常解析）。

它体积很大（3700+ 个图标），但 **release 构建里 R8 会把没用到的全部裁掉**：实测 debug 包 23.7 MB / 15 个 dex，release 包 **2.52 MB / 1 个 dex**。
所以除非你在意 debug 构建速度，否则不必替换它。真想去掉的话，把用到的十几个图标换成自建 `ImageVector` 即可。

---

## 九、许可证

本项目基于 **Apache License 2.0** 发布，全文见仓库根目录 [`LICENSE`](LICENSE)。

这意味着你可以自由使用、修改、分发（包括商用），只需保留版权与许可声明。选择 Apache-2.0
而不是 GPL 系，是因为它与本项目依赖（AndroidX / Media3 / kotlinx.serialization 等）的
许可一致，且不要求衍生作品开源。

### 关于 F-Droid

如果要上架 F-Droid，除了本许可证外还需要满足：

1. **构建在无签名密钥时也能通过**：已处理。`app/build.gradle.kts` 在既没有
   `KEYSTORE_PATH` 环境变量、也没有仓库内 `keystore/lightmusic-release.jks` 时，
   会**产出未签名**的 release 包（而不是构建失败），F-Droid 用它自己的密钥重新签名。
2. **提交构建配方**：配方已写好，见 [`fdroid/com.azhan.music.yml`](fdroid/com.azhan.music.yml)。
   把它复制到 `gitlab.com/fdroid/fdroiddata` 的 `metadata/` 目录（文件名必须是
   `com.azhan.music.yml`），然后提 MR。
3. **每次发版必须递增 `versionCode`**：F-Droid 靠 `versionCode` 判断新旧，
   一直停在 `1` 会导致新版本无法被识别。配方里配了 `UpdateCheckData`，
   会自动从 `app/build.gradle.kts` 读取 `versionCode` / `versionName`。

注意：F-Droid 版与本地/CI 版**签名不同**，用户切换时需要先卸载旧版。
另外 compileSdk 37 较新，F-Droid 构建服务器能否装到对应 SDK 需要实际验证——
这一条我无法在本地确认，若构建服务器不支持，需在 MR 评审时与 F-Droid 维护者沟通。
