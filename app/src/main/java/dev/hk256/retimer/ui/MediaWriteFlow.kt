package dev.hk256.retimer.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import dev.hk256.retimer.core.EditTargets
import dev.hk256.retimer.core.MediaItem
import dev.hk256.retimer.core.ProcessingResult
import dev.hk256.retimer.data.UserPreferences
import dev.hk256.retimer.media.ExifImageDateWriter
import dev.hk256.retimer.media.MediaStoreRepository
import dev.hk256.retimer.media.MediaStoreUris
import dev.hk256.retimer.ui.theme.AppMotion
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一个待写入项目：媒体本身、要写入的目标时间，以及申请写入授权用的媒体条目地址。
 *
 * [uri] 是申请写入授权时反查出来的媒体条目地址，拿不到才是 null。写入必须用这里记下的
 * 地址，不能到写入时再反查一次：相册选择器给的地址是临时的，反查结果可能已经变了。
 */
internal data class MediaWriteTarget(
    val item: MediaItem,
    val target: Instant,
    val uri: Uri?,
)

/**
 * 两个页面共用的写入执行链：反查媒体条目地址 → 按需申请读取媒体权限 → 申请写入授权 →
 * 写入 EXIF（及可选的媒体日期/文件修改时间）→ 汇总结果文案。
 *
 * Android 端实现：整条链路依赖 MediaStore、Activity Result API 与 ContentResolver，
 * 换平台（如桌面端）需要另写一份同等职责的适配。
 *
 * 页面只负责给出"要写哪些项目、写成什么时间"，以及拿到结果文案后怎么显示。
 */
