package com.cws.image

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

class AppCrashHandler(val application: Application) : Thread.UncaughtExceptionHandler {
  var liveActivity: Activity? = null

  init {
    application.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity?) {
          liveActivity = activity
        }

        override fun onActivityPaused(activity: Activity?) {
          liveActivity = null
        }

        override fun onActivityStarted(activity: Activity?) {}

        override fun onActivityDestroyed(activity: Activity?) {}

        override fun onActivitySaveInstanceState(activity: Activity?, bundle: Bundle?) {}

        override fun onActivityStopped(activity: Activity?) {}

        override fun onActivityCreated(activity: Activity?, bundle: Bundle?) {}
      })
  }

  override fun uncaughtException(thread: Thread?, e: Throwable?) {
    liveActivity?.let {
      val intent = Intent(application.applicationContext, MainActivity::class.java)
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      it.finish()
      it.startActivity(intent)
    }

    System.exit(0)
  }
}
