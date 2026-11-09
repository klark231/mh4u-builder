package io.github.klark231.mh4ubuilder

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BuilderTheme { BuilderApp(vm) } }
    }

    // picks up All files access after coming back from settings
    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
