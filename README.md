# 轻听 · 本地音乐播放器

原生 Android 播放器，Kotlin + Jetpack Compose，**零第三方 UI 框架、零 DI 框架、零数据库**。
只做两件事：**按目录建播放列表** + **同步歌词（全屏 / 换色 / 时间微调）**。

---

## 一、功能清单

### 1. 添加目录 → 自动建播放列表

- 通过系统文件选择器（SAF）选择目录，**不需要申请任何存储权限**
- 播放列表名字 = 目录名字；一个目录 = 一个播放列表
- 支持添加多个目录，递归扫描子目录（最深 8 层）
- 递归时在同级目录里自动配对同名 `.lrc`
- 音频格式：mp3 / m4a / aac / flac / wav / ogg / opus / wma / ape / aiff / mka / amr / 3gp / dsf / dff
- 手动「重新扫描」可增量更新（元数据按 `uri|size|modified` 缓存，重扫几乎瞬间完成）

### 2. 歌词

- 识别同名 `.lrc`（`song.mp3` ↔ `song.lrc`）
- 编码自动识别：UTF-8（含 BOM）/ UTF-16 / **GBK 回退**（中文歌词乱码问题的根因）
- 格式支持：`[mm:ss]` `[mm:ss.x]` `[mm:ss.xx]` `[mm:ss.xxx]` `[mm:ss:xx]`、一行多时间戳、`[ti:] [ar:] [al:] [offset:]`
- **双语歌词合并**：同一时间戳出现两次时，第二行作为翻译显示在原文下方
- 全屏歌词：当前行高亮 + 自动居中滚动，点击任意行跳转播放位置
- 手动拖动时暂停自动滚动 2.5 秒，不跟你抢
- **换颜色**：8 个预设色 + 色相滑杆，全局生效
- **时间微调**：±0.1 / ±0.5 / ±1.0 秒 + 归零，**按歌曲分别记忆**（不同歌的偏移量不通用）
- 字号可调（14–44sp）
- 歌词是后补的？点右上角刷新即可重新读取，不用重扫目录

### 3. 播放

- Media3 ExoPlayer + MediaSessionService：**后台播放 + 系统通知栏控制 + 锁屏控制**（media3 自带，无需手写通知）
- 播放模式：顺序 / 列表循环 / 单曲循环 / 随机
- 上一首：播放超过 3 秒时先回到本曲开头（主流播放器习惯）
- 音频焦点处理、拔耳机自动暂停、本地唤醒锁

---

## 二、工程结构

```
music/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml          # 版本集中管理
├── gradlew / gradlew.bat / gradle/wrapper/
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/                            # 主题、自适应图标（纯矢量，无二进制图）
    └── java/com/azhan/music/
        ├── MusicApp.kt                 # 手写依赖容器（唯一的"注入"处）
        ├── MainActivity.kt
        ├── data/
        │   ├── Models.kt               # Song / Playlist / SongMeta / LyricPrefs
        │   ├── MusicStore.kt           # DataStore + JSON 持久化（3 个 key 分开存）
        │   ├── MusicScanner.kt         # SAF 递归遍历 + 歌词配对
        │   ├── MetadataReader.kt       # MediaMetadataRetriever 读标签
        │   ├── LibraryRepository.kt    # 曲库状态机（StateFlow）
        │   └── LyricPrefsState.kt      # 歌词偏好（Compose state，写入去抖）
        ├── lyrics/
        │   ├── LrcParser.kt            # LRC 解析 + 双语合并
        │   ├── TextDecoder.kt          # 编码嗅探（UTF-8 → GBK）
        │   └── LyricsRepository.kt     # 歌词定位 + LruCache
        ├── player/
        │   ├── PlaybackService.kt      # MediaSessionService
        │   └── PlaybackController.kt   # MediaController → Compose state
        └── ui/
            ├── AppRoot.kt              # 导航（4 个页面，字符串路由）
            ├── theme/Theme.kt
            ├── common/                 # 时间格式化、歌词记忆、迷你播放条
            ├── home/HomeScreen.kt      # 播放列表（目录）列表
            ├── playlist/PlaylistScreen.kt
            ├── player/PlayerScreen.kt  # 播放页 + 两行歌词预览
            └── lyrics/LyricsScreen.kt  # 全屏歌词 + 控制面板
```

---

## 二·五、实测验证记录

### v1.2 · 去掉通知权限申请

启动时不再弹 `POST_NOTIFICATIONS` 权限框。Android 13+ 上默认不显示播放通知，
但**后台播放照常工作**（没有该权限时 `startForeground()` 仍会成功，只是通知被系统丢掉，不会崩溃）。

在模拟器上撤销权限（`pm revoke`）后冷启动实测：无对话框，`state=PLAYING`，
position 2792ms → 5802ms 正常推进，FATAL 计数 0。

AndroidManifest 里**保留**了权限声明：想要通知栏播放控制的用户可以在
「系统设置 → 通知 → 轻听」手动打开，通知会立刻恢复。

