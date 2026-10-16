package com.example.musica

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musica.data.DownloadRepository
import com.example.musica.data.FavoritesRepository
import com.example.musica.data.HistoryRepository
import com.example.musica.data.MusicRepository
import com.example.musica.data.PlayStatsRepository
import com.example.musica.data.PlaylistRepository
import com.example.musica.player.PlayerViewModel
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.screens.AlbumSongsScreen
import com.example.musica.ui.screens.AlbumsScreen
import com.example.musica.ui.screens.ArtistSongsScreen
import com.example.musica.ui.screens.ArtistsScreen
import com.example.musica.ui.screens.DetailScreen
import com.example.musica.ui.screens.DownloadsScreen
import com.example.musica.ui.screens.ExploreScreen
import com.example.musica.ui.screens.GenresScreen
import com.example.musica.ui.screens.HistoryScreen
import com.example.musica.ui.screens.HomeScreen
import com.example.musica.ui.screens.LibraryScreen
import com.example.musica.ui.screens.NowPlayingScreen
import com.example.musica.ui.screens.PlaylistsScreen
import com.example.musica.ui.screens.QueueScreen
import com.example.musica.ui.screens.SearchScreen
import com.example.musica.ui.screens.SettingsScreen
import com.example.musica.ui.screens.SongListScreen
import com.example.musica.ui.screens.SplashScreen
import com.example.musica.ui.screens.TelegramSetupScreen
import com.example.musica.ui.screens.UserPlaylistScreen
import com.example.musica.ui.theme.BottomBarBg
import com.example.musica.ui.theme.MusicaTheme
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.musica.ui.screens.SmartListScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicRepository.init(applicationContext)
        PlaylistRepository.init(applicationContext)
        PlayStatsRepository.init(applicationContext)
        FavoritesRepository.init(applicationContext)
        DownloadRepository.init(applicationContext)
        HistoryRepository.init(applicationContext)
        setContent {
            MusicaTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: PlayerViewModel = viewModel()) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf("home", "explore", "library")
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // BUCLE AUTOMÁTICO — arranca cuando la biblioteca cargó
    val ready by MusicRepository.ready.collectAsState()
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect

        if (TelegramConfig.token(context).isNotBlank() && MusicRepository.songs.value.isEmpty()) {
            try { MusicRepository.sync() } catch (_: Exception) {}
        }
        var cycles = 0
        while (isActive) {
            if (DownloadRepository.offlineMode.value) {
                delay(3000)
                continue
            }
            val meta = MusicRepository.pendingCount()
            val online = MusicRepository.pendingOnlineCount()
            val covers = MusicRepository.pendingCoverCount()
            when {
                meta > 0 -> {
                    try { MusicRepository.scanPending() } catch (_: Exception) {}
                    delay(150)
                }
                online > 0 -> {
                    try { MusicRepository.enrichOnline() } catch (_: Exception) {}
                    delay(500)
                }
                covers > 0 -> {
                    try { MusicRepository.fetchMissingCovers() } catch (_: Exception) {}
                    delay(800)
                }
                else -> {
                    delay(60_000)
                    try { MusicRepository.sync() } catch (_: Exception) {}
                }
            }
            cycles++
            if (cycles % 15 == 0) {
                try { MusicRepository.sync() } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        containerColor = NavyBg,
        bottomBar = {
            if (route != null && route in tabs) {
                Column {
                    if (vm.current != null) MiniPlayer(vm) { nav.navigate("player") }
                    BottomNavBar(nav)
                }
            }
        }
    ) { pad ->
        NavHost(
            nav,
            startDestination = "splash",
            modifier = Modifier.padding(pad),
            // ---------- Transiciones vistosas en toda la app ----------
            // · Pantallas normales: entran deslizándose desde la derecha
            //   con fundido; al volver, se invierten (la actual sale hacia
            //   la derecha y la anterior entra desde la izquierda).
            // · El reproductor ("player") entra de abajo hacia arriba como
            //   una hoja a pantalla completa y al cerrarse baja de nuevo.
            enterTransition = {
                when (targetState.destination.route) {
                    "player" -> slideInVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) { it } + fadeIn(animationSpec = tween(240))
                    else -> slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { it / 4 } + fadeIn(animationSpec = tween(220))
                }
            },
            exitTransition = {
                when (targetState.destination.route) {
                    "player" -> fadeOut(animationSpec = tween(180))
                    else -> slideOutHorizontally(animationSpec = tween(220)) { -it / 6 } +
                            fadeOut(animationSpec = tween(180))
                }
            },
            popEnterTransition = {
                when (targetState.destination.route) {
                    "player" -> fadeIn(animationSpec = tween(240))
                    else -> slideInHorizontally(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { -it / 6 } + fadeIn(animationSpec = tween(220))
                }
            },
            popExitTransition = {
                when (initialState.destination.route) {
                    "player" -> slideOutVertically(
                        animationSpec = tween(320, easing = FastOutSlowInEasing)
                    ) { it } + fadeOut(animationSpec = tween(240))
                    else -> slideOutHorizontally(animationSpec = tween(220)) { it / 4 } +
                            fadeOut(animationSpec = tween(180))
                }
            }
        ) {
            composable("splash") { SplashScreen(nav) }
            composable("home") { HomeScreen(nav, vm) }
            composable("explore") { ExploreScreen(nav, vm) }
            composable("library") { LibraryScreen(nav, vm) }
            composable("player") { NowPlayingScreen(nav, vm) }
            composable("queue") { QueueScreen(nav, vm) }
            composable("history") { HistoryScreen(nav, vm) }
            composable("playlists") { PlaylistsScreen(nav) }
            composable("playlist/{id}") { b ->
                DetailScreen(nav, vm, b.arguments?.getString("id").orEmpty())
            }
            composable("userplaylist/{id}") { b ->
                UserPlaylistScreen(nav, vm, b.arguments?.getString("id").orEmpty())
            }
            composable("downloads") { DownloadsScreen(nav, vm) }
            composable("smart/{type}") { b ->
                SmartListScreen(nav, vm, b.arguments?.getString("type").orEmpty())
            }
            composable("artists") { ArtistsScreen(nav) }
            composable("artist/{name}") { b ->
                ArtistSongsScreen(nav, vm, Uri.decode(b.arguments?.getString("name").orEmpty()))
            }
            composable("settings") { SettingsScreen(nav) }
            composable("genres") { GenresScreen(nav) }
            composable("albums") { AlbumsScreen(nav) }
            composable("search") { SearchScreen(nav, vm) }
            composable("genre/{name}") { b ->
                SongListScreen(nav, vm, Uri.decode(b.arguments?.getString("name").orEmpty()))
            }
            composable("album/{name}") { b ->
                AlbumSongsScreen(nav, vm, Uri.decode(b.arguments?.getString("name").orEmpty()))
            }
            composable("telegram") { TelegramSetupScreen(nav) }
        }
    }
}

@Composable
fun BottomNavBar(nav: NavHostController) {
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    NavigationBar(containerColor = BottomBarBg, tonalElevation = 0.dp) {
        listOf(
            Triple("home", "Inicio", Icons.Filled.Home),
            Triple("explore", "Explorar", Icons.Filled.Search),
            Triple("library", "Biblioteca", Icons.Filled.LibraryMusic)
        ).forEach { (r, label, icon) ->
            NavigationBarItem(
                selected = route == r,
                onClick = {
                    nav.navigate(r) {
                        popUpTo("home") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, null) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color.White,
                    selectedTextColor = Purple,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
