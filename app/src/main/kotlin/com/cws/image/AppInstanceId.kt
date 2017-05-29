package com.cws.image

interface AppInstanceIdStorageGateway {
  fun getId(): Result<String, String>
  fun setId(uuid: String): Result<String, Unit>
}

class AppInstanceId(
  val appInstanceIdStorageGateway: AppInstanceIdStorageGateway
) {
  companion object Factory {
    private var instance: AppInstanceId? = null
    fun getInstance(
      appInstanceIdStorageGateway: AppInstanceIdStorageGateway
    ): AppInstanceId {
      val instanceSnapshot = instance

      if (instanceSnapshot == null) {
        val newInstanceSnapshot = AppInstanceId(appInstanceIdStorageGateway)
        instance = newInstanceSnapshot
        return newInstanceSnapshot
      }
      return instanceSnapshot
    }
  }

  fun getId(): Result<String, String> {
    return appInstanceIdStorageGateway.getId()
  }

  fun setId(uuid: String): Result<String, Unit> {
    return appInstanceIdStorageGateway.setId(uuid)
  }
}