### v1.1 · 拟态（Neumorphism）风格 + 全屏歌词面板默认收起

**在 Android 17 模拟器上重装验证通过。** 新版 APK 路径同下：

```
dist/轻听-LightMusic-1.0-release.apk   2.34 MB  ← 装机用这个
```

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
- 拟态天然依赖"元素与背景同色、靠阴影分层"，所以 App **不跟随系统深色模式**固定浅色；
  全屏歌词单独用一套深色拟态配色（见 LyricsScreen），避免夜里看歌词刺眼。

### v1.0 · 完整流程实测

| 验证项 | 结果 |
|---|---|
| 安装 + 启动 | ✅ 无 FATAL，进程存活 |
| 后台播放服务 | ✅ 日志出现 `Background started FGS ... MediaSessionService cmp=com.azhan.music/.player.PlaybackService` |
| 添加目录（SAF） | ✅ 选 `/sdcard/Music` 授权后，自动生成名为 `Music` 的播放列表 |
| 目录扫描 | ✅ 扫到 2 个音频文件，**时长正确读出 00:20** |
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
| release（已签名，推荐） | `dist/轻听-LightMusic-1.0-release.apk` | **2.4 MB** | 1 |
| debug（便于看崩溃堆栈） | `dist/轻听-LightMusic-1.0-debug.apk` | 23.6 MB | 15 |

（原始产物也在 `app/build/outputs/apk/{release,debug}/` 下。）

装到手机：

```bash
adb install -r dist/轻听-LightMusic-1.0-release.apk
```

也可以直接把 APK 传到手机上点击安装（需要允许"安装未知来源应用"）。

### 签名

release 包用 `keystore/lightmusic-release.jks` 签名：

- alias：`lightmusic`
- store / key 密码：`lightmusic`

**个人自用够用；要上架应用商店请换成你自己的 keystore 并妥善保管**（丢了就没法覆盖升级）。

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

**冷启动不扫描。** 扫描结果（含元数据）落盘，启动时只读 JSON。3000 首的曲库启动耗时基本等于读一个几百 KB 的文件。代价是曲库不会自动跟随文件系统变化 —— 所以留了「重新扫描」按钮。

**元数据按 `uri|size|modified` 缓存。** 重扫时文件没变就直接命中缓存，不再开 `MediaMetadataRetriever`（这是扫描慢的主要来源）。首次扫描用 4 并发 + 信号量限流。

**位置刷新用 `derivedStateOf` 收口。** 播放位置 80ms 更新一次，如果直接读会让整个界面每秒重组 12 次。这里把"当前歌词行号"包成 derivedState，只有**行号变化**时才触发重组，行内动画用 `animateFloatAsState` 平滑。

**没有 ViewModel。** 曲库是 App 级单例，状态在 `StateFlow` 里；歌词偏好和播放状态是 Compose state。UI 直接读，少一层转发。

**没有 Room。** 曲库是"整体读、整体写、量级几千条"的场景，DataStore + JSON 足够，省掉 KSP 编译开销和一套 schema 迁移。

**歌词定位不用整树扫描。** 扫描时把配对好的 `lrcUri` 直接存下来；用户后补歌词时，顺着 `relPath` 逐级 `findFile` 只查一层目录，不重扫整棵树。

---

## 六、已知限制

- 曲库不监听文件系统变化，新增/删除歌曲需要手动「重新扫描」
- 不支持内嵌歌词（仅 `.lrc` 外挂文件）、不支持逐字歌词（`.krc` / `.qrc`）
- 不读专辑封面（`MediaMetadataRetriever` 取封面会让列表滚动变卡，有意省掉）
- 没有搜索、没有收藏、没有排序方式切换
- 歌词偏移按文件 uri 记忆，歌曲文件被移动/改名后会丢失

## 七、后续可扩展方向

按改动成本从低到高：

1. 播放页加专辑封面（用 `LruCache` + `MediaMetadataRetriever.embeddedPicture`，只给当前曲目加载）
2. 曲库搜索框（`LazyColumn` 前加过滤，几行代码）
3. 播放队列界面（`controller.getMediaItemAt(i)` 直接可用）
4. 睡眠定时器 / 倍速播放（ExoPlayer 原生支持 `setPlaybackSpeed`）
5. 内嵌歌词支持（`MediaMetadataRetriever` 读 `METADATA_KEY_LYRICS` 或解析 ID3 USLT 帧）

---

## 八、关于图标库的说明

`material-icons-extended` 已被 androidx 冻结在 1.7.8（模块已从主干移除，但 Compose BOM 仍 pin 住这个版本，可正常解析）。

它体积很大（3700+ 个图标），但 **release 构建里 R8 会把没用到的全部裁掉**：实测 debug 包 23.6 MB / 15 个 dex，release 包 **2.4 MB / 1 个 dex**。
所以除非你在意 debug 构建速度，否则不必替换它。真想去掉的话，把用到的十几个图标换成自建 `ImageVector` 即可。
