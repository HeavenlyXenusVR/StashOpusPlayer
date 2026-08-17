package com.stash.opusplayer.ui

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.bumptech.glide.Glide
import com.stash.opusplayer.BuildConfig
import com.google.android.material.navigation.NavigationView
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.stash.opusplayer.R
import com.stash.opusplayer.databinding.ActivityMainBinding
import com.stash.opusplayer.bridge.DiscordLoginEvents
import com.stash.opusplayer.bridge.DiscordLoginOutcome
import com.stash.opusplayer.security.AppLockManager
import com.stash.opusplayer.ui.fragments.MusicLibraryFragment
import com.stash.opusplayer.ui.fragments.EqualizerFragment
import com.stash.opusplayer.ui.fragments.SettingsFragment
import com.stash.opusplayer.ui.fragments.PlaylistsFragment
import com.stash.opusplayer.ui.fragments.YouTubeSearchFragment
import com.stash.opusplayer.utils.PermissionUtils
import com.stash.opusplayer.updates.UpdateManager
import com.stash.opusplayer.player.MusicPlayerManager
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.ui.appearance.ThemeManager
import com.stash.opusplayer.ui.appearance.AppearancePreferences
import com.stash.opusplayer.ui.appearance.VisualCustomizationManager
import com.stash.opusplayer.ui.MiniPlayerSurface
import com.stash.opusplayer.ui.managers.MiniPlayerToggleManager
import android.content.IntentFilter
import android.content.BroadcastReceiver
import com.stash.opusplayer.ui.themes.GenreBasedThemeManager
import com.stash.opusplayer.ui.physics.PhysicsAnimationEngine
import com.stash.opusplayer.utils.UIPerformanceOptimizer
import com.stash.opusplayer.utils.AnimationDurationManager
import com.stash.opusplayer.utils.AnimationUtils

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var updateManager: UpdateManager
    private lateinit var musicPlayerManager: MusicPlayerManager
    private lateinit var miniPlayerView: MiniPlayerSurface
    private lateinit var miniPlayerToggleManager: MiniPlayerToggleManager
    private var currentMiniPlayerStyle: String? = null

    // App Lock
    private var wentToBackground = false
    private var appLockOverlay: View? = null

    @javax.inject.Inject
    lateinit var discordLoginEvents: DiscordLoginEvents

    @javax.inject.Inject
    lateinit var settingsSyncManager: com.stash.opusplayer.bridge.SettingsSyncManager

    // Appearance customization
    private var appearanceReceiver: BroadcastReceiver? = null
    private lateinit var visualCustomizationManager: VisualCustomizationManager
    private lateinit var genreThemeManager: GenreBasedThemeManager
    private lateinit var physicsAnimationEngine: PhysicsAnimationEngine
    
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { setBackgroundImage(it) }
    }

    private var pendingMediaDeleteCallback: ((Boolean) -> Unit)? = null
    private var pendingMediaDeleteUri: Uri? = null

    private val mediaDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val callback = pendingMediaDeleteCallback
        val uri = pendingMediaDeleteUri
        pendingMediaDeleteCallback = null
        pendingMediaDeleteUri = null
        val granted = result.resultCode == android.app.Activity.RESULT_OK
        if (granted && uri != null && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // API 29's consent dialog only grants permission -- the delete call itself has to
            // be reissued now that it will actually succeed instead of throwing again.
            val rows = runCatching { contentResolver.delete(uri, null, null) }.getOrDefault(0)
            callback?.invoke(rows > 0)
        } else {
            callback?.invoke(granted)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Start performance tracking
        UIPerformanceOptimizer.startPerformanceTracking("MainActivity.onCreate")
        
        // Apply appearance theme BEFORE setting content view
        applyAppearanceTheme()
        
        // Apply activity-level optimizations
        UIPerformanceOptimizer.optimizeActivity(this)
        UIPerformanceOptimizer.optimizeAnimationsBasedOnSettings(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Optimize the root view hierarchy
        UIPerformanceOptimizer.optimizeLayoutMeasurement(binding.root)
        
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        updateManager = UpdateManager(this)
        visualCustomizationManager = VisualCustomizationManager(this)
        genreThemeManager = GenreBasedThemeManager(this)
        physicsAnimationEngine = PhysicsAnimationEngine(this)
        miniPlayerToggleManager = MiniPlayerToggleManager(this)
        
        // Set physics engine in AnimationUtils for global use
        AnimationUtils.setPhysicsEngine(physicsAnimationEngine)
        
        setupMusicPlayer()
        setupMiniPlayer()
        setupToolbar()
        setupNavigationDrawer()
        setupBackgroundImage()
        applyVisualCustomization()
        
        // Apply appearance customizations after views are set up
        applyAppearanceToViews()
        
        setupBottomNavigation()
        checkPermissionsAndSetup()
        requestNotificationPermissionIfNeeded()
        handleDiscordVerifyDeepLink(intent)
        handleDiscordLoginDeepLink(intent)
        settingsSyncManager.pullOnce()

        // Observe image download tracker to show top banner
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.stash.opusplayer.utils.ImageDownloadTracker.active.collect { count ->
                    val banner = findViewById<android.view.View>(com.stash.opusplayer.R.id.download_banner)
                    val text = findViewById<android.widget.TextView>(com.stash.opusplayer.R.id.download_text)
                    if (count > 0) {
                        banner.visibility = android.view.View.VISIBLE
                        text.text = "Downloading images… ($count)"
                    } else {
                        banner.visibility = android.view.View.GONE
                    }
                }
            }
        }

        // Observe library scanning status (filter out background metadata operations)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                com.stash.opusplayer.utils.LibraryScanTracker.status.collect { msg ->
                    val banner = findViewById<android.view.View>(com.stash.opusplayer.R.id.scanning_banner)
                    val text = findViewById<android.widget.TextView>(com.stash.opusplayer.R.id.scanning_text)
                    
                    // Filter out background metadata scanning and processing messages
                    val shouldShow = msg.isNotBlank() && 
                                   !msg.contains("metadata", ignoreCase = true) &&
                                   !msg.contains("processing", ignoreCase = true) &&
                                   !msg.contains("extracting", ignoreCase = true)
                    
                    if (shouldShow) {
                        banner.visibility = android.view.View.VISIBLE
                        text.text = msg
                    } else {
                        banner.visibility = android.view.View.GONE
                    }
                }
            }
        }

