package com.grapheneapps.enotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.grapheneapps.enotes.ui.theme.ENotesTheme
import com.grapheneapps.enotes.data.db.dao.NoteDao
import com.grapheneapps.enotes.navigation.ENotesNavHost
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var noteDao: NoteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Purge notes deleted more than 30 days ago
        lifecycleScope.launch(Dispatchers.IO) {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            noteDao.purgeDeletedBefore(thirtyDaysAgo)
        }

        setContent {
            ENotesTheme {
                ENotesNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
