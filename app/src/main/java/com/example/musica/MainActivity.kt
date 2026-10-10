package com.example.musica

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musica.data.MusicRepository
import com.example.musica.player.PlayerViewModel
import com.example.musica.telegram.TelegramConfig
import com.example.musica.ui.components.MiniPlayer
import com.example.musica.ui.screens.AlbumSongsScreen
import com.example.musica.ui.screens.AlbumsScreen
import com.example.musica.ui.screens.ArtistsScreen
import com.example.musica.ui.screens.DetailScreen
import com.example.musica.ui.screens.ExploreScreen
import com.example.musica.ui.screens.GenresScreen
import com.example.musica.ui.screens.HomeScreen
import com.example.musica.ui.screens.LibraryScreen
import com.example.musica.ui.screens.NowPlayingScreen
import com.example.musica.ui.screens.PlaylistsScreen
import com.example.musica.ui.screens.QueueScreen
import com.example.musica.ui.screens.SettingsScreen
import com.example.musica.ui.screens.SongListScreen
import com.example.musica.ui.screens.SplashScreen
import com.example.musica.ui.screens.TelegramSetupScreen
import com.example.musica.ui.theme.BottomBarBg
import com.example.musica.ui.theme.MusicaTheme
import com.example.musica.ui.theme.NavyBg
import com.example.musica.ui.theme.Purple
import com.example.musica.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicRepository.init(applicationContext)
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

    LaunchedEffect(Unit) {
        if (TelegramConfig.token(context).isNotBlank() && MusicRepository.songs.value.isEmpty()) {
            try { MusicRepository.sync() } catch (_: Exception) {}
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
        NavHost(nav, startDestination = "splash", modifier = Modifier.padding(pad)) {
            composable("splash") { SplashScreen(nav) }
            composable("home") { HomeScreen(nav, vm) }
            composable("explore") { ExploreScreen(nav) }
            composable("library") { LibraryScreen(nav, vm) }
            composable("player") { NowPlayingScreen(nav, vm) }
            composable("queue") { QueueScreen(nav, vm) }
            composable("playlists") { PlaylistsScreen(nav) }
            composable("playlist/{id}") { b ->
                DetailScreen(nav, vm, b.arguments?.getString("id").orEmpty())
            }
            composable("artists") { ArtistsScreen(nav) }
            composable("settings") { SettingsScreen(nav) }
            composable("genres") { GenresScreen(nav) }
            composable("albums") { AlbumsScreen(nav) }
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
