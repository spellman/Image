package com.cws.image

import android.app.Application
import android.os.Environment
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.facebook.stetho.Stetho
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher
import io.fabric.sdk.android.Fabric
import timber.log.Timber
import java.io.File

class App : Application() {
  val storageDir by lazy {
    File(Environment.getExternalStorageDirectory(), packageName)
  }

  val tokenFile by lazy {
    File(storageDir, ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb")
  }

  lateinit var refWatcher: RefWatcher

  // 2016-10-12 Cort Spellman
  // TODO: Use locales instead of string languages. The method of getting the
  // current locale depends on the Android API version of the device:
  // https://developer.android.com/reference/android/content/res/Configuration.html#locale
  // resources.configuration.locale is deprecated in API level 24 (7.0).
  // resources.configuration.locales() was added in 24 and
  // resources.configuration.locales().get(0) is the new way to get the primary
  // current locale.

  override fun onCreate() {
    // 2017-02-23 Cort Spellman
    // TODO: Add app-crash handler: slides 13 and 14 at
    // https://www.slideshare.net/Infinum/infinum-android-talks-15-timber-crashlytics-a-match-made-in-heaven
    super.onCreate()
    Fabric.with(this, Crashlytics())
    Fabric.with(this, Answers())
    if (BuildConfig.DEBUG) {
        Timber.plant(Timber.DebugTree())
    }
    Timber.plant(CrashlyticsTree())
    refWatcher = LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
  }
}
