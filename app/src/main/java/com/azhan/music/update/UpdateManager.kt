package com.azhan.music.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.azhan.music.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext

/** 更新流程的界面状态。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Latest : UpdateState

    data class Available(
        val version: String,
        val sizeBytes: Long,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String,
    ) : UpdateState

    data class Downloading(val progress: Float, val downloaded: Long, val total: Long) : UpdateState

    data class Ready(val version: String, val file: File) : UpdateState

    data class Failed(val message: String) : UpdateState
}

/**
 * 应用内更新：查 GitHub Releases → 下载 APK → 交给系统安装器。
 *
 * 刻意做成**手动触发**：
 * - App 没有后台联网、没有启动自检、不上报任何信息，只有用户点「检查更新」时才会请求一次
 *   `api.github.com`，这一点写进了 README；
 * - 不引入 OkHttp/Retrofit，直接用 HttpURLConnection（少一个依赖，体积也更小）。
 *
 * 下载后会用 PackageManager 校验一次包名，避免把错误内容当安装包递给系统。
 */
class UpdateManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var available: UpdateState.Available? = null
    private var job: Job? = null

    /** 检查最新版本。重复点击会被忽略（正在检查时）。 */
    fun check() {
        if (_state.value is UpdateState.Checking) return
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = try {
                fetchLatest()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                UpdateState.Failed(t.friendlyMessage())
            }
        }
    }

    /** 下载当前发现的版本；已下载过则直接复用缓存文件。 */
    fun download() {
        val target = available ?: return
        if (_state.value is UpdateState.Downloading) return
        job?.cancel()
        job = scope.launch {
            _state.value = UpdateState.Downloading(0f, 0L, target.sizeBytes)
            try {
                _state.value = UpdateState.Ready(target.version, downloadApk(target))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.friendlyMessage())
            }
        }
    }

    /** 系统是否允许本应用安装 APK（Android 8+ 需要用户手动开）。 */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 跳到「安装未知应用」的系统设置页。 */
    fun openInstallPermission(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 调起系统安装器；返回 false 表示没有安装权限。 */
    fun install(file: File): Boolean {
        if (!canInstallPackages()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 打开浏览器看发布页（不下载时用）。 */
    fun openReleasePage(url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    // ------------------------------------------------------------------

    private suspend fun fetchLatest(): UpdateState = withContext(Dispatchers.IO) {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LightMusic/${BuildConfig.VERSION_NAME}")
        }
        try {
            when (val code = connection.responseCode) {
                404 -> return@withContext UpdateState.Failed("仓库还没有发布任何版本")
                403, 429 -> return@withContext UpdateState.Failed("请求太频繁，过一会儿再试")
                !in 200..299 -> return@withContext UpdateState.Failed("检查失败（HTTP $code）")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<ReleaseDto>(body)

            val remote = parseVersion(release.tagName)
                ?: return@withContext UpdateState.Failed("无法识别版本号：${release.tagName}")
            val local = parseVersion(BuildConfig.VERSION_NAME)
                ?: return@withContext UpdateState.Failed("本地版本号异常")

            if (remote <= local) {
                available = null
                return@withContext UpdateState.Latest
            }

            val asset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?: return@withContext UpdateState.Failed("这个版本没有 APK 附件")

            UpdateState.Available(
                version = release.tagName.removePrefix("v"),
                sizeBytes = asset.size,
                notes = release.body.orEmpty().trim(),
                pageUrl = release.htmlUrl.orEmpty(),
                apkUrl = asset.downloadUrl,
            ).also { available = it }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadApk(target: UpdateState.Available): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        // 只留这一次的包，避免旧版本堆在缓存里
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "LightMusic-${target.version}.apk")

        val connection = (URL(target.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LightMusic/${BuildConfig.VERSION_NAME}")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("下载失败（HTTP $code）")

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: target.sizeBytes
            var downloaded = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        _state.value = UpdateState.Downloading(
                            progress = if (total > 0) downloaded.toFloat() / total else 0f,
                            downloaded = downloaded,
                            total = total,
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }

        // 基本校验：必须是一个包名对得上的 APK，防止把错误页面/残缺文件递给安装器
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (info == null || info.packageName != context.packageName) {
            file.delete()
            error("下载的安装包不合法，请重试")
        }
        file
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is UnknownHostException -> "连不上 GitHub，请检查网络（部分网络环境可能无法访问）"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        else -> message ?: "未知错误"
    }

    private companion object {
        const val API_URL = "https://api.github.com/repos/xushujin/music/releases/latest"
    }
}

/** 语义化版本，只比较主/次/修订号，忽略 `-beta` 之类的后缀。 */
private data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
}

private fun parseVersion(raw: String): Version? {
    val core = raw.trim().removePrefix("v").substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    if (parts.size < 2) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts[1].toIntOrNull() ?: return null
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return Version(major, minor, patch)
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val assets: List<AssetDto> = emptyList(),
)

@Serializable
private data class AssetDto(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)
