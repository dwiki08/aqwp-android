package froztt13.python.aqw

import android.app.Application
import froztt13.python.aqw.data.repository.ConfigRepositoryImpl

class AqwApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ConfigRepositoryImpl.instance.init(filesDir)
    }
}