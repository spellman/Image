package com.cws.image

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.cws.image.databinding.PlayInstructionActivityBinding
import io.reactivex.disposables.Disposable

class PlayInstructionActivity : AppCompatActivity() {
  private val app by lazy { application as App }
  private val viewModel by lazy { app.viewModel }
  private val binding: PlayInstructionActivityBinding by lazy {
    DataBindingUtil.setContentView<PlayInstructionActivityBinding>(this, R.layout.play_instruction_activity)
  }
  private lateinit var viewModelChanSubscription: Disposable

  companion object {
    fun startForResult(activity: Activity, requestCode: Int) {
      activity.startActivityForResult(
        Intent(activity, PlayInstructionActivity::class.java),
        requestCode)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    viewModel.selectedInstruction ?: finish()

    setSupportActionBar(binding.toolbar)
    binding.viewModel = viewModel

    viewModelChanSubscription = viewModel.msgChan.subscribe { msg ->
      Log.d(this.javaClass.simpleName, "View model message ${msg.toString()}")
      val unused = when (msg) {
        is ViewModelMessage.InstructionsChanged -> {}

        is ViewModelMessage.LanguageChanged -> {}

        is ViewModelMessage.CouldNotReadInstructions -> {}

        is ViewModelMessage.CouldNotPlayInstruction -> finishWithError()

        is ViewModelMessage.PreparedToPlayInstructionAudio -> {}

        is ViewModelMessage.InstructionAudioCompleted -> {
          setResult(Activity.RESULT_OK, Intent())
          finish()
        }
      }
    }

    viewModel.mediaPlayer?.start() ?: finishWithError()
  }

  private fun finishWithError() {
    setResult(
      Activity.RESULT_FIRST_USER,
      Intent().putExtra("subject", viewModel.selectedInstruction?.subject)
        .putExtra("language", viewModel.selectedInstruction?.language))
    finish()
  }

  override fun onDestroy() {
    viewModelChanSubscription.dispose()
    viewModel.mediaPlayer?.release()
    viewModel.mediaPlayer = null
    super.onDestroy()
  }
}
