package com.cws.image

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.media.AudioManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.cws.image.databinding.PlayInstructionActivityBinding

class PlayInstructionActivity : AppCompatActivity() {
  private val viewModel by lazy { ViewModel() }
  private val binding: PlayInstructionActivityBinding by lazy {
    DataBindingUtil.setContentView<PlayInstructionActivityBinding>(
      this,
      R.layout.play_instruction_activity)
  }
  private val mediaPlayerFragment by lazy {
    val fragmentTag = PlayInstructionFragment::class.java.name
    val fm = supportFragmentManager
    fm.findFragmentByTag(fragmentTag) as? PlayInstructionFragment ?: let {
      Log.d(this.javaClass.simpleName, "Making new PlayInstructionFragment.")
      val fragment = PlayInstructionFragment()
      fm.beginTransaction()
        .add(fragment, fragmentTag)
        .commit()
      fragment
    }
  }
  private val presenter by lazy {
    val instruction = intent.getParcelableExtra<InstructionViewModel>("instruction")
    PlayInstructionPresenter(this, mediaPlayerFragment, instruction)
  }

  companion object {
    fun startForResult(activity: Activity, requestCode: Int, instruction: InstructionViewModel) {
      val intent = Intent(activity, PlayInstructionActivity::class.java)
      intent.putExtra("instruction", instruction)
      activity.startActivityForResult(intent, requestCode)
    }
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
