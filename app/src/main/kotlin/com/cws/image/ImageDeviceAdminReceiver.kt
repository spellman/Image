package com.cws.image

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ImageDeviceAdminReceiver : DeviceAdminReceiver() {
  override fun onEnabled(context: Context, intent: Intent) {
    Toast.makeText(context, "Device admin permission received",
                   Toast.LENGTH_SHORT).show()
  }

  override fun onDisableRequested(
    context: Context,
    intent: Intent
  ): CharSequence {
    return "Disable device admin permission for Image app?"
  }

  override fun onDisabled(context: Context, intent: Intent) {
    Toast.makeText(context, "Device admin permission revoked",
                   Toast.LENGTH_SHORT).show()
  }

  override fun onLockTaskModeEntering(context: Context?, intent: Intent?,
                                      pkg: String?) {
    Toast.makeText(context, "Kiosk mode enabled.",
                   Toast.LENGTH_SHORT).show()
  }

  override fun onLockTaskModeExiting(context: Context, intent: Intent) {
    Toast.makeText(context, "Kiosk mode disabled.",
                   Toast.LENGTH_SHORT).show()
  }
}
