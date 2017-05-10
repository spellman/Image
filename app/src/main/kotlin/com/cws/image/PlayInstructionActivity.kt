package com.cws.image

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.media.AudioManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.cws.image.databinding.PlayInstructionActivityBinding

class PlayInstructionViewModel(
  val subject: String,
  val language: String
) {
  val appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}"
}

class PlayInstructionActivity : AppCompatActivity() {
  companion object {
    fun startForResult(activity: Activity, requestCode: Int, instruction: InstructionViewModel) {
      val intent = Intent(activity, PlayInstructionActivity::class.java)
      intent.putExtra("instruction", instruction)
      activity.startActivityForResult(intent, requestCode)
    }
  }

  private val instruction by lazy {
    intent.getParcelableExtra<InstructionViewModel>("instruction")
  }
  private val viewModel by lazy {
    PlayInstructionViewModel(
      subject = instruction.subject,
      language = instruction.language
    )
  }
  private val binding: PlayInstructionActivityBinding by lazy {
    DataBindingUtil.setContentView<PlayInstructionActivityBinding>(
      this,
      R.layout.play_instruction_activity)
  }
  private val mediaPlayerFragment by lazy {
    val fragmentTag = PlayInstructionFragment::class.java.name
    val fm = supportFragmentManager
    fm.findFragmentByTag(fragmentTag) as? PlayInstructionFragment ?: let {
      val fragment = PlayInstructionFragment()
      fm.beginTransaction()
        .add(fragment, fragmentTag)
        .commit()
      fragment
    }
  }
  private val presenter by lazy {
    PlayInstructionPresenter(this, mediaPlayerFragment, instruction)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setSupportActionBar(binding.toolbar)
    binding.viewModel = viewModel

    volumeControlStream = AudioManager.STREAM_MUSIC
    presenter.playInstruction()
  }

  override fun onBackPressed() {
    presenter.stopInstruction()
    super.onBackPressed()
  }

  fun showDelayMessage(message: String) {
    Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).show()
  }

  fun setInstructionProgress(percent: Int) {
    binding.instructionProgress2.progress = percent
  }

  fun showCue() {
    binding.cueText.visibility = View.VISIBLE
  }

  fun finishWithInstructionComplete() {
    setResult(Activity.RESULT_OK, Intent())
    finish()
  }

  fun finishWithInstructionError(instruction: InstructionViewModel, message: String) {
    setResult(
      Activity.RESULT_FIRST_USER,
      Intent().putExtra("instruction", instruction)
        .putExtra("message", message))
    finish()
  }

  override fun onDestroy() {
    presenter.onDestroy()
    super.onDestroy()
  }
}
