package io.github.markyu0401.everheard.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.markyu0401.everheard.R
import io.github.markyu0401.everheard.capture.guards.ForegroundAppMonitor
import io.github.markyu0401.everheard.data.settings.excludedAppsStore
import io.github.markyu0401.everheard.ui.LocalizedActivity
import io.github.markyu0401.everheard.ui.components.BackButton
import io.github.markyu0401.everheard.ui.components.EverheardCard
import io.github.markyu0401.everheard.ui.components.Hairline
import io.github.markyu0401.everheard.ui.components.ScreenHeader
import io.github.markyu0401.everheard.ui.components.SearchField
import io.github.markyu0401.everheard.ui.components.SectionLabel
import io.github.markyu0401.everheard.ui.components.TextAction
import io.github.markyu0401.everheard.ui.theme.EverheardAppTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExcludedAppsActivity : LocalizedActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EverheardAppTheme {
                ExcludedAppsScreen(onBack = { finish() })
            }
        }
    }
}

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

@Composable
private fun ExcludedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember(context) { context.excludedAppsStore() }
    val monitor = remember(context) { ForegroundAppMonitor(context) }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var selected by remember { mutableStateOf(store.load()) }
    var query by remember { mutableStateOf("") }
    var usageGranted by remember { mutableStateOf(monitor.hasUsageAccess()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val installed = withContext(Dispatchers.IO) { loadApps(context) }
        val validPackages = installed.mapTo(mutableSetOf()) { it.packageName }
        val cleaned = selected.intersect(validPackages)
        apps = installed
        selected = cleaned
        store.save(cleaned)
        loading = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = monitor.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toggle(packageName: String) {
        val next = if (packageName in selected) selected - packageName else selected + packageName
        selected = next
        store.save(next)
    }

    val filtered = remember(apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(needle, ignoreCase = true) ||
                    app.packageName.contains(needle, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp)
        ) {
            BackButton(onClick = onBack)
            ScreenHeader(
                title = stringResource(R.string.excluded_apps_title),
                subtitle = stringResource(R.string.excluded_apps_subtitle)
            )
            Spacer(Modifier.height(16.dp))

            UsageAccessCard(
                granted = usageGranted,
                onOpenSettings = { openUsageAccessSettings(context) }
            )

            Spacer(Modifier.height(16.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.excluded_apps_search_hint)
            )

            Spacer(Modifier.height(12.dp))
            SectionLabel(
                text = stringResource(R.string.excluded_apps_title),
                trailing = stringResource(R.string.excluded_apps_selected, selected.size)
            )

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.excluded_apps_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.excluded_apps_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            selected = app.packageName in selected,
                            onToggle = { toggle(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageAccessCard(
    granted: Boolean,
    onOpenSettings: () -> Unit
) {
    EverheardCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.excluded_apps_usage_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (granted) {
                            R.string.excluded_apps_usage_granted
                        } else {
                            R.string.excluded_apps_usage_not_granted
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.excluded_apps_usage_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!granted) {
                Spacer(Modifier.height(6.dp))
                TextAction(
                    text = stringResource(R.string.excluded_apps_open_settings),
                    onClick = onOpenSettings,
                    showArrow = false
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val icon = remember(app.packageName) {
        app.icon.toBitmap(width = 48, height = 48).asImageBitmap()
    }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        Hairline(modifier = Modifier.padding(start = 60.dp))
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            AppEntry(
                packageName = packageName,
                label = info.loadLabel(packageManager).toString(),
                icon = info.loadIcon(packageManager)
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun openUsageAccessSettings(context: Context) {
    val packageUri = Uri.parse("package:${context.packageName}")
    val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri)
    runCatching { context.startActivity(specific) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
}
