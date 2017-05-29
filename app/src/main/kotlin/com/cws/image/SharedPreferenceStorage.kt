package com.cws.image

import android.content.Context
import com.f2prateek.rx.preferences2.Preference

class SharedPreferencesStorage(
  val ctx: Context
) : AuthenticationGateway, KioskModeStorageGateway, AppInstanceIdStorageGateway {
  val PASSWORD = "password"
  val APP_INSTANCE_ID = "app-instance-id"

  fun getPasswordPreference(): Preference<String> {
    return provideRxSharedPreferences(ctx).getString(PASSWORD)
  }

  override fun getPassword(): Result<String, String> {
    val passwordPreference = getPasswordPreference()

    return if (!passwordPreference.isSet) {
      Result.Err("Password has not been set.")
    }
    else {
      Result.Ok(passwordPreference.get().toString())
    }
  }

  override fun setPassword(password: String): Result<String, Unit> {
    val sharedPreferencesEditor = provideSharedPreferences(ctx).edit()
    sharedPreferencesEditor.putString(PASSWORD, password)

    return if (sharedPreferencesEditor.commit()) {
      Result.Ok(Unit)
    }
    else {
      Result.Err(
        "Writing password string ${password} to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed.")
    }
  }

  override fun isPasswordSet(): Boolean {
    return getPasswordPreference().isSet
  }

  override fun getShouldBeInKioskMode(): Boolean {
    return provideRxSharedPreferences(ctx)
      .getBoolean("should-be-in-kiosk-mode", false)
      .get() as Boolean
  }

  override fun setShouldBeInKioskMode(
    shouldBeInKioskMode: Boolean
  ): Result<String, Unit> {
    val sharedPreferencesEditor = provideSharedPreferences(ctx).edit()
    sharedPreferencesEditor.putBoolean("should-be-in-kiosk-mode", shouldBeInKioskMode)

    return if (sharedPreferencesEditor.commit()) {
      Result.Ok(Unit)
    }
    else {
      Result.Err(
        "Writing shouldBeInKioskMode boolean ${shouldBeInKioskMode} to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed.")
    }
  }

  fun getIdPreference(): Preference<String> {
    return provideRxSharedPreferences(ctx).getString(APP_INSTANCE_ID)
  }

  override fun getId(): Result<String, String> {
    val idPreference = getIdPreference()

    return if (!idPreference.isSet) {
      Result.Err("App-instance ID has not been set.")
    }
    else {
      Result.Ok(idPreference.get().toString())
    }
  }

  override fun setId(uuid: String): Result<String, Unit> {
    val sharedPreferencesEditor = provideSharedPreferences(ctx).edit()
    sharedPreferencesEditor.putString(APP_INSTANCE_ID, uuid)

    return if (sharedPreferencesEditor.commit()) {
      Result.Ok(Unit)
    }
    else {
      Result.Err(
        "Writing app-instance ID ${uuid} to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed.")
    }
  }
}