@Stable
internal class MediaWriteController(
    private val context: Context,
    private val repository: MediaStoreRepository,
    private val mediaStoreUris: MediaStoreUris,
    private val zoneId: ZoneId,
    private val scope: CoroutineScope,
    private val preferences: UserPreferences,
    initialSyncModified: Boolean,
) {
    /** Activity 结果回调，由 [rememberMediaWriteController] 注入（创建 launcher 必须在组合里）。 */
    internal var writeLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    internal var readMediaPermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    internal var allFilesAccessLauncher: ActivityResultLauncher<Intent>? = null

    /** 是否同时改文件在存储里的时间戳；需要「所有文件访问权限」，默认关闭。 */
    var syncModified by mutableStateOf(initialSyncModified)
        private set

    /** 请求「所有文件访问权限」前的说明弹窗。 */
    var allFilesDialogVisible by mutableStateOf(false)
        private set

    private var pendingTargets: List<MediaWriteTarget> = emptyList()
    private var targetsAwaitingReadAccess: List<MediaWriteTarget> = emptyList()
    private var onFinished: ((String) -> Unit)? = null

    /** 请求 MediaStore 写入授权时使用的媒体条目 URI；拿不到时返回 null（改走直接写入）。 */
    fun mediaStoreUri(item: MediaItem): Uri? =
        mediaStoreUris.resolve(repository.uriFor(item), item.filePath)

    /** 缩略图与写入实际使用的 URI：优先用转换后的媒体条目地址，否则退回原始 URI。 */
    fun writableUri(item: MediaItem): Uri = mediaStoreUri(item) ?: repository.uriFor(item)

    /** 「同步文件修改时间」开关：没有「所有文件访问权限」时先说明为什么需要、不开启会怎样。 */
    fun changeSyncModified(checked: Boolean) {
        when {
            !checked -> applySyncModified(false)
            hasAllFilesAccess() -> applySyncModified(true)
            else -> allFilesDialogVisible = true
        }
    }

    fun dismissAllFilesDialog() {
        allFilesDialogVisible = false
    }

    /** 打开系统设置里的「所有文件访问权限」页面。 */
    fun requestAllFilesAccess() {
        allFilesDialogVisible = false
        val launcher = checkNotNull(allFilesAccessLauncher)
        val appSpecific =
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null),
            )
        // 部分定制系统没有应用级的这个页面，退回权限总列表。
        runCatching { launcher.launch(appSpecific) }
            .onFailure { launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    internal fun onAllFilesAccessResult() {
        // 真的授权了才把开关打开。
        applySyncModified(hasAllFilesAccess())
        if (!syncModified) {
            Toast.makeText(context, "权限授予失败，选项保持关闭", Toast.LENGTH_SHORT).show()
        }
    }

    /** 改动即记为下次的默认值。 */
    private fun applySyncModified(enabled: Boolean) {
        syncModified = enabled
        preferences.syncFileModifiedTime = enabled
    }

    /**
     * 执行写入，最后把结果文案交给 [onFinished]。
     *
     * 只有 MediaStore 的媒体条目 URI 能用于请求写入授权，其余（相册选择器的只读地址、
     * 反查不到媒体条目的 SAF 文档）直接写文件，写不动时由结果文案解释原因。
     */
    fun write(targets: List<MediaWriteTarget>, onFinished: (String) -> Unit) {
        if (targets.isEmpty()) {
            onFinished("没有可处理的媒体")
            return
        }
        this.onFinished = onFinished
        pendingTargets = targets
        // 相册选择器只授予读取权限，可写的媒体条目地址要靠读取媒体库反查；
        // 没有读取权限时先申请一次，否则这些项目拿不到可写地址，只能写只读地址并失败。
        val needsReadAccess =
            targets.any { target ->
                target.uri == null && mediaStoreUris.isPickerUri(repository.uriFor(target.item))
            }
        if (needsReadAccess && !hasReadMediaAccess(context)) {
            targetsAwaitingReadAccess = targets
            checkNotNull(readMediaPermissionLauncher).launch(readMediaPermissions())
            return
        }
        requestWriteAccess(targets)
    }

    internal fun onReadMediaPermissionResult() {
        val targets = targetsAwaitingReadAccess
        targetsAwaitingReadAccess = emptyList()
        // 无论用户是否同意都继续：拿到权限能反查到媒体条目，拿不到就退回直接写文件。
        requestWriteAccess(targets)
    }

    internal fun onWriteAuthorizationResult(resultCode: Int) {
        val targets = pendingTargets
        pendingTargets = emptyList()
        if (resultCode != Activity.RESULT_OK) {
            finish("未获得媒体写入授权")
            return
        }
        scope.launch { finish(writeTargets(targets)) }
    }

    private fun requestWriteAccess(targets: List<MediaWriteTarget>) {
        val grantable = targets.mapNotNull { it.uri }
        if (grantable.isEmpty()) {
            scope.launch { finish(writeTargets(targets)) }
            return
        }
        runCatching {
            val request =
                MediaStore.createWriteRequest(
                    context.contentResolver,
                    grantable.distinct(),
                )
            checkNotNull(writeLauncher).launch(IntentSenderRequest.Builder(request.intentSender).build())
        }.onFailure { error ->
            finish("无法请求媒体写入授权：${error.message ?: "不支持此媒体来源"}")
        }
    }

    /** 把目标时间写进 EXIF（及可选的媒体日期/文件修改时间），并汇总结果文案。 */
    private suspend fun writeTargets(targets: List<MediaWriteTarget>): String {
        val uriById = targets.associate { it.item.id to it.uri }
        val writer =
            ExifImageDateWriter(
                contentResolver = context.contentResolver,
                uriProvider = { item -> uriById[item.id] ?: repository.uriFor(item) },
                zoneId = zoneId,
            )
        val results =
            withContext(Dispatchers.IO) {
                targets.map { target ->
                    writer.write(target.item, target.target, EditTargets(fileModifiedTime = syncModified))
                }
            }
        // 只有确实写失败的项目才值得提示来源限制，避免"已经写好了却说不允许写入"。
        val failedItems =
            targets.filterIndexed { index, _ -> results[index] is ProcessingResult.Failed }.map { it.item }
        val reasons =
            results
                .mapNotNull { result ->
                    when (result) {
                        is ProcessingResult.Partial -> result.reason
                        is ProcessingResult.Failed -> result.reason
                        is ProcessingResult.Skipped -> result.reason
                        else -> null
                    }
                }
                .distinct()
                .take(3)
        return listOfNotNull(
            results.summarize(),
            reasons.takeIf { it.isNotEmpty() }?.joinToString("；"),
            pickerReadOnlyHint(failedItems),
        ).joinToString("\n\n")
    }

    /** 相册选择器只授予读取权限：反查不回媒体库条目的项目无法写入，需要给用户一个明确的解释。 */
    private fun pickerReadOnlyHint(items: List<MediaItem>): String? {
        val blocked =
            items.filter { item ->
                mediaStoreUri(item) == null && mediaStoreUris.isPickerUri(repository.uriFor(item))
            }
        if (blocked.isEmpty()) return null
        val names = blocked.take(3).joinToString("、") { it.displayName }
        val more = if (blocked.size > 3) " 等 ${blocked.size} 项" else ""
        return "系统相册选择器只授予读取权限，$names$more 无法写入。请在系统提示中允许访问照片和视频，或改用「选择文件夹」「选择文件」重新选择这些媒体。"
    }

    private fun finish(message: String) {
        val callback = onFinished
        onFinished = null
        callback?.invoke(message)
    }

    /** 是否已经拿到「所有文件访问权限」（同步文件修改时间要靠它）。 */
    private fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()
}

