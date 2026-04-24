package dev.ecalendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.ecalendar.data.preferences.CalendarView
import dev.ecalendar.ui.accounts.AccountSetupScreen
import dev.ecalendar.ui.accounts.AccountSetupViewModel
import dev.ecalendar.ui.accounts.AccountsScreen
import dev.ecalendar.ui.accounts.AccountsViewModel
import dev.ecalendar.ui.agenda.AgendaScreen
import dev.ecalendar.ui.day.DayScreen
import dev.ecalendar.ui.event.EventDetailScreen
import dev.ecalendar.ui.event.EventDetailViewModel
import dev.ecalendar.ui.event.EventEditScreen
import dev.ecalendar.ui.event.EventEditViewModel
import dev.ecalendar.ui.ics.IcsImportScreen
import dev.ecalendar.ui.ics.IcsImportViewModel
import dev.ecalendar.ui.ics.RsvpScreen
import dev.ecalendar.ui.month.MonthScreen
import dev.ecalendar.ui.settings.SettingsScreen
import dev.ecalendar.ui.settings.SettingsViewModel
import dev.ecalendar.ui.week.WeekScreen
import java.time.ZoneId

/** Temporary holder for passing RSVP data between ICS import and RSVP screens. */
private object RsvpDataHolder {
    var originalIcs: String? = null
    var organizer: String? = null
    var title: String = ""
    var startMillis: Long = 0L
    var myEmail: String = ""
}

