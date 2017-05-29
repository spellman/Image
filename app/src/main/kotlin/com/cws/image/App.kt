package com.cws.image

import android.app.Application
import android.os.Environment
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.answers.Answers
import com.facebook.stetho.Stetho
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher
import io.fabric.sdk.android.Fabric
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

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
    super.onCreate()

    if (BuildConfig.SHOULD_RESTART_ON_CRASH) {
      Thread.setDefaultUncaughtExceptionHandler(AppCrashHandler(this))
    }

    Fabric.with(this, Crashlytics())
    Fabric.with(this, Answers())
    Crashlytics.setString("git_sha", BuildConfig.GIT_SHA)
    setCrashlyticsUserIdentifier(provideAppInstanceId(this))
    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }
    Timber.plant(CrashlyticsTree())

    if (BuildConfig.DEBUG) {
      refWatcher = LeakCanary.install(this)
      Stetho.initializeWithDefaults(this)
    }
  }

  fun setCrashlyticsUserIdentifier(appInstanceId: AppInstanceId) {
    val id = appInstanceId.getId()
    when (id) {
      is Result.Ok -> Crashlytics.setUserIdentifier(id.okValue)

      is Result.Err -> {
        Observable.just(appInstanceId.setId(UUID.randomUUID().toString()))
          .map { res ->
            when (res) {
              is Result.Err -> throw Exception(res.errValue)
              is Result.Ok -> res.okValue
            }
          }
          .retryWhen { errors ->
            errors
              .zipWith(Observable.range(4, 12),
                       BiFunction(
                         { error: Throwable, i: Int -> Pair(error, i) }))
              .flatMap { (err, numberOfRetries) ->
                // 2017-05-28 Cort Spellman: Retry with exponential backoff.
                val durationUntilRetry =
                  Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

                Timber.d(
                  "${err.message} Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
                Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS)
              }
          }
          .observeOn(Schedulers.io())
          .subscribe(
            { _ -> setCrashlyticsUserIdentifier(appInstanceId) },
            { err -> Timber.e(err.message) }
          )
      }
    }
  }
}
