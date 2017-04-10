package com.cws.image

interface AuthenticationGateway {
  suspend fun getPassword(): Result<String, String>
  suspend fun setPassword(password: String): Result<String, Unit>
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

  suspend fun getPassword(): Result<String, String> {
    return authenticationGateway.getPassword()
  }

  suspend fun setPassword(password: String): Result<String, Unit> {
    return authenticationGateway.setPassword(Hash.sha256(password))
  }

  suspend fun isPasswordCorrect(password: String): Boolean {
    return try {
      val result = getPassword()
      when (result) {
        is Result.Err -> {
          // TODO: This should never happen so log this.
          false
        }
        is Result.Ok -> result.okValue == Hash.sha256(password)
      }
    }
    catch (e: IllegalArgumentException) {
      false
    }
  }

  fun isPasswordSet(): Boolean {
    return authenticationGateway.isPasswordSet()
  }
}
