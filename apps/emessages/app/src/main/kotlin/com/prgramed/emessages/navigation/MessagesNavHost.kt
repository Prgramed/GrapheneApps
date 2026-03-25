package com.prgramed.emessages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prgramed.emessages.feature.chat.ChatScreen
import com.prgramed.emessages.feature.chat.NewMessageScreen
import com.prgramed.emessages.feature.conversations.ConversationsListScreen
import com.prgramed.emessages.feature.conversations.RecentlyDeletedScreen
import com.prgramed.emessages.feature.settings.BlockedNumbersScreen
import com.prgramed.emessages.feature.settings.SettingsScreen

@Composable
fun MessagesNavHost(
    modifier: Modifier = Modifier,
    sendToAddress: String? = null,
    sharedMessageText: String? = null,
    onSendToHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(sendToAddress, sharedMessageText) {
        if (sendToAddress != null) {
            val route = buildString {
                append(MessagesDestinations.NEW_MESSAGE)
                append("?address=${sendToAddress}")
                if (sharedMessageText != null) {
                    append("&body=${java.net.URLEncoder.encode(sharedMessageText, "UTF-8")}")
                }
            }
            navController.navigate(route)
            onSendToHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = MessagesDestinations.CONVERSATIONS,
        modifier = modifier,
    ) {
        composable(MessagesDestinations.CONVERSATIONS) {
            ConversationsListScreen(
                onConversationClick = { threadId ->
                    navController.navigate(MessagesDestinations.chat(threadId))
                },
                onNewMessage = {
                    navController.navigate(MessagesDestinations.NEW_MESSAGE)
                },
                onSettingsClick = {
                    navController.navigate(MessagesDestinations.SETTINGS)
                },
            )
        }
        composable(
            MessagesDestinations.CHAT,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onForwardMessage = { body ->
                    val encoded = java.net.URLEncoder.encode(body, "UTF-8")
                    navController.navigate(
                        "${MessagesDestinations.NEW_MESSAGE}?address=&body=$encoded",
                    )
                },
            )
        }
        composable(
            "${MessagesDestinations.NEW_MESSAGE}?address={address}&body={body}",
            arguments = listOf(
                navArgument("address") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("body") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            NewMessageScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { threadId ->
                    navController.popBackStack()
                    navController.navigate(MessagesDestinations.chat(threadId))
                },
            )
        }
        composable(MessagesDestinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBlockedNumbersClick = {
                    navController.navigate(MessagesDestinations.BLOCKED_NUMBERS)
                },
                onRecentlyDeletedClick = {
                    navController.navigate(MessagesDestinations.RECENTLY_DELETED)
                },
            )
        }
        composable(MessagesDestinations.BLOCKED_NUMBERS) {
            BlockedNumbersScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(MessagesDestinations.RECENTLY_DELETED) {
            RecentlyDeletedScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
