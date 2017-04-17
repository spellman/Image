package com.cws.image

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class BootBroadcastReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
      Timber.d("Intent.ACTION_BOOT_COMPLETED received. About to start Image main activity.")
      val activityIntent = Intent(context, MainActivity::class.java)
      activityIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
      context.startActivity(activityIntent)
    }
  }
}
