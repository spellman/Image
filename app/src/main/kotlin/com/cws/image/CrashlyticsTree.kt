package com.cws.image

import android.util.Log
import com.crashlytics.android.Crashlytics
import timber.log.Timber

class CrashlyticsTree : Timber.Tree() {
  override fun log(priority: Int, tag: String?, message: String?, t: Throwable?) {
    if (priority == Log.VERBOSE || priority == Log.DEBUG) {
      return
    }

    // 2017-02-23 Does not log to logcat.
    // "... every time you log in production, a puppy dies."
    //   -- https://github.com/JakeWharton/timber
    Crashlytics.log(message)

    if (t != null) {
      Crashlytics.logException(t)
    }
  }
}