@Composable
fun CalendarScaffold(
    viewModel: CalendarViewModel,
    pendingIcsContent: StateFlow<String?>? = null,
    onIcsConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()

    // Surface sync errors as a toast — otherwise they're buried in logcat and
    // the user has no feedback when a sync fails.
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(syncState) {
        val s = syncState
        if (s is dev.ecalendar.sync.SyncState.Error) {
            android.widget.Toast.makeText(
                context,
                "Sync failed: ${s.message}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Handle incoming ICS intent
    if (pendingIcsContent != null) {
        val icsContent by pendingIcsContent.collectAsStateWithLifecycle()
        LaunchedEffect(icsContent) {
            if (icsContent != null) {
                navController.navigate("ics/import")
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = "calendar",
        modifier = modifier,
    ) {
        composable("calendar") {
            CalendarHome(
                viewModel = viewModel,
                onCreateEvent = { startMillis ->
                    navController.navigate("event/edit?startMillis=$startMillis")
                },
                onEventClick = { uid, instanceStart ->
                    navController.navigate("event/detail/$uid/$instanceStart")
                },
                onAccounts = {
                    navController.navigate("settings")
                },
            )
        }

        composable(
            route = "event/detail/{uid}/{instanceStart}",
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("instanceStart") { type = NavType.LongType },
            ),
        ) {
            val detailViewModel: EventDetailViewModel = hiltViewModel()
            EventDetailScreen(
                viewModel = detailViewModel,
                onDismiss = { navController.popBackStack() },
                onEdit = { uid ->
                    navController.navigate("event/edit?uid=$uid")
                },
            )
        }

        composable(
            route = "event/edit?startMillis={startMillis}&uid={uid}",
            arguments = listOf(
                navArgument("startMillis") {
                    type = NavType.LongType
                    defaultValue = System.currentTimeMillis()
                },
                navArgument("uid") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            val editViewModel: EventEditViewModel = hiltViewModel()
            EventEditScreen(
                viewModel = editViewModel,
                onDismiss = { navController.popBackStack() },
            )
        }

        composable("settings") {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onDismiss = { navController.popBackStack() },
                onAccounts = { navController.navigate("accounts") },
            )
        }

        composable("accounts") {
            val accountsViewModel: AccountsViewModel = hiltViewModel()
            AccountsScreen(
                viewModel = accountsViewModel,
                onDismiss = { navController.popBackStack() },
                onAddAccount = { navController.navigate("accounts/setup") },
                onEditAccount = { accountId -> navController.navigate("accounts/edit/$accountId") },
            )
        }

        composable("accounts/setup") {
            val setupViewModel: AccountSetupViewModel = hiltViewModel()
            AccountSetupScreen(
                viewModel = setupViewModel,
                onDismiss = { navController.popBackStack() },
            )
        }

        composable(
            "accounts/edit/{accountId}",
            arguments = listOf(
                androidx.navigation.navArgument("accountId") {
                    type = androidx.navigation.NavType.LongType
                },
            ),
        ) {
            val setupViewModel: AccountSetupViewModel = hiltViewModel()
            AccountSetupScreen(
                viewModel = setupViewModel,
                onDismiss = { navController.popBackStack() },
            )
        }

        composable("ics/import") {
            val importViewModel: IcsImportViewModel = hiltViewModel()
            // Pass the pending ICS content to the ViewModel
            val icsContent = pendingIcsContent?.collectAsStateWithLifecycle()?.value
            LaunchedEffect(icsContent) {
                if (icsContent != null) {
                    importViewModel.parseIcs(icsContent)
                }
            }
            IcsImportScreen(
                viewModel = importViewModel,
                onDismiss = {
                    onIcsConsumed()
                    navController.popBackStack()
                },
                onSaved = {
                    val event = importViewModel.lastParsedEvent
                    onIcsConsumed()
                    if (event?.organizer != null) {
                        // Has organizer — show RSVP screen
                        RsvpDataHolder.originalIcs = event.rawIcs
                        RsvpDataHolder.organizer = event.organizer
                        RsvpDataHolder.title = event.title
                        RsvpDataHolder.startMillis = event.startMillis
                        // Use first non-organizer attendee as myEmail (likely the recipient)
                        RsvpDataHolder.myEmail = event.attendees
                            .firstOrNull { it != event.organizer } ?: ""
                        navController.navigate("ics/rsvp") {
                            popUpTo("calendar")
                        }
                    } else {
                        // No organizer — go back to calendar
                        navController.popBackStack("calendar", inclusive = false)
                    }
                },
            )
        }

        composable("ics/rsvp") {
            val originalIcs = RsvpDataHolder.originalIcs
            if (originalIcs != null) {
                DisposableEffect(Unit) {
                    onDispose {
                        // Clear holder when leaving RSVP screen (back navigation, etc.)
                        RsvpDataHolder.originalIcs = null
                        RsvpDataHolder.organizer = null
                        RsvpDataHolder.title = ""
                        RsvpDataHolder.startMillis = 0L
                        RsvpDataHolder.myEmail = ""
                    }
                }
                RsvpScreen(
                    originalIcs = originalIcs,
                    organizer = RsvpDataHolder.organizer,
                    title = RsvpDataHolder.title,
                    startMillis = RsvpDataHolder.startMillis,
                    myEmail = RsvpDataHolder.myEmail,
                    onDone = {
                        navController.popBackStack("calendar", inclusive = false)
                    },
                )
            }
        }
    }
}

@Composable
private fun CalendarHome(
    viewModel: CalendarViewModel,
    onCreateEvent: (Long) -> Unit,
    onEventClick: (String, Long) -> Unit,
    onAccounts: () -> Unit,
) {
    val activeView by viewModel.activeView.collectAsStateWithLifecycle()
    val activeDate by viewModel.activeDate.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    var isFabVisible by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            AnimatedVisibility(
                visible = isFabVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = {
                        val startMillis = activeDate.atStartOfDay(zone).toInstant().toEpochMilli()
                        onCreateEvent(startMillis)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create event")
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Offline banner
            AnimatedVisibility(visible = !isOnline) {
                Text(
                    text = "Working offline \u2014 changes will sync when connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Crossfade(
                targetState = activeView,
                modifier = Modifier.weight(1f),
                label = "view-switch",
        ) { view ->
            when (view) {
                CalendarView.MONTH -> MonthScreen(
                    viewModel = viewModel,
                    onDayClick = { date ->
                        viewModel.navigate(date)
                        viewModel.setView(CalendarView.DAY)
                    },
                    onEventClick = onEventClick,
                    onAccounts = onAccounts,
                )

                CalendarView.WEEK -> WeekScreen(
                    viewModel = viewModel,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    onAccounts = onAccounts,
                )

                CalendarView.DAY -> DayScreen(
                    viewModel = viewModel,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    onAccounts = onAccounts,
                )

                CalendarView.AGENDA -> AgendaScreen(
                    viewModel = viewModel,
                    onEventClick = onEventClick,
                    onCreateEvent = onCreateEvent,
                    onAccounts = onAccounts,
                    onScrollDirectionChanged = { scrollingDown -> isFabVisible = !scrollingDown },
                )
            }
        }
        } // Column
    }
}
