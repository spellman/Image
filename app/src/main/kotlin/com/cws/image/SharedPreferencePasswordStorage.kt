package com.cws.image

import android.content.Context

class SharedPreferencesPasswordStorage(
  val ctx: Context
) : AuthenticationGateway {
  override suspend fun getPassword(): Result<String, String> {
    val passwordPreference =
      provideRxSharedPreferences(ctx).getString("password")

    return if (!passwordPreference.isSet) {
      Result.Err("Password has not been set.")
    }
    else {
      Result.Ok(passwordPreference.get().toString())
    }
  }

  override suspend fun setPassword(password: String): Result<String, Unit> {
    val sharedPreferencesEditor = provideSharedPreferences(ctx).edit()
    sharedPreferencesEditor.putString("password", password)

    return if (sharedPreferencesEditor.commit()) {
      Result.Ok(Unit)
    }
    else {
      Result.Err(
        "Writing to ${ctx.getString(R.string.shared_preferences_file_key)} shared preferences failed")
    }
  }

  override fun isPasswordSet(): Boolean {
    return provideRxSharedPreferences(ctx).getString("password").isSet
  }
}

// 2017-04-07 Cort Spellman
// These functions form the basis of an InternalStoragePasswordStorage
//
//fun setPassword(context: Context, fileName: String, password: String): Completable {
//  return Completable.create { emitter ->
//    try {
//      context.openFileOutput(fileName, Context.MODE_PRIVATE).use { fileOutputStream ->
//        fileOutputStream.write(password.toByteArray())
//        fileOutputStream.close()
//        emitter.onComplete()
//      }
//    }
//    catch (e: FileNotFoundException) {
//      emitter.onError(e)
//    }
//    catch (e: IOException) {
//      emitter.onError(e)
//    }
//  }
//}
//
//fun getPassword(context: Context, fileName: String): Single<Result<String, String>> {
//  return Single.create { emitter ->
//    try {
//      context.openFileInput(fileName).use { fileInputStream ->
//        val bufferedReader = BufferedReader(InputStreamReader(fileInputStream))
//        val password = bufferedReader.useLines { s -> s.firstOrNull() }
//        when (password) {
//          is String -> emitter.onSuccess(Result.Ok(password))
//          else -> emitter.onSuccess(Result.Err("No password has been set."))
//        }
//      }
//    }
//    catch (e: FileNotFoundException) {
//      emitter.onSuccess(Result.Err("No password has been set."))
//    }
//    catch (e: IOException) {
//      emitter.onError(e)
//    }
//  }
//}