// Removed custom loading overlay; using Android SplashScreen API instead
        
        // End performance tracking
        UIPerformanceOptimizer.endPerformanceTracking("MainActivity.onCreate")
        
        // Check for updates on app start (AI will decide if/when to show)
        // Defer a bit to avoid competing with first render and permission prompts
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000)
            updateManager.checkForUpdates(this@MainActivity)
        }
        
        // Defer loading content until permissions are granted
        if (savedInstanceState != null) {
            // If recreating, assume content already loaded
        }
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (appLockOverlay != null) {
                    moveTaskToBack(true)
                } else if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }
    
    override fun onStart() {
        super.onStart()
        registerAppearanceReceiver()
    }
    
    override fun onStop() {
        super.onStop()
        unregisterAppearanceReceiver()
        if (AppLockManager.isEnabled(this)) {
            wentToBackground = true
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
        when (level) {
            android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Critical memory situation - trim aggressively
                UIPerformanceOptimizer.trimMemoryOnLowMemory(this)
            }
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                // App is in background - trim non-essential memory
                Glide.get(this).clearMemory()
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Optimize for configuration changes
        UIPerformanceOptimizer.optimizeForConfigurationChange(this)
        
        // Re-apply appearance settings for new configuration
        applyAppearanceTheme()
        applyAppearanceToViews()
    }
    
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 1001)
            }
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.app_name)
    }
    
    private fun setupBottomNavigation() {
        // Optimize touch responsiveness for bottom navigation
        UIPerformanceOptimizer.optimizeTouchResponsiveness(binding.bottomNav)
        
        // Animate bottom navigation on startup with optimized duration
        binding.bottomNav.alpha = 0f
        binding.bottomNav.translationY = 100f
        binding.bottomNav.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(AnimationDurationManager.getOptimizedDuration(500).toLong())
            .setStartDelay(AnimationDurationManager.getOptimizedDuration(300).toLong())
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
            
        binding.bottomNav.setOnItemSelectedListener { item ->
            // Add click animation to navigation items
            val selectedView = binding.bottomNav.findViewById<android.view.View>(item.itemId)
            AnimationUtils.animateButtonPress(selectedView)
            
            when (item.itemId) {
                R.id.nav_songs -> {
                    loadFragment(MusicLibraryFragment())
                    supportActionBar?.title = getString(R.string.menu_music_library)
                    true
                }
                R.id.nav_liked -> {
                    loadFragment(com.stash.opusplayer.ui.fragments.FavoritesFragment())
                    supportActionBar?.title = "Liked Songs"
                    true
                }
                R.id.nav_folders -> {
                    loadFragment(com.stash.opusplayer.ui.fragments.FoldersFragment())
                    supportActionBar?.title = getString(R.string.menu_folders)
                    true
                }
                R.id.nav_youtube -> {
                    loadFragment(YouTubeSearchFragment())
                    supportActionBar?.title = "YouTube Search"
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    supportActionBar?.title = getString(R.string.menu_settings)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupNavigationDrawer() {
        toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.nav_open, R.string.nav_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        binding.navView.setNavigationItemSelectedListener(this)
    }
    
    private fun setupBackgroundImage() {
        val backgroundUri = sharedPreferences.getString("background_image_uri", null)
        backgroundUri?.let { uri ->
            binding.backgroundImage.visibility = android.view.View.VISIBLE
            Glide.with(this)
                .load(Uri.parse(uri))
                .into(binding.backgroundImage)
        }
    }
    
    private fun setBackgroundImage(uri: Uri) {
        try {
            // Take persistent permission
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            
            // Save URI to appearance preferences
            val currentPrefs = AppearancePreferences.fromPrefs(this)
            currentPrefs.copy(backgroundImageUri = uri.toString()).saveToPrefs(this)
            
            // Also save to legacy preference for compatibility
            sharedPreferences.edit()
                .putString("background_image_uri", uri.toString())
                .apply()
            
            // Display image
            binding.backgroundImage.visibility = android.view.View.VISIBLE
            Glide.with(this)
                .load(uri)
                .into(binding.backgroundImage)
            
            // Apply background overlay with current dim/blur settings
            ThemeManager.applyBackgroundOverlay(this, currentPrefs.copy(backgroundImageUri = uri.toString()))
            
            // Broadcast change
            ThemeManager.broadcastChange(this, false)
                
        } catch (e: Exception) {
            showPlayingBanner("Failed to set background image")
        }
    }
    
    fun pickBackgroundImage() {
        imagePickerLauncher.launch("image/*")
    }
    
    private fun checkPermissionsAndSetup() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        Dexter.withContext(this)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    if (report.areAllPermissionsGranted()) {
                        // Permissions granted, proceed to load default content
                        loadDefaultContentIfNeeded()
                    } else {
                        showPlayingBanner("Audio permission is required to show your music library.")
                        // Load a safe screen (Settings) so app doesn't crash
                        loadFragment(SettingsFragment())
                        supportActionBar?.title = getString(R.string.menu_settings)
                        binding.bottomNav.selectedItemId = R.id.nav_settings
                        // Ensure loading overlay is dismissed even if permission denied
                        hideLoadingOverlay()
                    }
                }
                
                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    token.continuePermissionRequest()
                }
            })
            .check()
    }

    private fun loadDefaultContentIfNeeded() {
        // Only load if nothing is displayed yet
        if (supportFragmentManager.findFragmentById(R.id.main_content) == null) {
            loadFragment(MusicLibraryFragment())
            supportActionBar?.title = getString(R.string.menu_music_library)
            binding.bottomNav.selectedItemId = R.id.nav_songs
            binding.navView.setCheckedItem(R.id.nav_music_library)
        }
        
        // Initialize background metadata scanning (silent)
        initializeBackgroundMetadataScanning()
        
        hideLoadingOverlay()
    }
    
    private fun initializeBackgroundMetadataScanning() {
        try {
            // Schedule periodic metadata scanning (runs every 6 hours when device is idle)
            com.stash.opusplayer.work.MetadataScanWorker.schedulePeriodicMetadataScan(this)
            
            // Also do an initial one-time scan for any new files (low priority)
            lifecycleScope.launch {
                kotlinx.coroutines.delay(5000) // Wait 5 seconds after app start
                com.stash.opusplayer.work.MetadataScanWorker.scheduleMetadataScan(this@MainActivity)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to initialize background metadata scanning", e)
        }
    }
    
    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_music_library -> {
                loadFragment(MusicLibraryFragment())
                supportActionBar?.title = getString(R.string.menu_music_library)
            }
            R.id.nav_playlists -> {
                loadFragment(PlaylistsFragment())
                supportActionBar?.title = getString(R.string.menu_playlists)
            }
            R.id.nav_artists -> {
                loadFragment(com.stash.opusplayer.ui.fragments.ArtistsFragment())
                supportActionBar?.title = "Artists"
            }
            R.id.nav_genres -> {
                loadFragment(com.stash.opusplayer.ui.fragments.GenresFragment())
                supportActionBar?.title = "Genres"
            }
            R.id.nav_equalizer -> {
                loadFragment(EqualizerFragment())
                supportActionBar?.title = "Equalizer"
            }
            R.id.nav_settings -> {
                loadFragment(SettingsFragment())
                supportActionBar?.title = getString(R.string.menu_settings)
            }
            R.id.nav_audio_settings -> {
                loadFragment(com.stash.opusplayer.ui.fragments.settings.PlaybackSettingsFragment())
                supportActionBar?.title = "Playback Settings"
            }
            R.id.nav_about -> {
                showAboutDialog()
            }
        }
        
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
    
    private fun loadFragment(fragment: Fragment) {
        UIPerformanceOptimizer.startPerformanceTracking("loadFragment")
        
        // Optimize fragment lifecycle
        UIPerformanceOptimizer.optimizeFragmentLifecycle(fragment)
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // Add fade transition animation for smooth fragment switching with optimized duration
        AnimationUtils.setFragmentFadeTransitions(transaction)
        
        transaction
            .replace(R.id.main_content, fragment)
            .runOnCommit {
                applyAppearanceToViews()
                UIPerformanceOptimizer.endPerformanceTracking("loadFragment")
            }
            .commit()
    }
    // No-op: legacy method retained for compatibility with older calls
    private fun hideLoadingOverlay() = Unit
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Stash Audio")
            .setMessage("""Stash Audio v${BuildConfig.VERSION_NAME}
                
A modern music player with precision pitch & speed, EQ, and beautiful artwork.
                
Features:
• Multi-format audio support
• Custom background images
• Smart update notifications
• Material Design 3 UI
• Intelligent user experience
                
© 2025 Stash Audio
                
Check for updates anytime from Settings.""")
            .setPositiveButton("Check for Updates") { _, _ ->
                updateManager.checkForUpdates(this, forceCheck = true)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    fun getUpdateManager() = updateManager

    // Persist the last playback source for Jump-to-Source navigation
    fun setLastPlaybackSource(type: String, arg: String = "") {
        val prefs = getSharedPreferences("playback_source", 0)
        prefs.edit().putString("type", type).putString("arg", arg).apply()
    }

    // Called by fragments once they have loaded their initial content
    fun notifyContentLoaded() {
        hideLoadingOverlay()
    }

    /**
     * Handles the `lumisound://discord-verify` redirect the bridge server
     * always sends the Chrome Custom Tab back to once the Discord OAuth2
     * code exchange finishes (see the manifest's intent-filter and
     * DiscordVerificationApi's KDoc for why that fixed scheme/host, not a
     * Stash-specific one). Just surfaces a Toast -- the actual verified
     * state comes from DiscordVerificationScreen re-fetching
     * GET /api/discord/verification on its own resume, not from anything
     * signaled here.
     */
    private fun handleDiscordVerifyDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "lumisound" || data.host != "discord-verify") return
        val success = data.getQueryParameter("success") == "true"
        if (success) {
            Toast.makeText(this, "Discord account linked", Toast.LENGTH_SHORT).show()
        } else {
            val reason = data.getQueryParameter("reason")
            val message = when (reason) {
                "already_linked_elsewhere" -> "That Discord account is already linked to a different account."
                "expired_state", "exchange_failed", "invalid_response" -> "Discord verification failed -- try again."
                else -> "Discord verification was cancelled."
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleDiscordLoginDeepLink(intent: Intent) {
        val data = intent.data ?: return
        if (data.scheme != "lumisound" || data.host != "discord-login") return
        val success = data.getQueryParameter("success") == "true"
        if (!success) {
            val reason = data.getQueryParameter("reason")
            lifecycleScope.launch { discordLoginEvents.emit(DiscordLoginOutcome.Failed(reason)) }
            return
        }
        if (data.getQueryParameter("requires_2fa") == "true") {
            val pendingToken = data.getQueryParameter("pending_token") ?: return
            lifecycleScope.launch { discordLoginEvents.emit(DiscordLoginOutcome.RequiresTwoFactor(pendingToken)) }
            return
        }
        val token = data.getQueryParameter("token") ?: return
        lifecycleScope.launch { discordLoginEvents.emit(DiscordLoginOutcome.SignedIn(token)) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDiscordVerifyDeepLink(it) }
        intent?.let { handleDiscordLoginDeepLink(it) }
        when (intent?.action) {
            "com.stash.opusplayer.ACTION_JUMP_TO_SOURCE" -> jumpToLastPlaybackSource()
            "com.stash.opusplayer.ACTION_GO_TO_ARTIST" -> {
                val name = intent.getStringExtra("artist") ?: return
                val repo = com.stash.opusplayer.data.MusicRepository(this)
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    // Fast path: query MediaStore directly for this artist
                    val quick = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.getSongsByArtistDirect(name) }
                    val songs = if (quick.isNotEmpty()) quick else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.getSongsByArtist(name) }

                    // Kick off background metadata scan for these files (best-effort)
                    runCatching {
                        val paths = songs.map { it.path }
                        com.stash.opusplayer.work.MetadataScanWorker.scheduleMetadataScan(this@MainActivity, paths, forceRescan = false)
                    }

                    val fragment = com.stash.opusplayer.ui.fragments.ArtistSongsFragment.newInstance(name, ArrayList(songs))
                    loadFragment(fragment)
                }
            }
            "com.stash.opusplayer.ACTION_GO_TO_ALBUM" -> {
                val album = intent.getStringExtra("album") ?: return
                val repo = com.stash.opusplayer.data.MusicRepository(this)
                lifecycleScope.launch {
                    val songs = repo.getSongsInAlbum(album)
                    val fragment = com.stash.opusplayer.ui.fragments.FolderDetailFragment.newInstance(album, ArrayList(songs), isAlbum = true)
                    loadFragment(fragment)
                }
            }
        }
    }
    
    /**
     * Apply appearance theme early in onCreate
     */
    private fun applyAppearanceTheme() {
        try {
            val prefs = AppearancePreferences.fromPrefs(this)
            
            // Apply font scale
            if (prefs.fontScale != 1.0f) {
                ThemeManager.applyTypographyScale(this, prefs.fontScale)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error applying appearance theme", e)
        }
    }
    
    /**
     * Apply appearance customizations to views after they're set up
     */
    private fun applyAppearanceToViews() {
        try {
            val prefs = AppearancePreferences.fromPrefs(this)
            
            // Apply to activity (system bars, toolbar, etc.)
            ThemeManager.applyToActivity(this, prefs)
            applyAdaptiveChromeScale(prefs)
            
            // Apply background overlay
            ThemeManager.applyBackgroundOverlay(this, prefs)
            
            // Apply mini player settings
            updateMiniPlayerFromPrefs(prefs)
            
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error applying appearance to views", e)
        }
    }

    private fun applyAdaptiveChromeScale(prefs: AppearancePreferences) {
        val toolbarHeight = ThemeManager.scaleDp(this, 52)
        binding.toolbar.layoutParams = binding.toolbar.layoutParams.apply {
            height = toolbarHeight
        }

        binding.bottomNav.itemIconSize = ThemeManager.scaleDp(this, 20)
        binding.bottomNav.setPadding(0, ThemeManager.scaleDp(this, 2), 0, 0)

        binding.scanningText.textSize = ThemeManager.scaleSp(this, 16f, prefs.fontScale)
        binding.downloadText.textSize = ThemeManager.scaleSp(this, 16f, prefs.fontScale)
        binding.playingText.textSize = ThemeManager.scaleSp(this, 16f, prefs.fontScale)
        binding.playingActionButton.textSize = ThemeManager.scaleSp(this, 12f, prefs.fontScale)
        val actionScale = (ThemeManager.getAdaptiveUiScale(this) * prefs.buttonSizeScale).coerceIn(0.76f, 1.0f)
        binding.playingActionButton.scaleX = actionScale
        binding.playingActionButton.scaleY = actionScale

        scaleBottomNavigationLabels(prefs)
    }

    private fun scaleBottomNavigationLabels(prefs: AppearancePreferences) {
        val menuView = binding.bottomNav.getChildAt(0) as? ViewGroup ?: return
        val labelSize = ThemeManager.scaleSp(this, 10.5f, prefs.fontScale)
        val itemPadding = ThemeManager.scaleDp(this, 4)
        for (i in 0 until menuView.childCount) {
            val itemView = menuView.getChildAt(i) as? ViewGroup ?: continue
            itemView.setPadding(itemView.paddingLeft, itemPadding, itemView.paddingRight, itemPadding)
            applyLabelScaleRecursively(itemView, labelSize)
        }
    }

    private fun applyLabelScaleRecursively(view: View, labelSize: Float) {
        when (view) {
            is TextView -> view.textSize = labelSize
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    applyLabelScaleRecursively(view.getChildAt(index), labelSize)
                }
            }
        }
    }
    
    /**
     * Update mini player based on appearance preferences
     */
    private fun updateMiniPlayerFromPrefs(prefs: AppearancePreferences) {
        try {
            ensureMiniPlayerView()
            ThemeManager.applyMiniPlayerSettings(miniPlayerView, prefs)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error updating mini player", e)
        }
    }
    
    /**
     * Apply visual customization from VisualCustomizationManager
     */
    private fun applyVisualCustomization() {
        try {
            val background = visualCustomizationManager.getCurrentBackground()
            binding.mainContent.background = background
            binding.drawerLayout.background = background
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error applying visual customization", e)
        }
    }
    
    /**
     * Register broadcast receiver for appearance changes
     */
    private fun registerAppearanceReceiver() {
        appearanceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                if (intent?.action == ThemeManager.ACTION_APPEARANCE_CHANGED) {
                    val requiresRecreate = intent.getBooleanExtra(ThemeManager.EXTRA_REQUIRES_RECREATE, false)
                    
                    if (requiresRecreate) {
                        // Recreate activity for changes that require it
                        recreate()
                    } else {
                        ensureMiniPlayerView()
                        // Apply changes dynamically
                        applyAppearanceToViews()
                        applyVisualCustomization()
                    }
                }
            }
        }
        
        val filter = IntentFilter(ThemeManager.ACTION_APPEARANCE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appearanceReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appearanceReceiver, filter)
        }
    }
    
    /**
     * Unregister broadcast receiver
     */
    private fun unregisterAppearanceReceiver() {
        appearanceReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        appearanceReceiver = null
    }
    
    private fun jumpToLastPlaybackSource() {
        val prefs = getSharedPreferences("playback_source", 0)
        val type = prefs.getString("type", null) ?: return
        val arg = prefs.getString("arg", "") ?: ""
        when (type) {
            "songs" -> {
                // Navigate to MusicLibraryFragment
                loadFragment(com.stash.opusplayer.ui.fragments.MusicLibraryFragment())
            }
            "artist" -> {
                // If we have the artist name and currently cached songs, build the fragment
                val repo = com.stash.opusplayer.data.MusicRepository(this)
                lifecycleScope.launch {
                    val songs = repo.getSongsByArtist(arg)
                    val fragment = com.stash.opusplayer.ui.fragments.ArtistSongsFragment.newInstance(arg, ArrayList(songs))
                    loadFragment(fragment)
                }
            }
            "folder" -> {
                val repo = com.stash.opusplayer.data.MusicRepository(this)
                lifecycleScope.launch {
                    val songs = repo.getSongsInFolder(arg)
                    val fragment = com.stash.opusplayer.ui.fragments.FolderDetailFragment.newInstance(arg, ArrayList(songs))
                    loadFragment(fragment)
                }
            }
            "playlist" -> {
                val id = arg.toLongOrNull()
                if (id != null) {
                    loadFragment(com.stash.opusplayer.ui.fragments.PlaylistDetailFragment.newInstance(id))
                }
            }
            "favorites" -> {
                loadFragment(com.stash.opusplayer.ui.fragments.FavoritesFragment())
            }
            else -> {
                // Default to library
                loadFragment(com.stash.opusplayer.ui.fragments.MusicLibraryFragment())
            }
        }
    }
    
    private fun setupMusicPlayer() {
        musicPlayerManager = (application as com.stash.opusplayer.StashOpusApplication).playerManager
    }
    
    private var playingBannerDismissRunnable: Runnable? = null

    fun showPlayingBanner(text: String) {
        try {
            val banner = findViewById<android.view.View>(R.id.playing_banner)
            val tv = findViewById<android.widget.TextView>(R.id.playing_text)
            val btn = findViewById<com.google.android.material.button.MaterialButton>(R.id.playing_action_button)
            tv.text = text
            btn.setOnClickListener {
                try { startActivity(Intent(this, QueueActivity::class.java)) } catch (_: Exception) {}
            }
            banner.visibility = android.view.View.VISIBLE
            // Cancel any previous dismissal and schedule a new one (keep visible during rapid switching)
            playingBannerDismissRunnable?.let { banner.removeCallbacks(it) }
            val runnable = Runnable {
                try { banner.visibility = android.view.View.GONE } catch (_: Exception) {}
            }
            playingBannerDismissRunnable = runnable
            banner.postDelayed(runnable, 2500) // ~2.5 seconds for better readability
        } catch (_: Exception) {}
    }
    
    private fun setupMiniPlayer() {
        ensureMiniPlayerView()
        
        // Observe current song changes for genre-based theming
        lifecycleScope.launch {
            musicPlayerManager.currentSong.collect { song ->
                song?.let { 
                    // Apply genre-based theme to main content area
                    genreThemeManager.applyThemeForSong(it, binding.mainContent)
                }
            }
        }
    }

    private fun ensureMiniPlayerView() {
        val requestedStyle = miniPlayerToggleManager.getMiniPlayerStyle()
        if (::miniPlayerView.isInitialized && currentMiniPlayerStyle == requestedStyle) {
            return
        }

        if (::miniPlayerView.isInitialized) {
            runCatching { miniPlayerView.release() }
        }

        binding.miniPlayerContainer.removeAllViews()
        miniPlayerView = miniPlayerToggleManager.createMiniPlayerView(binding.miniPlayerContainer)
        binding.miniPlayerContainer.addView(miniPlayerView.asView())
        miniPlayerView.initialize(this, musicPlayerManager)
        currentMiniPlayerStyle = requestedStyle
    }
    
    // Music player functionality
    fun playMusic(song: Song) {
        lifecycleScope.launch {
            try {
                musicPlayerManager.playSong(song)
                // Open Now Playing activity
                val intent = Intent(this@MainActivity, NowPlayingActivity::class.java).apply {
                    putExtra("song", song)
                }
                startActivity(intent)
            } catch (e: Exception) {
                showPlayingBanner("Error playing song: ${e.message}")
            }
        }
    }

    fun playSongsStartingFrom(songs: List<Song>, startIndex: Int, sourceLabel: String? = null) {
        lifecycleScope.launch {
            try {
                val idx = startIndex.coerceIn(0, songs.lastIndex)
                // Replace queue atomically and start playback from the selected index
                musicPlayerManager.playQueue(songs, idx)
                // Persist source hint for Jump-to-Source
                sourceLabel?.let { label ->
                    when {
                        label.startsWith("Artist:") -> setLastPlaybackSource("artist", label.removePrefix("Artist:").trim())
                        label.startsWith("Folder:") -> setLastPlaybackSource("folder", label.removePrefix("Folder:").trim())
                        label.startsWith("PlaylistId:") -> setLastPlaybackSource("playlist", label.removePrefix("PlaylistId:").trim())
                        label.equals("Liked Songs", true) -> setLastPlaybackSource("favorites", "")
                        label.equals("Songs", true) -> setLastPlaybackSource("songs", "")
                        else -> setLastPlaybackSource("unknown", label)
                    }
                    showPlayingBanner("Playing from $label")
                }
                val intent = Intent(this@MainActivity, NowPlayingActivity::class.java).apply {
                    putExtra("song", songs[idx])
                }
                startActivity(intent)
            } catch (e: Exception) {
                showPlayingBanner("Error starting playback")
            }
        }
    }
    
    fun addToPlaylist(song: com.stash.opusplayer.data.Song) {
val repo = com.stash.opusplayer.data.MusicRepository(this)
        lifecycleScope.launch {
            // Fetch current playlists
            val first = repo.getPlaylists().first()
            val names = first.map { it.name }.toTypedArray()
            val ids = first.map { it.id }.toLongArray()
            runOnUiThread {
                val options = names + "New playlist…"
                androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Add to playlist")
                    .setItems(options) { dialog, which ->
                        lifecycleScope.launch {
                            if (which < names.size) {
                                val playlistId = ids[which]
                                repo.addSongToPlaylist(playlistId, song)
                                showPlayingBanner("Added to ${names[which]}")
                            } else {
                                // create new
                                promptCreatePlaylistAndAdd(repo, song)
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun promptCreatePlaylistAndAdd(repo: com.stash.opusplayer.data.MusicRepository, song: com.stash.opusplayer.data.Song) {
        val edit = android.widget.EditText(this)
        edit.hint = "Playlist name"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create playlist")
            .setView(edit)
            .setPositiveButton("Create") { _, _ ->
                val name = edit.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val id = repo.createPlaylist(name)
                        repo.addSongToPlaylist(id, song)
                        showPlayingBanner("Added to $name")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    fun playNext(song: com.stash.opusplayer.data.Song) {
        try {
            musicPlayerManager.insertNext(song)
            showPlayingBanner("Will play next")
        } catch (_: Exception) {}
    }

    fun addToQueueTail(song: com.stash.opusplayer.data.Song) {
        try {
            musicPlayerManager.addToQueue(song)
            showPlayingBanner("Added to queue")
        } catch (_: Exception) {}
    }

    fun toggleFavorite(song: com.stash.opusplayer.data.Song) {
        lifecycleScope.launch {
            try {
val repository = com.stash.opusplayer.data.MusicRepository(this@MainActivity)
                val isFavorite = repository.isFavorite(song.id)
                
                if (isFavorite) {
                    repository.removeFromFavorites(song.id)
                    showPlayingBanner("💔 Removed from favorites")
                } else {
                    repository.addToFavorites(song)
                    showPlayingBanner("❤️ Added to favorites")
                }
            } catch (e: Exception) {
                showPlayingBanner("Error updating favorites: ${e.message}")
            }
        }
    }

    /**
     * Requests deletion of a MediaStore-backed audio file through the correct
     * scoped-storage flow for the running API level: a direct
     * `ContentResolver.delete` on API < 29 (legacy storage), a
     * `RecoverableSecurityException` catch-and-retry on API 29, and
     * `MediaStore.createDeleteRequest`'s system confirmation dialog on API 30+.
     * [onResult] always fires exactly once.
     */
    fun requestMediaDelete(uri: Uri, onResult: (Boolean) -> Unit) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                pendingMediaDeleteCallback = onResult
                mediaDeleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                try {
                    val rows = contentResolver.delete(uri, null, null)
                    onResult(rows > 0)
                } catch (e: android.app.RecoverableSecurityException) {
                    pendingMediaDeleteCallback = onResult
                    pendingMediaDeleteUri = uri
                    mediaDeleteLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
                } catch (e: Exception) {
                    onResult(false)
                }
            }
            else -> {
                val rows = runCatching { contentResolver.delete(uri, null, null) }.getOrDefault(0)
                onResult(rows > 0)
            }
        }
    }

    /**
     * Moves [song] into the in-app Recently Deleted trash: copies its bytes into
     * app-private storage first, then only removes the original (and the song
     * index row) once the system's own delete confirmation actually succeeds --
     * a copy is never staged as "deleted" while the real file is still sitting
     * in the user's library. See [com.stash.opusplayer.library.RecentlyDeletedService].
     */
    fun trashSong(song: com.stash.opusplayer.data.Song, onDone: (Boolean) -> Unit = {}) {
        lifecycleScope.launch {
            val db = com.stash.opusplayer.data.database.MusicDatabase.getDatabase(this@MainActivity)
            val entity = db.songDao().getSongById(song.id)
            if (entity == null) {
                onDone(false)
                return@launch
            }
            val trashFile = com.stash.opusplayer.library.RecentlyDeletedService.copyToTrash(this@MainActivity, entity)
            if (trashFile == null) {
                Toast.makeText(this@MainActivity, "Couldn't move \"${song.displayName}\" to trash.", Toast.LENGTH_SHORT).show()
                onDone(false)
                return@launch
            }
            val uri = com.stash.opusplayer.library.AudioFileValidator.contentUriFor(entity)
            requestMediaDelete(uri) { deleted ->
                lifecycleScope.launch {
                    if (deleted) {
                        com.stash.opusplayer.library.RecentlyDeletedService.finalize(
                            this@MainActivity, entity, trashFile, db.recentlyDeletedDao(), db.songDao()
                        )
                        Toast.makeText(this@MainActivity, "\"${song.displayName}\" moved to trash.", Toast.LENGTH_SHORT).show()
                    } else {
                        com.stash.opusplayer.library.RecentlyDeletedService.discard(trashFile)
                        Toast.makeText(this@MainActivity, "Delete was cancelled.", Toast.LENGTH_SHORT).show()
                    }
                    onDone(deleted)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::miniPlayerView.isInitialized) {
            try { miniPlayerView.resync() } catch (_: Exception) {}
        }
        if (wentToBackground && AppLockManager.isEnabled(this)) {
            showAppLockOverlay()
        }
    }

    private fun showAppLockOverlay() {
        wentToBackground = false
        if (appLockOverlay != null) return

        val overlay = layoutInflater.inflate(R.layout.view_app_lock_overlay, binding.drawerLayout, false)
        binding.drawerLayout.addView(overlay)
        appLockOverlay = overlay

        val promptForUnlock: () -> Unit = {
            AppLockManager.showPrompt(
                activity = this,
                onSuccess = { hideAppLockOverlay() },
                onFailure = { /* prompt dismissed/cancelled -- overlay stays up, Unlock button re-triggers it */ }
            )
        }
        overlay.findViewById<View>(R.id.app_lock_unlock_button).setOnClickListener { promptForUnlock() }
        promptForUnlock()
    }

    private fun hideAppLockOverlay() {
        appLockOverlay?.let { binding.drawerLayout.removeView(it) }
        appLockOverlay = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::miniPlayerView.isInitialized) {
            miniPlayerView.release()
        }
        if (::genreThemeManager.isInitialized) {
            genreThemeManager.release()
        }
        if (::physicsAnimationEngine.isInitialized) {
            physicsAnimationEngine.release()
        }
    }
    
}
