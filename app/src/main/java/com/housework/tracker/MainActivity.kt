package com.housework.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.housework.tracker.data.repository.ThemeMode
import com.housework.tracker.data.repository.ThemeRepository
import com.housework.tracker.ui.navigation.AppNavigation
import com.housework.tracker.ui.navigation.Routes
import com.housework.tracker.ui.theme.HouseworkTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeRepository: ThemeRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과 무시 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+ 알림 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val firebaseAuth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()

        setContent {
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            HouseworkTrackerTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    var startDestination by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val user = firebaseAuth.currentUser
                        startDestination = when {
                            user == null -> Routes.LOGIN
                            else -> {
                                try {
                                    val userDoc = firestore.collection("users")
                                        .document(user.uid).get().await()
                                    val houseId = userDoc.getString("houseId")

                                    // FCM 토큰 저장 (set + merge로 안전하게)
                                    try {
                                        val token = FirebaseMessaging.getInstance().token.await()
                                        firestore.collection("users")
                                            .document(user.uid)
                                            .set(
                                                mapOf("fcmToken" to token),
                                                SetOptions.merge()
                                            )
                                            .await()
                                    } catch (_: Exception) {}

                                    if (houseId.isNullOrBlank()) Routes.HOUSE_SETUP else Routes.MAIN
                                } catch (_: Exception) {
                                    Routes.LOGIN
                                }
                            }
                        }
                    }

                    if (startDestination != null) {
                        AppNavigation(
                            navController = navController,
                            startDestination = startDestination!!
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
