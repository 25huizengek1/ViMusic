package it.vfsfitvnm.vimusic

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.util.DebugLogger
import com.kieronquinn.monetcompat.core.MonetActivityAccessException
import com.kieronquinn.monetcompat.core.MonetCompat
import com.kieronquinn.monetcompat.interfaces.MonetColorsChangedListener
import com.valentinilk.shimmer.LocalShimmerTheme
import com.valentinilk.shimmer.defaultShimmerTheme
import dev.kdrag0n.monet.theme.ColorScheme
import it.vfsfitvnm.compose.persist.LocalPersistMap
import it.vfsfitvnm.compose.persist.PersistMap
import it.vfsfitvnm.compose.preferences.PreferencesHolder
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.BrowseBody
import it.vfsfitvnm.innertube.requests.playlistPage
import it.vfsfitvnm.innertube.requests.song
import it.vfsfitvnm.vimusic.enums.ColorPaletteMode
import it.vfsfitvnm.vimusic.enums.ColorPaletteName
import it.vfsfitvnm.vimusic.preferences.AppearancePreferences
import it.vfsfitvnm.vimusic.preferences.DataPreferences
import it.vfsfitvnm.vimusic.service.PlayerService
import it.vfsfitvnm.vimusic.service.downloadState
import it.vfsfitvnm.vimusic.ui.components.BottomSheetMenu
import it.vfsfitvnm.vimusic.ui.components.LocalMenuState
import it.vfsfitvnm.vimusic.ui.components.rememberBottomSheetState
import it.vfsfitvnm.vimusic.ui.components.themed.LinearProgressIndicator
import it.vfsfitvnm.vimusic.ui.screens.albumRoute
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.home.HomeScreen
import it.vfsfitvnm.vimusic.ui.screens.player.Player
import it.vfsfitvnm.vimusic.ui.screens.playlistRoute
import it.vfsfitvnm.vimusic.ui.styling.Appearance
import it.vfsfitvnm.vimusic.ui.styling.Dimensions
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.ui.styling.colorPaletteOf
import it.vfsfitvnm.vimusic.ui.styling.dynamicColorPaletteOf
import it.vfsfitvnm.vimusic.ui.styling.typographyOf
import it.vfsfitvnm.vimusic.utils.LocalMonetCompat
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import it.vfsfitvnm.vimusic.utils.intent
import it.vfsfitvnm.vimusic.utils.invokeOnReady
import it.vfsfitvnm.vimusic.utils.isAtLeastAndroid6
import it.vfsfitvnm.vimusic.utils.isAtLeastAndroid8
import it.vfsfitvnm.vimusic.utils.isCompositionLaunched
import it.vfsfitvnm.vimusic.utils.setDefaultPalette
import it.vfsfitvnm.vimusic.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity(), MonetColorsChangedListener {
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is PlayerService.Binder) this@MainActivity.binder = service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binder = null
            // Try to rebind, otherwise fail
            unbindService(this)
            bindService(intent<PlayerService>(), this, Context.BIND_AUTO_CREATE)
        }
    }

    private var _monet: MonetCompat? by mutableStateOf(null)
    private val monet get() = _monet ?: throw MonetActivityAccessException()

    private var binder by mutableStateOf<PlayerService.Binder?>(null)

    override fun onStart() {
        super.onStart()
        bindService(intent<PlayerService>(), serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MonetCompat.setup(this)
        _monet = MonetCompat.getInstance()
        monet.setDefaultPalette()
        monet.addMonetColorsChangedListener(
            listener = this,
            notifySelf = false
        )
        monet.updateMonetColors()
        monet.invokeOnReady(block = ::setContent)

        onNewIntent(intent)
    }

    // TODO: clean up the theme mess
    @Suppress("CyclomaticComplexMethod")
    @OptIn(ExperimentalLayoutApi::class)
    fun setContent() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val launchedFromNotification = intent?.extras?.getBoolean("expandPlayerBottomSheet") == true

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val isSystemInDarkTheme = isSystemInDarkTheme()

            var appearance by rememberSaveable(
                isSystemInDarkTheme,
                isCompositionLaunched(),
                stateSaver = Appearance.AppearanceSaver
            ) {
                with(AppearancePreferences) {
                    val colorPalette = colorPaletteOf(
                        name = colorPaletteName,
                        mode = colorPaletteMode,
                        isDark = isSystemInDarkTheme
                    )

                    setSystemBarAppearance(colorPalette.isDark)

                    mutableStateOf(
                        Appearance(
                            colorPalette = colorPalette,
                            typography = typographyOf(
                                color = colorPalette.text,
                                useSystemFont = useSystemFont,
                                applyFontPadding = applyFontPadding
                            ),
                            thumbnailShapeCorners = thumbnailRoundness.dp
                        )
                    )
                }
            }

            DisposableEffect(binder, isSystemInDarkTheme) {
                var bitmapListenerJob: Job? = null
                var appearanceUpdaterJob: Job? = null

                fun setDynamicPalette(
                    name: ColorPaletteName,
                    mode: ColorPaletteMode
                ) {
                    val isDark = mode == ColorPaletteMode.Dark ||
                            (mode == ColorPaletteMode.System && isSystemInDarkTheme)

                    if (name == ColorPaletteName.MaterialYou) dynamicColorPaletteOf(
                        accentColor = monet.getAccentColor(
                            context = this@MainActivity,
                            darkMode = isDark
                        ),
                        isDark = isDark,
                        isAmoled = false
                    ).let {
                        appearance = appearance.copy(
                            colorPalette = it,
                            typography = appearance.typography.copy(it.text)
                        )
                    } else binder?.setBitmapListener { bitmap: Bitmap? ->
                        if (bitmap == null) {
                            val colorPalette = colorPaletteOf(
                                name = name,
                                mode = mode,
                                isDark = isSystemInDarkTheme
                            )

                            setSystemBarAppearance(colorPalette.isDark)

                            appearance = appearance.copy(
                                colorPalette = colorPalette,
                                typography = appearance.typography.copy(colorPalette.text)
                            )

                            return@setBitmapListener
                        }

                        bitmapListenerJob = coroutineScope.launch(Dispatchers.IO) {
                            dynamicColorPaletteOf(
                                bitmap = bitmap,
                                isDark = isDark,
                                isAmoled = name == ColorPaletteName.AMOLED
                            )?.let {
                                withContext(Dispatchers.Main) {
                                    setSystemBarAppearance(it.isDark)
                                }
                                appearance = appearance.copy(
                                    colorPalette = it,
                                    typography = appearance.typography.copy(it.text)
                                )
                            }
                        }
                    }
                }

                with(AppearancePreferences) {
                    fun setPalette(): Boolean {
                        if (!colorPaletteName.isDynamic) return false

                        setDynamicPalette(colorPaletteName, colorPaletteMode)
                        return true
                    }
                    setPalette()

                    appearanceUpdaterJob = coroutineScope.launch {
                        launch {
                            combine(
                                flow = colorPaletteNameProperty.stateFlow,
                                flow2 = colorPaletteModeProperty.stateFlow
                            ) { name, mode ->
                                val isStatic = setPalette()
                                if (!isStatic || name == ColorPaletteName.MaterialYou) {
                                    bitmapListenerJob?.cancel()
                                    binder?.setBitmapListener(null)
                                }
                                if (!isStatic) {
                                    val colorPalette = colorPaletteOf(
                                        name = name,
                                        mode = mode,
                                        isDark = isSystemInDarkTheme
                                    )

                                    setSystemBarAppearance(colorPalette.isDark)

                                    appearance = appearance.copy(
                                        colorPalette = colorPalette,
                                        typography = appearance.typography.copy(colorPalette.text)
                                    )
                                }
                            }.collect()
                        }
                        launch {
                            thumbnailRoundnessProperty.stateFlow.collectLatest {
                                appearance = appearance.copy(thumbnailShapeCorners = it.dp)
                            }
                        }
                        launch {
                            combine(
                                flow = useSystemFontProperty.stateFlow,
                                flow2 = applyFontPaddingProperty.stateFlow
                            ) { system, padding ->
                                appearance = appearance.copy(
                                    typography = typographyOf(
                                        appearance.colorPalette.text,
                                        system,
                                        padding
                                    )
                                )
                            }.collectLatest { }
                        }
                    }
                }

                onDispose {
                    bitmapListenerJob?.cancel()
                    appearanceUpdaterJob?.cancel()
                    binder?.setBitmapListener(null)
                }
            }

            val rippleTheme = remember(
                appearance.colorPalette.text,
                appearance.colorPalette.isDark
            ) {
                object : RippleTheme {
                    @Composable
                    override fun defaultColor(): Color = RippleTheme.defaultRippleColor(
                        contentColor = appearance.colorPalette.text,
                        lightTheme = !appearance.colorPalette.isDark
                    )

                    @Composable
                    override fun rippleAlpha(): RippleAlpha = RippleTheme.defaultRippleAlpha(
                        contentColor = appearance.colorPalette.text,
                        lightTheme = !appearance.colorPalette.isDark
                    )
                }
            }

            val shimmerTheme = remember {
                defaultShimmerTheme.copy(
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 800,
                            easing = LinearEasing,
                            delayMillis = 250
                        ),
                        repeatMode = RepeatMode.Restart
                    ),
                    shaderColors = listOf(
                        Color.Unspecified.copy(alpha = 0.25f),
                        Color.White.copy(alpha = 0.50f),
                        Color.Unspecified.copy(alpha = 0.25f)
                    )
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appearance.colorPalette.background0)
            ) {
                val density = LocalDensity.current
                val windowsInsets = WindowInsets.systemBars
                val bottomDp = with(density) { windowsInsets.getBottom(density).toDp() }

                val imeVisible = WindowInsets.isImeVisible
                val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
                val animatedBottomDp by animateDpAsState(
                    targetValue = if (imeVisible) 0.dp else bottomDp,
                    label = ""
                )

                val playerBottomSheetState = rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = Dimensions.items.collapsedPlayerHeight + bottomDp,
                    expandedBound = maxHeight
                )

                val playerAwareWindowInsets by remember(
                    bottomDp,
                    animatedBottomDp,
                    playerBottomSheetState.value,
                    imeVisible,
                    imeBottomDp
                ) {
                    derivedStateOf {
                        val bottom =
                            if (imeVisible) imeBottomDp.coerceAtLeast(playerBottomSheetState.value)
                            else playerBottomSheetState.value.coerceIn(
                                animatedBottomDp..playerBottomSheetState.collapsedBound
                            )

                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(bottom = bottom))
                    }
                }

                CompositionLocalProvider(
                    LocalAppearance provides appearance,
                    LocalIndication provides rememberRipple(bounded = true),
                    LocalRippleTheme provides rippleTheme,
                    LocalShimmerTheme provides shimmerTheme,
                    LocalPlayerServiceBinder provides binder,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalLayoutDirection provides LayoutDirection.Ltr,
                    LocalPersistMap provides Dependencies.application.persistMap,
                    LocalMonetCompat provides monet
                ) {
                    val isDownloading by downloadState.collectAsState()

                    Box {
                        HomeScreen(
                            onPlaylistUrl = { url ->
                                onNewIntent(Intent.parseUri(url, 0))
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = isDownloading,
                        modifier = Modifier.padding(playerAwareWindowInsets.asPaddingValues())
                    ) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }

                    CompositionLocalProvider(
                        LocalAppearance provides if (
                            AppearancePreferences.colorPaletteName == ColorPaletteName.AMOLED
                        ) appearance.copy(
                            colorPalette = dynamicColorPaletteOf(
                                accentColor = appearance.colorPalette.accent,
                                isDark = true,
                                isAmoled = false
                            )
                        ) else appearance
                    ) {
                        Player(
                            layoutState = playerBottomSheetState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .imePadding()
                    )
                }

                DisposableEffect(binder?.player) {
                    val player = binder?.player ?: return@DisposableEffect onDispose { }

                    if (player.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) playerBottomSheetState.dismiss()
                    } else {
                        if (playerBottomSheetState.isDismissed) {
                            if (launchedFromNotification) {
                                intent.replaceExtras(Bundle())
                                playerBottomSheetState.expand(tween(700))
                            } else playerBottomSheetState.collapse(tween(700))
                        }
                    }

                    val listener = object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && mediaItem != null) {
                                if (mediaItem.mediaMetadata.extras?.getBoolean("isFromPersistentQueue") != true) {
                                    playerBottomSheetState.expand(tween(500))
                                } else playerBottomSheetState.collapse(tween(700))
                            }
                        }
                    }

                    player.addListener(listener)

                    onDispose { player.removeListener(listener) }
                }
            }
        }
    }

    @Suppress("CyclomaticComplexMethod")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        val uri = intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri() ?: return

        intent?.data = null
        this.intent = null

        Log.d("MainActivity", "Opening url: $uri")

        lifecycleScope.launch(Dispatchers.IO) {
            when (val path = uri.pathSegments.firstOrNull()) {
                "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                    val browseId = "VL$playlistId"

                    if (playlistId.startsWith("OLAK5uy_")) {
                        Innertube.playlistPage(BrowseBody(browseId = browseId))?.getOrNull()?.let {
                            it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                                albumRoute.ensureGlobal(browseId)
                            }
                        }
                    } else {
                        playlistRoute.ensureGlobal(browseId, uri.getQueryParameter("params"), null)
                    }
                }

                "channel", "c" -> uri.lastPathSegment?.let { channelId ->
                    artistRoute.ensureGlobal(channelId)
                }

                else -> when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> path
                    else -> {
                        toast(getString(R.string.error_url, uri))
                        null
                    }
                }?.let { videoId ->
                    Innertube.song(videoId)?.getOrNull()?.let { song ->
                        val binder = snapshotFlow { binder }.filterNotNull().first()
                        withContext(Dispatchers.Main) {
                            binder.player.forcePlay(song.asMediaItem)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monet.removeMonetColorsChangedListener(this)
        _monet = null
    }

    override fun onStop() {
        unbindService(serviceConnection)
        super.onStop()
    }

    override fun onMonetColorsChanged(
        monet: MonetCompat,
        monetColors: ColorScheme,
        isInitialChange: Boolean
    ) {
        if (!isInitialChange) recreate()
    }

    private fun setSystemBarAppearance(isDark: Boolean) {
        with(WindowCompat.getInsetsController(window, window.decorView.rootView)) {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }

        if (!isAtLeastAndroid6) window.statusBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()

        if (!isAtLeastAndroid8) window.navigationBarColor =
            (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
    }
}

val LocalPlayerServiceBinder = staticCompositionLocalOf<PlayerService.Binder?> { null }

val LocalPlayerAwareWindowInsets =
    staticCompositionLocalOf<WindowInsets> { error("No player insets provided") }

class MainApplication : Application(), ImageLoaderFactory, Configuration.Provider {
    override fun onCreate() {
        MonetCompat.debugLog = BuildConfig.DEBUG
        super.onCreate()

        Dependencies.init(this)
        MonetCompat.enablePaletteCompat()
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .crossfade(true)
        .respectCacheHeaders(false)
        .diskCache(
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes(DataPreferences.coilDiskCacheMaxSize.bytes)
                .build()
        )
        .let { if (BuildConfig.DEBUG) it.logger(DebugLogger()) else it }
        .build()

    val persistMap = PersistMap()

    override val workManagerConfiguration = Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
        .build()
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    internal fun init(application: MainApplication) {
        this.application = application
        DatabaseInitializer()
    }
}

open class GlobalPreferencesHolder : PreferencesHolder(Dependencies.application, "preferences")
