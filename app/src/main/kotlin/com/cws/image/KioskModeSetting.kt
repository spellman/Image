package com.cws.image

interface KioskModeStorageGateway {
  fun getShouldBeInKioskMode(): Boolean
  fun setShouldBeInKioskMode(shouldBeInKioskMode: Boolean): Result<String, Unit>
}

class KioskModeSetting(
  val kioskModeStorageGateway: KioskModeStorageGateway
) {
  companion object Factory {
    private var instance: KioskModeSetting? = null
    fun getInstance(
      kioskModeStorageGateway: KioskModeStorageGateway
    ): KioskModeSetting {
      val instanceSnapshot = instance

      if (instanceSnapshot == null) {
        val newInstanceSnapshot = KioskModeSetting(kioskModeStorageGateway)
        instance = newInstanceSnapshot
        return newInstanceSnapshot
      }
      return instanceSnapshot
    }
  }

  fun getShouldBeInKioskMode(): Boolean {
    return kioskModeStorageGateway.getShouldBeInKioskMode()
  }

  fun setShouldBeInKioskMode(shouldBeInKioskMode: Boolean): Result<String, Unit> {
    return kioskModeStorageGateway.setShouldBeInKioskMode(shouldBeInKioskMode)
  }
}
