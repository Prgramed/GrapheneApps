package com.prgramed.econtacts.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prgramed.econtacts.feature.contactedit.ContactEditScreen
import com.prgramed.econtacts.feature.contactedit.VCardImportScreen
import com.prgramed.econtacts.feature.contactedit.VCardImportViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.prgramed.econtacts.feature.contacts.ContactDetailScreen
import com.prgramed.econtacts.feature.contacts.ContactsListScreen
import com.prgramed.econtacts.feature.contacts.FavoritesScreen
import com.prgramed.econtacts.feature.contacts.RecentsScreen
import com.prgramed.econtacts.feature.dialer.DialerScreen
import com.prgramed.econtacts.feature.settings.CardDavSettingsScreen
import com.prgramed.econtacts.feature.settings.DuplicatesScreen
import com.prgramed.econtacts.feature.settings.SettingsScreen
import com.prgramed.econtacts.feature.settings.SpeedDialScreen

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navItems = listOf(
    NavItem(ContactsDestinations.FAVORITES, "Favorites", Icons.Default.Star),
    NavItem(ContactsDestinations.RECENTS, "Recents", Icons.Default.History),
    NavItem(ContactsDestinations.CONTACTS_LIST, "Contacts", Icons.Default.People),
)

private val bottomNavRoutes = navItems.map { it.route }.toSet()

@Composable
fun ContactsNavHost(
    modifier: Modifier = Modifier,
    vcardContact: com.prgramed.econtacts.domain.model.Contact? = null,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true
                        NavigationBarItem(
                            icon = {
                                Icon(item.icon, contentDescription = item.label)
                            },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBottomBar) {
                FloatingActionButton(
                    onClick = { navController.navigate(ContactsDestinations.DIALER) },
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Phone, contentDescription = "Dialer")
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (vcardContact != null) ContactsDestinations.VCARD_IMPORT else ContactsDestinations.RECENTS,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ContactsDestinations.FAVORITES) {
                FavoritesScreen(
                    onContactClick = { contactId ->
                        navController.navigate(ContactsDestinations.contactDetail(contactId))
                    },
                )
            }
            composable(ContactsDestinations.CONTACTS_LIST) {
                ContactsListScreen(
                    onContactClick = { contactId ->
                        navController.navigate(ContactsDestinations.contactDetail(contactId))
                    },
                    onAddContact = {
                        navController.navigate(ContactsDestinations.CONTACT_NEW)
                    },
                    onNavigateToSettings = {
                        navController.navigate(ContactsDestinations.SETTINGS)
                    },
                )
            }
            composable(ContactsDestinations.RECENTS) {
                RecentsScreen(
                    onContactClick = { contactId ->
                        navController.navigate(ContactsDestinations.contactDetail(contactId))
                    },
                )
            }
            composable(ContactsDestinations.DIALER) {
                DialerScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ContactsDestinations.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onDuplicatesClick = {
                        navController.navigate(ContactsDestinations.DUPLICATES)
                    },
                    onCardDavClick = {
                        navController.navigate(ContactsDestinations.CARDDAV_SETTINGS)
                    },
                )
            }
            composable(
                ContactsDestinations.CONTACT_DETAIL,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
            ) {
                ContactDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { contactId ->
                        navController.navigate(ContactsDestinations.contactEdit(contactId))
                    },
                )
            }
            composable(
                ContactsDestinations.CONTACT_EDIT,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType }),
            ) {
                ContactEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(
                "${ContactsDestinations.CONTACT_NEW}?fromVCard={fromVCard}",
                arguments = listOf(
                    navArgument("fromVCard") { defaultValue = false; type = NavType.BoolType },
                ),
            ) {
                ContactEditScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(ContactsDestinations.DUPLICATES) {
                DuplicatesScreen(onBack = { navController.popBackStack() })
            }
            composable(ContactsDestinations.SPEED_DIAL) {
                SpeedDialScreen(onBack = { navController.popBackStack() })
            }
            composable(ContactsDestinations.CARDDAV_SETTINGS) {
                CardDavSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(ContactsDestinations.VCARD_IMPORT) {
                val vm: VCardImportViewModel = hiltViewModel()
                vcardContact?.let { contact ->
                    VCardImportScreen(
                        contact = contact,
                        viewModel = vm,
                        onDone = {
                            navController.navigate(ContactsDestinations.CONTACTS_LIST) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onCancel = {
                            navController.navigate(ContactsDestinations.RECENTS) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onEditBeforeSaving = {
                            navController.navigate("${ContactsDestinations.CONTACT_NEW}?fromVCard=true")
                        },
                    )
                } ?: LaunchedEffect(Unit) {
                    navController.navigate(ContactsDestinations.CONTACTS_LIST) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }
}
