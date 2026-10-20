package id.rudissp.smania

import android.app.Application

class SmaniaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VolleySingleton.getInstance(this)
    }
}
