package com.cws.image

import android.content.Context

class SharedPreferencesStorage(
  val ctx: Context
) : AuthenticationGateway, KioskModeStorageGateway {
  override fun getPassword(): Result<String, String> {
    val passwordPreference =
      provideRxSharedPreferences(ctx).getString("password")

    return if (!passwordPreference.isSet) {
      Result.Err("Password has not been set.")
    }
    else {
      Result.Ok(passwordPreference.get().toString())
    }
  }

  override fun setPassword(password: String): Result<String, Unit> {
    val sharedPreferencesEditor = provideSharedPreferences(ctx).edit()
    sharedPreferencesEditor.putString("password", password)

    return if (sharedPreferencesEditor.commit()) {
      Result.Ok(Unit)
    }
    else {
      Result.Err(
        "Writing password string to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed")
    }
  }

  override fun isPasswordSet(): Boolean {
    return provideRxSharedPreferences(ctx).getString("password").isSet
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
        "Writing shouldBeInKioskMode boolean to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed")
    }
  }
}
