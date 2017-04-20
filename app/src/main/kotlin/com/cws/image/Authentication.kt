package com.cws.image

import timber.log.Timber

interface AuthenticationGateway {
  fun getPassword(): Result<String, String>
  fun setPassword(password: String): Result<String, Unit>
  fun isPasswordSet(): Boolean
}

class Authentication(
  val authenticationGateway: AuthenticationGateway
) {
  companion object Factory {
    private var instance: Authentication? = null
    fun getInstance(
      authenticationGateway: AuthenticationGateway
    ): Authentication {
      val instanceSnapshot = instance

      if (instanceSnapshot == null) {
        val newInstanceSnapshot = Authentication(authenticationGateway)
        instance = newInstanceSnapshot
        return newInstanceSnapshot
      }
      return instanceSnapshot
    }
  }

  fun getPassword(): Result<String, String> {
    return authenticationGateway.getPassword()
  }

  fun setPassword(password: String): Result<String, Unit> {
    return authenticationGateway.setPassword(Hash.sha256(password))
  }

  fun isPasswordCorrect(password: String): Result<String, Boolean> {
    val res = getPassword()
    return when (res) {
      is Result.Err -> Result.Err(res.errValue)
      is Result.Ok -> Result.Ok(res.okValue == Hash.sha256(password))
    }
  }

  fun isPasswordSet(): Boolean {
    return authenticationGateway.isPasswordSet()
  }
}
