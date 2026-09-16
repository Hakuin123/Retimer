package dev.hk256.retimer.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import dev.hk256.retimer.data.AppSettingsState
import dev.hk256.retimer.data.FilenameRuleState
import dev.hk256.retimer.ui.components.ExpressiveListItem
import dev.hk256.retimer.ui.components.FilledInfoCard
import dev.hk256.retimer.ui.components.ScreenHeadline
import dev.hk256.retimer.ui.components.SectionCardCorner
import dev.hk256.retimer.ui.theme.AppMotion
import dev.hk256.retimer.ui.theme.AppTypeScale
import kotlinx.coroutines.launch

private enum class AppPage(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    EDIT("编辑时间", Icons.Outlined.EditCalendar, Icons.Rounded.EditCalendar),
    REPAIR("更正时间", Icons.Outlined.AutoFixHigh, Icons.Rounded.AutoFixHigh),
    TOOLS("小工具", Icons.Outlined.Build, Icons.Rounded.Build),
    SETTINGS("设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTimeFixerApp(
    settings: AppSettingsState,
    filenameRules: FilenameRuleState,
) {
    var selectedPage by remember { mutableIntStateOf(AppPage.EDIT.ordinal) }
    /** 当前页面是否有未处理的已选媒体（页面自己上报），用于切换页面时的二次确认。 */
    var selectedMediaCount by remember { mutableIntStateOf(0) }
    /** 当前页面是否处在主页 FAB 之后的整屏流程里（页面自己上报）。 */
    var flowActive by remember { mutableStateOf(false) }
    /** 等待二次确认的切换目标；不为空时显示确认弹窗。 */
    var pendingPage by remember { mutableStateOf<Int?>(null) }
    val pages = AppPage.entries
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val snackbarHostState = remember { SnackbarHostState() }

    /** 还没做的东西只给一个明确反馈，不要点了没反应。 */
    fun showMessage(message: String) {
        scope.launch {
            // 连点时不排队：先把上一条收起来，再弹新的。
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    fun closeDrawer() {
        scope.launch { drawerState.close() }
    }

    /**
     * 顶栏滚动状态复位。
     *
     * 顶栏的"已滚动"颜色来自库里的 `TopAppBarState.overlappedFraction`，它**只看 contentOffset**
     * （pinned 行为的滚动连接只累加 contentOffset，heightOffset 恒为 0，复位 heightOffset 没有作用）。
     * 这份状态是外壳唯一一份、跨页面复用的：滚动增量只有当前内容会发出来，新内容停在顶部时不再发增量，
     * 上一块内容的"已滚动"就会留在顶栏上。所以每次换内容（切页、流程换阶段）都要把两个偏移一起归零。
     */
    fun resetTopBarScroll() {
        topBarScrollBehavior.state.contentOffset = 0f
        topBarScrollBehavior.state.heightOffset = 0f
    }

    /**
     * 真正切页。
     *
     * 目标页面的组合是新建的，选择列表、候选、流程阶段都带不过去（页面状态用的是 remember），
     * 这里同步复位外壳自己持有的状态，不依赖销毁时机。
     */
    fun selectPage(index: Int) {
        selectedPage = index
        flowActive = false
        selectedMediaCount = 0
        resetTopBarScroll()
    }

    /** 切页入口：有已选媒体待处理时先二次确认，避免用户以为选择会跟着切过去。 */
    fun requestSelectPage(index: Int) {
        if (index == selectedPage) {
            closeDrawer()
            return
        }
        if (selectedMediaCount > 0) {
            pendingPage = index
            closeDrawer()
            return
        }
        selectPage(index)
        closeDrawer()
    }

    // 主页标签页间的返回不做拦截：返回键/返回手势遵循系统行为退出应用；
    // 流程里的返回由页面按阶段自己处理。

    ModalNavigationDrawer(
        drawerState = drawerState,
        // 流程里顶栏是返回按钮，没有入口打开抽屉，边缘手势也一并关掉。
        gesturesEnabled = !flowActive,
        drawerContent = {
            // 侧滑栏宽度动态适配：库里默认固定 360dp，小屏上会几乎盖满整个屏幕。
            // 按 Material 规范取「屏幕宽度 - 左侧露出 56dp」与 360dp 上限中的较小值，
            // 小屏不超出屏幕、大屏不超过规范上限。
            val screenWidth = LocalConfiguration.current.screenWidthDp.dp
            val drawerWidth = minOf(360.dp, screenWidth - 56.dp)
            ModalDrawerSheet(
                modifier = Modifier.width(drawerWidth),
            ) {
                Text(
                    text = "Retimer",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp),
                )
                pages.forEachIndexed { index, page ->
                    NavigationDrawerItem(
                        label = { Text(page.label, style = MaterialTheme.typography.labelLarge) },
                        selected = selectedPage == index,
                        icon = {
                            Icon(
                                imageVector = if (selectedPage == index) page.selectedIcon else page.icon,
                                contentDescription = null,
                            )
                        },
                        onClick = { requestSelectPage(index) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        // 流程里标题留空：阶段本身的标题已经说明当前在哪一步。
                        if (!flowActive) {
                            Text("Retimer", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (flowActive) {
                                    // 与系统返回一致：由页面按当前阶段决定往回走还是退出流程
                                    backDispatcher?.onBackPressed()
                                } else {
                                    scope.launch { drawerState.open() }
                                }
                            },
                        ) {
                            if (flowActive) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                            } else {
                                Icon(Icons.Rounded.Menu, contentDescription = "打开导航菜单")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                    scrollBehavior = topBarScrollBehavior,
                )
            },
            bottomBar = {
                // 流程里不显示底部导航：整屏只留当前流程，减少入口干扰。
                //
                // 这里刻意不做高度动画：底部导航的高度就是内容区的下边界，动画期间内容区一直在变，
                // 流程页里贴着底部排的动作条会一边被水平滑入、一边被迫上下移动，轨迹就变成斜线。
                // 即时显隐让内容区在过渡开始前就定好尺寸，整段过渡都是纯水平位移。
                if (!flowActive) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        pages.forEachIndexed { index, page ->
                            val selected = selectedPage == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { requestSelectPage(index) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) page.selectedIcon else page.icon,
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(page.label, style = MaterialTheme.typography.labelMedium) },
                                alwaysShowLabel = !settings.hideUnselectedNavLabels,
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    ),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            // 过渡容器铺满整屏宽度，页面左右 16dp 边距由各页面内容自己承担，
            // 否则滑动时会在距屏幕两侧 16dp 处被裁剪，看起来像被切掉一条。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                AnimatedContent(
                    targetState = selectedPage,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val forward = targetState > initialState
                        val spatial = AppMotion.defaultSpatial<IntOffset>()
                        // 位置用 defaultSpatial，透明度进入用 defaultEffects、退出用 fastEffects
                        // （与库内 DatePicker 的内容切换保持一致：进入 default、退出 fast）。
                        (
                            slideInHorizontally(spatial) { width -> if (forward) width / 4 else -width / 4 } +
                                fadeIn(AppMotion.defaultEffects<Float>())
                        )
                            .togetherWith(
                                slideOutHorizontally(spatial) { width -> if (forward) -width / 4 else width / 4 } +
                                    fadeOut(AppMotion.fastEffects<Float>()),
                            )
                            // 页面都是铺满整屏的：不要库内默认的容器尺寸动画，否则外壳尺寸一变，
                            // 容器高度会被重新动画一遍，贴底的内容会跟着裁剪边走。
                            .using(SizeTransform(clip = true) { _, _ -> snap() })
                    },
                    label = "appPage",
                ) { index ->
                    when (pages[index]) {
                        AppPage.EDIT ->
                            EditMediaPage(
                                onSelectionCountChange = { selectedMediaCount = it },
                                onFlowActiveChange = { flowActive = it },
                                onTopBarScrollReset = ::resetTopBarScroll,
                            )
                        AppPage.REPAIR ->
                            RepairMediaPage(
                                filenameRules = filenameRules,
                                onSelectionCountChange = { selectedMediaCount = it },
                                onFlowActiveChange = { flowActive = it },
                                onTopBarScrollReset = ::resetTopBarScroll,
                            )
                        AppPage.TOOLS -> ToolsPage(onShowMessage = ::showMessage)
                        AppPage.SETTINGS -> SettingsPage(settings = settings, onShowMessage = ::showMessage)
                    }
                }
            }
        }
    }

    pendingPage?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingPage = null },
            title = { Text("切换页面？", style = AppTypeScale.dialogTitle) },
            text = {
                Text(
                    "切换到「${pages[target].label}」会清空当前已选择的 $selectedMediaCount 项媒体。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPage = null
                        selectPage(target)
                    },
                ) {
                    Text("切换", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPage = null }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

/** 小工具条目：图标、标题、一句话说明。 */
private data class ToolEntry(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/** 小工具清单，顺序即渲染顺序。 */
private val ToolEntries: List<ToolEntry> =
    listOf(
        ToolEntry(Icons.AutoMirrored.Rounded.ManageSearch, "元数据查看器", "查看照片和视频中的元数据"),
        ToolEntry(Icons.Rounded.TextFields, "批量重命名", "按规则批量重命名照片和视频文件"),
        ToolEntry(Icons.Rounded.History, "处理记录", "查看成功、跳过和失败的任务"),
    )

/** 与设置页同一套排版：页头、介绍卡片、分组标题、分组列表。 */
@Composable
private fun ToolsPage(onShowMessage: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item {
            ScreenHeadline(icon = Icons.Rounded.Build, title = "小工具")
            Spacer(Modifier.height(16.dp))
        }
        item {
            FilledInfoCard(
                icon = Icons.Rounded.Handyman,
                title = "媒体小工具",
                description = "敬请期待",
            )
            Spacer(Modifier.height(24.dp))
        }
        item {
            // 分组标题的缩进跟着分组列表的内层圆角走（与设置页的"外观""关于"一致）。
            Text(
                text = "工具",
                style = AppTypeScale.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = SectionCardCorner, bottom = 8.dp),
            )
        }
        item {
            // index/count 决定分组首尾项的圆角：最上面一项上边缘、最下面一项下边缘用外圆角。
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ToolEntries.forEachIndexed { index, tool ->
                    ExpressiveListItem(
                        index = index,
                        count = ToolEntries.size,
                        icon = tool.icon,
                        headline = tool.title,
                        supporting = tool.description,
                        onClick = { onShowMessage("敬请期待") },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}
