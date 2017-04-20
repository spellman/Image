package com.cws.image

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import com.cws.image.databinding.MainActivityBinding
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.jakewharton.rxbinding.support.design.widget.RxTabLayout
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import paperparcel.PaperParcel
import timber.log.Timber

// 2017-02-19 Cort Spellman
// TODO: Put version info in a menu item that doesn't do anything when clicked.
class MainViewModel {
  val appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}"
}

@PaperParcel
data class InstructionViewModel(
  val subject: String,
  val language: String,
  val audioAbsolutePath: String,
  val cueStartTimeMilliseconds: Long,
  val iconAbsolutePath: String?
) : Parcelable {
  companion object {
    @JvmField val CREATOR = PaperParcelInstructionViewModel.CREATOR
  }

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    PaperParcelInstructionViewModel.writeToParcel(this, dest, flags)
  }
}

data class UnparsableInstructionViewModel(
  val fileName: String,
  val failureMessage: String
)

class MainActivity : AppCompatActivity() {
  private val PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0
  private val REQUEST_CODE_PLAY_INSTRUCTION = 1
  private val SELECTED_LANGUAGE = "selected-language"
  private val viewModel by lazy { MainViewModel() }
  private val presenter by lazy {
    MainPresenter(
      this,
      provideGetInstructions(application as App),
      provideAuthentication(applicationContext),
      provideKioskModeSetting(applicationContext)
    )
  }
  private val binding: MainActivityBinding by lazy {
    DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
  }
  private lateinit var languageChangeSubscription: Disposable

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    presenter.resumeKioskModeIfItWasInterrupted()

    setSupportActionBar(binding.toolbar)
    binding.viewModel = viewModel

    initInstructionsRecyclerView()
    initUnparsableInstructionsRecyclerView()

    languageChangeSubscription =
      RxJavaInterop.toV2Observable(
        RxTabLayout.selections(binding.languages))
        .map { tab -> tab.tag as String }
        .subscribe { language ->
          Timber.i("Selected language: ${language}")
          presenter.showInstructionsForLanguage(language)
        }

    if (savedInstanceState != null) {
      presenter.selectedLanguage = savedInstanceState.getString(SELECTED_LANGUAGE)
    }

