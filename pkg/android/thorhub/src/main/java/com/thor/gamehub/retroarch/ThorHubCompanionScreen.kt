// Copy into Thor Game Hub's library module, then register it in that
// module's AndroidManifest.xml inside <application>:
//
//   <meta-data android:name="com.retroarch.companion.SCREEN"
//              android:value="com.thorgamehub.retroarch.ThorHubCompanionScreen" />
//
// RetroArch Thor then shows it on the bottom screen whenever a game runs.
package com.thor.gamehub.retroarch

import com.thor.gamehub.ui.HubViewModel
import com.thor.gamehub.ui.HubScreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import android.content.Context
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.retroarch.browser.companion.CompanionMemory
import com.retroarch.browser.companion.CompanionScreen

class ThorHubCompanionScreen : CompanionScreen {

    private var owner: BottomScreenOwner? = null

    override fun onCreateView(context: Context, memory: CompanionMemory): View {
        val client = DirectRetroArchClient(memory)
        val o = BottomScreenOwner().also { owner = it }
        val model = HubViewModel(client)
        o.viewModelStore.put("hub", model)

        return ComposeView(context).apply {
            // The bottom screen is a Presentation (a Dialog), which doesn't
            // provide the owners Compose needs, so supply our own.
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme { Surface(Modifier.fillMaxSize()) { HubScreen(model) } }
            }
        }
    }

    override fun onDestroy() {
        owner?.destroy()
        owner = null
    }
}

private class BottomScreenOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