/** 创建写入控制器，并注入必须在组合里创建的 Activity 结果回调。 */
@Composable
internal fun rememberMediaWriteController(
    repository: MediaStoreRepository,
    mediaStoreUris: MediaStoreUris,
    zoneId: ZoneId,
    preferences: UserPreferences,
): MediaWriteController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller =
        remember {
            MediaWriteController(
                context = context.applicationContext,
                repository = repository,
                mediaStoreUris = mediaStoreUris,
                zoneId = zoneId,
                scope = scope,
                preferences = preferences,
                // 记住的选择要重新过一遍权限：权限被撤销过就退回关闭。
                initialSyncModified =
                    preferences.syncFileModifiedTime && Environment.isExternalStorageManager(),
            )
        }
    controller.writeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            controller.onWriteAuthorizationResult(result.resultCode)
        }
    controller.readMediaPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            controller.onReadMediaPermissionResult()
        }
    controller.allFilesAccessLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            controller.onAllFilesAccessResult()
        }
    return controller
}

/**
 * 选媒体：三个入口（相册选择器 / 文件 / 文件夹）加统一的读取与进度状态。
 *
 * 读取在 IO 线程进行，期间 [loading] 为 true；读完后交给 [onLoaded]（挂起函数，
 * 页面可以在这期间继续做自己的处理，进度对话框不会闪断）。
 */
@Stable
internal class MediaPicker(
    private val context: Context,
    private val repository: MediaStoreRepository,
    private val mediaStoreUris: MediaStoreUris,
    private val scope: CoroutineScope,
    private val onLoaded: suspend (List<MediaItem>) -> Unit,
) {
    internal var visualLauncher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    internal var documentLauncher: ActivityResultLauncher<Array<String>>? = null
    internal var treeLauncher: ActivityResultLauncher<Uri?>? = null

    /** 是否正在读取所选媒体。 */
    var loading by mutableStateOf(false)
        private set

    /** 读取阶段的提示文案（例如"正在读取所选媒体…"）。 */
    var loadingLabel by mutableStateOf("")
        private set

    fun pickVisual() {
        checkNotNull(visualLauncher)
            .launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    fun pickDocument() {
        checkNotNull(documentLauncher).launch(arrayOf("image/*", "video/*"))
    }

    fun pickTree() {
        checkNotNull(treeLauncher).launch(null)
    }

    internal fun loadUris(uris: List<Uri>) {
        start(uri = null, uris = uris, label = "正在读取所选媒体…")
    }

    internal fun loadTree(uri: Uri) {
        start(uri = uri, uris = emptyList(), label = "正在读取所选目录…")
    }

    private fun start(uri: Uri?, uris: List<Uri>, label: String) {
        if (uri == null && uris.isEmpty()) return
        scope.launch {
            loading = true
            loadingLabel = label
            try {
                val loaded =
                    withContext(Dispatchers.IO) {
                        if (uri != null) {
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                            }
                            repository.mediaFromTree(uri)
                        } else {
                            uris.mapNotNull(repository::mediaFromUri).map { item ->
                                // 相册选择器给的地址上文件名是系统内部副本的名字，能查到就用媒体库里的原名。
                                item.copy(
                                    displayName =
                                        mediaStoreUris.displayNameFor(repository.uriFor(item), item.displayName),
                                )
                            }
                        }
                    }
                onLoaded(loaded)
            } finally {
                loading = false
            }
        }
    }
}