    showInstructions()
  }

  override fun onSaveInstanceState(outState: Bundle?) {
    super.onSaveInstanceState(outState)
    outState?.putString(SELECTED_LANGUAGE, presenter.selectedLanguage)
  }

  override fun onDestroy() {
    languageChangeSubscription.dispose()
    super.onDestroy()
  }

  override fun onActivityResult(requestCode: Int,
                                resultCode: Int,
                                data: Intent?) {
    when (requestCode) {
      REQUEST_CODE_PLAY_INSTRUCTION -> {
        when (resultCode) {
          Activity.RESULT_OK -> {}

          Activity.RESULT_CANCELED -> {}

          Activity.RESULT_FIRST_USER ->
            presenter.couldNotPlayInstruction(
              data?.getParcelableExtra<InstructionViewModel>("instruction"),
              data?.getStringExtra("message")
            )

          else -> {
            Timber.d("Unhandled activity result code: ${resultCode} for request-code ${requestCode}")
          }
        }
      }

      else -> {
        Timber.d("Unknown activity-result request-code: ${requestCode}")
      }
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    menuInflater.inflate(R.menu.main_activity, menu)

    return true
  }

  override fun onPrepareOptionsMenu(menu: Menu): Boolean {
    super.onPrepareOptionsMenu(menu)
    menu.findItem(R.id.set_up_kiosk_mode)
      .isVisible = presenter.isSetUpKioskModeMenuItemVisible()

    menu.findItem(R.id.set_password)
      .isVisible = presenter.isSetPasswordMenuItemVisible()

    val enterKioskModeMenuItem = menu.findItem(R.id.enter_kiosk_mode)
    enterKioskModeMenuItem.isVisible = presenter.isEnterKioskModeMenuItemVisible()
    enterKioskModeMenuItem.isEnabled = presenter.isEnterKioskModeMenuItemEnabled()

    menu.findItem(R.id.exit_kiosk_mode)
      .isVisible = presenter.isExitKioskModeMenuItemVisible()

    return true
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    return when (item?.itemId) {
      R.id.set_up_kiosk_mode -> {
        presenter.showInstructionsToSetUpLockedMode()
        true
      }

      R.id.set_password -> {
        presenter.startWorkflowToSetPassword()
        true
      }

      R.id.enter_kiosk_mode -> {
        presenter.enterKioskMode()
        true
      }

      R.id.exit_kiosk_mode -> {
        presenter.startWorkflowToExitKioskMode()
        true
      }

      R.id.force_exit_kiosk_mode -> {
        presenter.exitKioskMode()
        true
      }

      else -> {
        super.onOptionsItemSelected(item)
      }
    }
  }

  override fun finish() {
    if (presenter.isInLockTaskMode()) {
      Snackbar.make(binding.root,
                    "To quit the app, first exit kiosk mode.",
                    Snackbar.LENGTH_SHORT)
        .show()
      return
    }
    else {
      super.finish()
    }
  }

  fun initInstructionsRecyclerView() {
    val onSubjectClicked: (View, Int, Any?) -> Unit =
      { view: View, position: Int, item: Any? ->
        if (item as? InstructionViewModel != null) {
          presenter.playInstruction(item as InstructionViewModel)
        }
        else {
          presenter.couldNotPlayNonInstruction(item, position)
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
    val divider =
      android.support.v7.widget.DividerItemDecoration(
        this,
        LinearLayoutManager.VERTICAL)
    divider.setDrawable(
      ContextCompat.getDrawable(this, R.drawable.vertical_list_divider))
    instructionsForCurrentLanguage.addItemDecoration(divider)
  }

  fun initUnparsableInstructionsRecyclerView() {
    val unparsableInstructionsLayoutManager = LinearLayoutManager(this)
    unparsableInstructionsLayoutManager.orientation = LinearLayoutManager.VERTICAL
    val unparsableInstructions: RecyclerView = binding.unparsableInstructions
    unparsableInstructions.layoutManager = unparsableInstructionsLayoutManager
    unparsableInstructions.adapter =
      UnparsableInstructionsAdapter(R.layout.unparsable_instruction_layout,
                                    this,
                                    immutableListOf())
  }

  fun showSnackbarShort(message: String) = showSnackbar(message, Snackbar.LENGTH_SHORT)
  fun showSnackbarLong(message: String) = showSnackbar(message, Snackbar.LENGTH_LONG)
  fun showSnackbarIndefinite(message: String) = showSnackbar(message, Snackbar.LENGTH_INDEFINITE)
  // TODO: Use reddish letters for error text.
  // Add an optional type argument to the above functions.
  // Style the text based on the type in showSnackbar.

  fun showSnackbar(message: String, duration: Int, maxLines: Int = 3) {
    val snackbar = Snackbar.make(binding.root, message, duration)
    val snackbarTextView = snackbar.view.findViewById(
      android.support.design.R.id.snackbar_text) as? TextView
    snackbarTextView?.maxLines = maxLines
    snackbar.show()
  }

  fun showInstructions() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
      presenter.showInstructions()
    }
    else {
      requestPermissionWriteExternalStorage()
    }
  }

  private fun requestPermissionWriteExternalStorage() {
    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
      // TODO: Show an explanation to the user *asynchronously* -- don't block
      // this thread waiting for the user's response! After the user sees the
      // explanation (dialog or snackbar are easy), try again to request the
      // permission.
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }
    else {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE)
    }
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
  ) {
    if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        showInstructions()
      }
      else {
        requestPermissionWriteExternalStorage()
      }
    }
    else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  fun refreshUnparsableInstructions(
    unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>
  ) {
    if (unparsableInstructions.isEmpty()) {
      binding.unparsableInstructionsContainer.visibility = View.GONE
      (binding.unparsableInstructions.adapter as UnparsableInstructionsAdapter)
        .refreshUnparsableInstructions(unparsableInstructions)
    }
    else {
      binding.unparsableInstructionsContainer.visibility = View.VISIBLE
      (binding.unparsableInstructions.adapter as UnparsableInstructionsAdapter)
        .refreshUnparsableInstructions(unparsableInstructions)
    }
  }

  fun selectLanguageTab(index: Int) {
    val tab = binding.languages.getTabAt(index)
    if (tab != null) {
      tab.select()
    }
    else {
      binding.languages.getTabAt(0)?.select()
    }
  }

  fun refreshLanguageTabs(languages: ImmutableList<String>) {
    val previouslySelectedLanguage = presenter.selectedLanguage
    val languageTabs = binding.languages

    languageTabs.removeAllTabs()

    languages.forEach { l ->
      languageTabs.addTab(
        languageTabs.newTab().setTag(l).setText(l))
    }

    // 2017-02-25 Cort Spellman
    // TODO: Can I use a ViewPager instead of adding tabs manually above?
    // When there are no tabs, the first tab added becomes the selected tab,
    // necessitating this reset to the previously selected language.
    // NOTE: I have to reset here because selecting the tab, whether by a
    // user's click or by TabLayout#addTab when there are no tabs, sets
    // MainPresenter#selectedLanguage.
    previouslySelectedLanguage?.let {
      selectLanguageTab(languages.indexOf(previouslySelectedLanguage))
    }
  }

  fun refreshInstructionsForCurrentLanguage(instructions: ImmutableList<InstructionViewModel>) {
    (binding.instructions.adapter as InstructionsAdapter)
      .refreshInstructions(instructions)
  }

  fun startPlayInstructionActivity(instruction: InstructionViewModel) {
    PlayInstructionActivity.startForResult(this,
                                           REQUEST_CODE_PLAY_INSTRUCTION,
                                           instruction)
  }

  fun showDialogToSetPassword() {
    SetPasswordDialogFragment()
      .show(supportFragmentManager, EnterPasswordDialogFragment::class.java.name)
  }

  fun setPassword(password: String) {
    presenter.setPassword(password)
  }

  fun showDialogWithInstructionsToSetUpKioskMode() {
    InstructionsToSetUpKioskModeDialogFragment()
      .show(supportFragmentManager,
            InstructionsToSetUpKioskModeDialogFragment::class.java.name)
  }

  fun showDialogToEnterPasswordToExitKioskMode() {
    EnterPasswordDialogFragment()
      .show(supportFragmentManager, EnterPasswordDialogFragment::class.java.name)
  }

  fun exitKioskMode() {
    presenter.exitKioskMode()
  }

  fun exitKioskMode(message: String) {
    presenter.exitKioskMode(message)
  }
}
