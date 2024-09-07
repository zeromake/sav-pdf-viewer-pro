package com.saverio.pdfviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val RECENT_FILES = "recents"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*setContentView(R.layout.activity_main)
        val navView: BottomNavigationView = findViewById(R.id.nav_view)

        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_open, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)*/

        openPDFFile()
    }

    fun loadRecents() {
        val recentFiles: String? =
            getSharedPreferences(RECENT_FILES, Context.MODE_PRIVATE).getString(RECENT_FILES, "")
        var recentFilesToSave = ""
        val recentFilesParts = recentFiles?.split("/:::/")
        var i = 0
        var addedI = 0
        while (i < recentFilesParts!!.size) {
            val recentFilesParts2 = recentFilesParts[i].split(":::")

            if (recentFilesParts2.size == 3) {
                if (addedI > 0) recentFilesToSave += "/:::/"
                recentFilesToSave += "${recentFilesParts2[0]}:::${recentFilesParts2[1]}:::${recentFilesParts2[2]}"
                addedI++
                //println("Recent ${i}:\ndate:${recentFilesParts2[0]}\nuri:${recentFilesParts2[1]}\nfavourite:${recentFilesParts2[2]}\n\n")
            }
            i++
        }
    }

    fun loadFavourites() {
        val recentFiles: String? =
            getSharedPreferences(RECENT_FILES, Context.MODE_PRIVATE).getString(RECENT_FILES, "")
        var recentFilesToSave = ""
        val recentFilesParts = recentFiles?.split("/:::/")
        var i = 0
        var addedI = 0
        while (i < recentFilesParts!!.size) {
            val recentFilesParts2 = recentFilesParts[i].split(":::")

            if (recentFilesParts2.size == 3 && recentFilesParts2[2] == "true") {
                if (addedI > 0) recentFilesToSave += "/:::/"
                recentFilesToSave += "${recentFilesParts2[0]}:::${recentFilesParts2[1]}:::${recentFilesParts2[2]}"
                addedI++
                //println("Recent ${i}:\ndate:${recentFilesParts2[0]}\nuri:${recentFilesParts2[1]}\nfavourite:${recentFilesParts2[2]}\n\n")
            }
            i++
        }
    }

    fun openPDFFile() {
        val intent = Intent(this@MainActivity, PDFViewer::class.java)
        val uriToOpen = Bundle()
        uriToOpen.putString("uri", "") //Your id
        intent.putExtras(uriToOpen) //Put your id to your next Intent
        startActivity(intent)
        finish() //TODO: when it will implemented also MainActivity, this won't remain
    }
}