/** 创建选媒体加载器，并注入必须在组合里创建的选择器回调。 */
@Composable
internal fun rememberMediaPicker(
    repository: MediaStoreRepository,
    mediaStoreUris: MediaStoreUris,
    onLoaded: suspend (List<MediaItem>) -> Unit,
): MediaPicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker =
        remember {
            MediaPicker(
                context = context.applicationContext,
                repository = repository,
                mediaStoreUris = mediaStoreUris,
                scope = scope,
                onLoaded = onLoaded,
            )
        }
    picker.visualLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
            picker.loadUris(uris)
        }
    picker.documentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            picker.loadUris(uris)
        }
    picker.treeLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) picker.loadTree(uri)
        }
    return picker
}

/**
 * 阶段切换的过渡：位置用 defaultSpatial，透明度进入用 defaultEffects、退出用 fastEffects
 * （与库内 DatePicker 的内容切换保持一致：进入 default、退出 fast）。
 *
 * 另外把 [SizeTransform] 的尺寸动画改成 snap：库内默认会在过渡期间把内容容器的高度
 * 从旧值平滑动画到新值（`AnimatedContent` 的 SizeModifierNode + 默认 SizeTransform），
 * 而我们的每个阶段都是铺满整屏的，容器高度唯一会变的原因就是外壳（底部导航隐藏）导致
 * 可用高度变化——那会让贴底的内容被移动的裁剪边"慢慢露出来"，和水平位移叠在一起就成了斜线。
 * 尺寸直接对齐，过渡就只剩纯水平位移。
 */
internal fun AnimatedContentTransitionScope<*>.mediaStageTransition(forward: Boolean): ContentTransform {
    val spatial = AppMotion.defaultSpatial<IntOffset>()
    return (
        slideInHorizontally(spatial) { width -> if (forward) width / 3 else -width / 3 } +
            fadeIn(AppMotion.defaultEffects())
    ).togetherWith(
        slideOutHorizontally(spatial) { width -> if (forward) -width / 3 else width / 3 } +
            fadeOut(AppMotion.fastEffects()),
    ).using(SizeTransform(clip = true) { _, _ -> snap() })
}

private fun List<ProcessingResult>.summarize(): String {
    val success = count { it is ProcessingResult.Success }
    val partial = count { it is ProcessingResult.Partial }
    val skipped = count { it is ProcessingResult.Skipped }
    val failed = count { it is ProcessingResult.Failed }
    return "成功 $success，部分成功 $partial，跳过 $skipped，失败 $failed"
}

/** 申请读取媒体库的权限。Android 13 起是「照片和视频」，更低版本是外部存储读取。 */
private fun readMediaPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

/**
 * 是否已经有读取媒体库的能力。
 *
 * Android 14 起用户可以只授权「选择的照片」，那时拿到的是
 * [Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED]，同样能查到用户选中的媒体。
 */
private fun hasReadMediaAccess(context: Context): Boolean {
    fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VIDEO) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}
