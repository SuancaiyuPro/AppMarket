package com.app.market.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.app.market.domain.model.download.DownloadState
import com.app.market.resources.Res
import com.app.market.resources.fdroid_official
import com.app.market.resources.fdroid_official_summary
import com.app.market.resources.fdroid_enabled
import com.app.market.resources.fdroid_enabled_summary
import com.app.market.resources.fdroid_repositories
import com.app.market.resources.fdroid_source
import com.app.market.resources.fdroid_tuna
import com.app.market.resources.fdroid_tuna_summary
import com.app.market.resources.install
import com.app.market.resources.search_hint
import com.app.market.resources.retry
import com.app.market.resources.loading
import com.app.market.resources.back
import com.app.market.resources.nav_fdroid
import com.app.market.ui.component.CardSegmentContainer
import com.app.market.ui.component.MainTabScaffold
import com.app.market.ui.component.MarketScaffold
import com.app.market.ui.component.PageVerticalPadding
import com.app.market.ui.component.AppActionButton
import com.app.market.ui.model.AppActionKind
import com.app.market.ui.util.formatSize
import com.app.market.viewmodel.FdroidApp
import com.app.market.viewmodel.FdroidViewModel
import com.app.market.viewmodel.ThemeSettingsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun FdroidRepositoriesScreen(viewModel: ThemeSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    MarketScaffold(title = stringResource(Res.string.fdroid_repositories), onBack = onBack) { padding, backdrop, behavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(backdrop).scrollEndHaptic().overScrollVertical()
                .nestedScroll(behavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 12.dp,
                end = padding.calculateEndPadding(layoutDirection) + 12.dp,
                top = padding.calculateTopPadding() + PageVerticalPadding,
                bottom = padding.calculateBottomPadding() + PageVerticalPadding,
            ),
        ) {
            item {
                CardSegmentContainer(isFirst = true, isLast = false, horizontalPadding = 0.dp) {
                    SwitchPreference(
                        title = stringResource(Res.string.fdroid_enabled),
                        summary = stringResource(Res.string.fdroid_enabled_summary),
                        checked = state.fdroidEnabled,
                        onCheckedChange = viewModel::setFdroidEnabled,
                    )
                }
            }
            item {
                CardSegmentContainer(isFirst = false, isLast = true, horizontalPadding = 0.dp) {
                    val sources = listOf(
                        stringResource(Res.string.fdroid_official),
                        stringResource(Res.string.fdroid_tuna),
                    )
                    WindowDropdownPreference(
                        title = stringResource(Res.string.fdroid_source),
                        summary = sources[if (state.fdroidSource == "tuna") 1 else 0],
                        items = sources,
                        selectedIndex = if (state.fdroidSource == "tuna") 1 else 0,
                        onSelectedIndexChange = { viewModel.setFdroidSource(if (it == 1) "tuna" else "official") },
                    )
                }
            }
        }
    }
}

@Composable
fun FdroidTab(bottomPadding: Dp) {
    val settings = koinInject<ThemeSettingsViewModel>()
    val viewModel = koinInject<FdroidViewModel>()
    val state by settings.uiState.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    LaunchedEffect(state.fdroidSource) { viewModel.load(state.fdroidSource) }
    MainTabScaffold(title = stringResource(Res.string.nav_fdroid)) { topPadding, backdrop, behavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().then(backdrop).scrollEndHaptic().overScrollVertical()
                .nestedScroll(behavior.nestedScrollConnection),
            contentPadding = PaddingValues(top = topPadding + PageVerticalPadding, bottom = bottomPadding + PageVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextField(value = ui.query, onValueChange = viewModel::setQuery,
                        label = stringResource(Res.string.search_hint), singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
            if (ui.loading) item {
                Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    InfiniteProgressIndicator(size = 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.loading))
                }
            }
            if (ui.error.isNotBlank()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ui.error, color = MiuixTheme.colorScheme.error)
                        Text(stringResource(Res.string.retry), modifier = Modifier.clickable { viewModel.load(state.fdroidSource, true) },
                            color = MiuixTheme.colorScheme.primary)
                    }
                }
            }
            if (ui.selected != null) {
                item(key = "detail") {
                    val selected = ui.selected!!
                    FdroidDetailCard(selected, downloadStates[selected.packageName],
                        { viewModel.select(null) }, { viewModel.install(selected) }, viewModel::installDownloaded, viewModel::cancel)
                }
            } else {
                items(ui.filteredApps, key = { it.packageName }) { app ->
                    FdroidAppRow(app, downloadStates[app.packageName], { viewModel.select(app) }, { viewModel.install(app) }, viewModel::installDownloaded, viewModel::cancel)
                }
            }
        }
    }
}

@Composable
private fun FdroidAppRow(app: FdroidApp, downloadState: DownloadState?, onOpen: () -> Unit, onInstall: () -> Unit, onInstallDownloaded: (String) -> Unit, onCancel: (String) -> Unit) {
    var iconIndex by remember(app.packageName) { mutableIntStateOf(0) }
    val iconUrls = remember(app.packageName, app.iconUrl, app.iconFallbackUrls) { listOf(app.iconUrl).filter(String::isNotBlank) + app.iconFallbackUrls }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (iconUrls.isNotEmpty()) AsyncImage(
                model = iconUrls.getOrNull(iconIndex), contentDescription = null, modifier = Modifier.size(52.dp),
                onError = { if (iconIndex < iconUrls.lastIndex) iconIndex++ },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.summary.ifBlank { app.packageName }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
                Text("${app.versionName} · ${formatSize(app.size)}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2)
            }
            Spacer(Modifier.width(8.dp))
            FdroidAction(app.packageName, downloadState, onInstall, onInstallDownloaded, onCancel)
        }
    }
}

@Composable
private fun FdroidDetailCard(app: FdroidApp, downloadState: DownloadState?, onBack: () -> Unit, onInstall: () -> Unit, onInstallDownloaded: (String) -> Unit, onCancel: (String) -> Unit) {
    var iconIndex by remember(app.packageName) { mutableIntStateOf(0) }
    val iconUrls = remember(app.packageName, app.iconUrl, app.iconFallbackUrls) { listOf(app.iconUrl).filter(String::isNotBlank) + app.iconFallbackUrls }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("‹  ${stringResource(Res.string.back)}", Modifier.clickable(onClick = onBack), color = MiuixTheme.colorScheme.primary)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (iconUrls.isNotEmpty()) AsyncImage(
                    model = iconUrls.getOrNull(iconIndex), contentDescription = null, modifier = Modifier.size(56.dp),
                    onError = { if (iconIndex < iconUrls.lastIndex) iconIndex++ },
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.name, style = MiuixTheme.textStyles.title2)
                    Text(app.packageName, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Text("版本 ${app.versionName} · ${formatSize(app.size)}", style = MiuixTheme.textStyles.body2)
            Text(app.description.ifBlank { app.summary }, maxLines = 16, overflow = TextOverflow.Ellipsis)
            FdroidAction(app.packageName, downloadState, onInstall, onInstallDownloaded, onCancel, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FdroidAction(packageName: String, state: DownloadState?, onInstall: () -> Unit, onInstallDownloaded: (String) -> Unit, onCancel: (String) -> Unit, modifier: Modifier = Modifier) {
    AppActionButton(
        packageName = packageName,
        actionText = stringResource(Res.string.install),
        actionKind = AppActionKind.INSTALL,
        downloadState = state,
        modifier = modifier,
        onAction = onInstall,
        onResumeDownload = onInstall,
        onInstallDownloaded = onInstallDownloaded,
        onCancel = onCancel,
    )
}

