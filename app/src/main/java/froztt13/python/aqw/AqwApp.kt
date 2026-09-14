package froztt13.python.aqw

import android.app.Application
import froztt13.python.aqw.helper.BotHelper

class AqwApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BotHelper.init(filesDir)
    }
}