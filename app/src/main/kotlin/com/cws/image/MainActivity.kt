package com.cws.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.widget.TextView
import com.cws.image.databinding.MainActivityBinding
import com.github.andrewoma.dexx.kollection.*
import com.jakewharton.rxbinding.support.design.widget.RxTabLayout
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable

class MainActivity : AppCompatActivity() {
  private val PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE = 0
  private val REQUEST_PLAY_INSTRUCTION = 1
  private val app by lazy { application as App }
  private val viewModel by lazy { app.viewModel }
  private val binding: MainActivityBinding by lazy {
    DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
  }
  private lateinit var viewModelChanSubscription: Disposable
  private lateinit var languageChangeSubscription: Disposable

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setSupportActionBar(binding.toolbar)
    binding.viewModel = viewModel

    initInstructionsRecyclerView()
    initUnparsableInstructionsRecyclerView()

    viewModelChanSubscription =
      viewModel.msgChan
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { msg -> handleViewModelMessage(msg) }

    languageChangeSubscription =
      RxJavaInterop.toV2Observable(
        RxTabLayout.selections(binding.languages))
        .map { tab -> tab.tag as String }
        .doOnNext { language ->
          Log.d(this.javaClass.simpleName, "RxTabLayout.selections")
          Log.d("selected tab", language)
        }
        .subscribe { language -> viewModel.setCurrentLanguage(language) }

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissionWriteExternalStorage()
    }
    else {
      getInstructions()
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (requestCode == PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        getInstructions()
      }
      else {
        requestPermissionWriteExternalStorage()
      }
    }
    else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  private fun requestPermissionWriteExternalStorage() {
    ActivityCompat.requestPermissions(this,
                                      arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                      PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE)
  }

  override fun onPause() {
    viewModelChanSubscription.dispose()
    super.onPause()
  }

  override fun onResume() {
    if (viewModelChanSubscription.isDisposed) {
      viewModelChanSubscription = viewModel.msgChan
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe { msg -> handleViewModelMessage(msg) }
    }
    super.onResume()
  }

  override fun onDestroy() {
    languageChangeSubscription.dispose()
    super.onDestroy()
  }

  fun initInstructionsRecyclerView() {
    val onSubjectClicked: (View, Int, Any?) -> Unit = { view: View, position: Int, item: Any? ->
      if (item as? Instruction != null) {
        viewModel.prepareToPlayInstruction(item as Instruction)
      }
      else {
        Log.e(this.javaClass.simpleName, "Cannot prepare to play instruction because ${item}, the ${position + 1}th item in the list of instructions, is not a valid instruction.")
      }
    }

    val instructionsLayoutManager = LinearLayoutManager(this)
    instructionsLayoutManager.orientation = LinearLayoutManager.VERTICAL
    val instructionsForCurrentLanguage: RecyclerView = binding.instructions
    instructionsForCurrentLanguage.layoutManager = instructionsLayoutManager
    instructionsForCurrentLanguage.adapter =
      InstructionsAdapter(R.layout.subject_layout,
                          this,
                          immutableListOf(),
                          onSubjectClicked)

    instructionsForCurrentLanguage.setHasFixedSize(true)
  }

  fun initUnparsableInstructionsRecyclerView() {
    val unparsableInstructionsLayoutManager = LinearLayoutManager(this)
    unparsableInstructionsLayoutManager.orientation = LinearLayoutManager.VERTICAL
    val unparsableInstructions: RecyclerView = binding.unparsableInstructions
    unparsableInstructions.layoutManager = unparsableInstructionsLayoutManager
    unparsableInstructions.adapter =
      UnparsableInstructionsAdapter(R.layout.unparsable_instruction_layout,
                                    this,
                                    immutableListOf(),
                                    null)
  }

  private fun getInstructions() {
    viewModel.getInstructions()
  }

  fun handleViewModelMessage(msg: ViewModelMessage) {
    Log.d(this.javaClass.simpleName, "View model message ${msg}")
    val unused = when (msg) {
      is ViewModelMessage.InstructionsChanged -> {
        refreshUnparsableInstructions(msg.unparsableInstructions)
        refreshLanguageTabs(msg.languages)
      }

      is ViewModelMessage.LanguageChanged -> {
        refreshInstructionsForCurrentLanguage(msg.instructionsForCurrentLanguage)
      }

      is ViewModelMessage.CouldNotReadInstructions -> {
        val snackbar = Snackbar.make(binding.root,
                                     msg.message,
                                     Snackbar.LENGTH_INDEFINITE)
        val snackbarTextView = snackbar.view.findViewById(
          android.support.design.R.id.snackbar_text) as? TextView
        snackbarTextView?.maxLines = 3
        snackbar.show()
      }

      is ViewModelMessage.CouldNotPlayInstruction -> {
        val instruction = msg.instruction
        val message = "The ${instruction.language} ${instruction.subject} instruction could not be played.\n(${instruction.absolutePath})"
        val snackbar = Snackbar.make(binding.root, message, 5000)
        val snackbarTextView = snackbar.view.findViewById(
          android.support.design.R.id.snackbar_text) as? TextView
        snackbarTextView?.maxLines = 3
        snackbar.show()
      }

      is ViewModelMessage.PreparedToPlayInstructionAudio -> {
        navigateToPlayInstructionActivity()
      }

      is ViewModelMessage.InstructionAudioCompleted -> {}
    }
  }


  private fun refreshUnparsableInstructions(
    unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>
  ) {
    Log.d(this.javaClass.simpleName, "refreshUnparsableInstructions")
    Log.d("unpars instructs", unparsableInstructions.toString())
    (binding.unparsableInstructions.adapter as UnparsableInstructionsAdapter)
      .refreshUnparsableInstructions(unparsableInstructions)
  }

  private fun setLanguage(language: String?, languages: ImmutableList<String>) {
    Log.d(this.javaClass.simpleName, "setLanguage")
    Log.d("language", language)
    val selectedLanguageIndex = languages.indexOf(language)
    val tab = binding.languages.getTabAt(selectedLanguageIndex)
    if (tab != null) {
      Log.d(this.javaClass.simpleName, "setLanguage")
      Log.d("selecting tab", "programmatically selecting tab for language ${tab.tag}")
      tab.select()
    }
    else {
      binding.languages.getTabAt(0)?.select()
      Log.e("setLanguage", "${language} (tab[${selectedLanguageIndex}]) not available. Selected default language instead.")
    }
  }

  private fun refreshLanguageTabs(languages: ImmutableList<String>) {
    // 2017-01-28 Cort Spellman
    // TODO: Use a viewpager -- this is stupid to be depending on the tabs
    // being in sync with another list.
    Log.d(this.javaClass.simpleName, "refreshLanguageTabs")
    Log.d("languages", languages.toString())
    val languageTabs = binding.languages
    val previouslySelectedTab = languageTabs.getTabAt(languageTabs.selectedTabPosition)

    languageTabs.removeAllTabs()

    languages.forEach { l ->
      languageTabs.addTab(
        languageTabs.newTab().setTag(l).setText(l))
    }

    previouslySelectedTab?.let {
      Log.d("refreshLanguagesTabs", "Setting language to selected language of ${previouslySelectedTab.tag}.")
      setLanguage(previouslySelectedTab.tag as? String, languages)
    }
  }

  private fun refreshInstructionsForCurrentLanguage(instructions: ImmutableList<Instruction>) {
    Log.d(this.javaClass.simpleName, "refreshInstructionsForCurrentLanguage")
    Log.d("instructions", instructions.toString())
    (binding.instructions.adapter as InstructionsAdapter)
      .refreshInstructions(instructions)
  }

  private fun navigateToPlayInstructionActivity() {
    PlayInstructionActivity.startForResult(this, REQUEST_PLAY_INSTRUCTION)
  }

  override fun onActivityResult(requestCode: Int,
                                resultCode: Int,
                                data: Intent?) {
    when (requestCode) {
      REQUEST_PLAY_INSTRUCTION -> {
        when (resultCode) {
          Activity.RESULT_OK -> {}

          Activity.RESULT_CANCELED -> {}

          Activity.RESULT_FIRST_USER ->
            viewModel.handlePlayInstructionFailure(
              data?.getStringExtra("subject") ?: "subject not available",
                data?.getStringExtra("language") ?: "language not available")

          else -> {
            Log.d(this.javaClass.simpleName, "Unhandled activity-result result-code: ${resultCode} for request-code ${requestCode}")
          }
        }
      }

      else -> {
        Log.d(this.javaClass.simpleName, "Unknown activity-result request-code: ${requestCode}")
      }
    }
  }
}
