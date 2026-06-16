package com.muvio.shared.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muvio.shared.domain.Addon
import com.muvio.shared.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

private val BgDark = Color(0xFF0A0A0A)
private val SurfaceCard = Color(0xFF161616)
private val DividerColor = Color(0xFF242424)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF888888)
private val AccentTeal = Color(0xFF00C8A0)
private val AccentRed = Color(0xFFCF6679)
private val IconBg = Color(0xFF1E1E1E)

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    var showAddonInstall by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "Settings",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── Content ────────────────────────────────────────────────────
                item { SectionHeader("Content") }
                item {
                    SettingsRow(
                        title = "Addons",
                        subtitle = "${state.installedAddons.size} installed",
                        icon = Icons.Default.Extension,
                        onClick = { showAddonInstall = !showAddonInstall },
                    )
                }

                if (showAddonInstall) {
                    // Install addon input
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceCard)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = state.installUrl,
                                    onValueChange = viewModel::onInstallUrlChanged,
                                    placeholder = { Text("Manifest URL or stremio://…", color = TextSecondary, fontSize = 13.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        keyboard?.hide()
                                        viewModel.installAddon()
                                    }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1E1E1E),
                                        unfocusedContainerColor = Color(0xFF1E1E1E),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary,
                                        cursorColor = AccentTeal,
                                        focusedIndicatorColor = AccentTeal,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.isInstalling) {
                                    CircularProgressIndicator(
                                        color = AccentTeal,
                                        modifier = Modifier.size(20.dp).padding(start = 8.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    IconButton(onClick = {
                                        keyboard?.hide()
                                        viewModel.installAddon()
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = "Install", tint = AccentTeal)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 16.dp))
                    }

                    // Installed addon list
                    if (state.isAddonsLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = AccentTeal, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        items(state.installedAddons, key = { it.id }) { addon ->
                            AddonRow(
                                addon = addon,
                                onToggle = { viewModel.toggleAddon(addon.id) },
                                onRemove = { viewModel.removeAddon(addon.id) },
                            )
                        }
                    }
                }

                item {
                    SettingsRow(
                        title = "IPTV",
                        subtitle = "M3U playlists & EPG",
                        icon = Icons.Default.Tv,
                        onClick = {},
                    )
                }
                item {
                    SettingsRow(
                        title = "Catalogs",
                        subtitle = "Reorder and toggle rows",
                        icon = Icons.AutoMirrored.Filled.List,
                        onClick = {},
                        showDivider = false,
                    )
                }

                item { SectionSpacer() }

                // ── Playback ───────────────────────────────────────────────────
                item { SectionHeader("Playback") }
                item {
                    SettingsRow(
                        title = "Player",
                        subtitle = "Default quality and subtitles",
                        icon = Icons.Default.PlayArrow,
                        onClick = {},
                    )
                }
                item {
                    SettingsRow(
                        title = "Downloads",
                        subtitle = "Storage location and quality",
                        icon = Icons.Default.CloudDownload,
                        onClick = {},
                        showDivider = false,
                    )
                }

                item { SectionSpacer() }

                // ── Account ────────────────────────────────────────────────────
                item { SectionHeader("Account") }
                item {
                    SettingsRow(
                        title = "Trakt",
                        subtitle = "Sync watch history",
                        icon = Icons.Default.Sync,
                        onClick = {},
                    )
                }
                item {
                    SettingsRow(
                        title = "Profiles",
                        subtitle = "Manage user profiles",
                        icon = Icons.Default.Person,
                        onClick = {},
                        showDivider = false,
                    )
                }

                item { SectionSpacer() }

                // ── About ──────────────────────────────────────────────────────
                item { SectionHeader("About") }
                item {
                    SettingsRow(
                        title = "Version",
                        subtitle = "1.0.0",
                        icon = Icons.Default.Info,
                        onClick = {},
                        showChevron = false,
                        showDivider = false,
                    )
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        // Toast
        state.toastMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(2_500)
                viewModel.dismissToast()
            }
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = SurfaceCard,
                contentColor = TextPrimary,
            ) {
                Text(msg)
            }
        }
    }
}

@Composable
private fun AddonRow(
    addon: Addon,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = addon.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = "v${addon.version} · ${addon.type.name.lowercase()}", color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = addon.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentTeal,
                checkedTrackColor = AccentTeal.copy(alpha = 0.3f),
            ),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = AccentRed.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AccentTeal,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(top = 4.dp),
    )
}

@Composable
private fun SectionSpacer() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(horizontal = 16.dp))
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(SurfaceCard)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color = IconBg, shape = RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(text = title, color = TextPrimary, fontSize = 16.sp)
            subtitle?.let {
                Text(text = it, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 1.dp))
            }
        }

        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    if (showDivider) {
        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 66.dp))
    }
}
