package com.cws.image

import android.Manifest
import android.content.pm.PackageManager
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.constraint.ConstraintLayout
import android.support.design.widget.Snackbar
import android.support.design.widget.TabLayout
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import com.cws.image.databinding.MainActivityBinding
import com.github.andrewoma.dexx.kollection.*
import com.jakewharton.rxbinding.support.design.widget.RxTabLayout
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.disposables.Disposable

class MainActivity : AppCompatActivity() {
  val PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE = 0
  val app by lazy { application as App }
  val binding: MainActivityBinding by lazy {
    DataBindingUtil.setContentView<MainActivityBinding>(this, R.layout.main_activity)
  }
//  lateinit var presenterChanSubscription: Disposable

  fun requestPermissionWriteExternalStorage() {
    ActivityCompat.requestPermissions(this,
                                      arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                      PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE)
  }

  fun setUpActivity(savedInstanceState: Bundle?) {
    setSupportActionBar(binding.toolbar)

    val viewModel =
      if (savedInstanceState == null) {
        ViewModel(app = app,
                  activity = this)
      }
      else {
        // TODO: Restore saved ViewModel
        throw NotImplementedError("TODO: Restore saved ViewModel")
      }

    addAppVersionInfo(viewModel.appVersionInfo)


    val instructionsForCurrentLanguageAdapter =
      InstructionsAdapter(R.layout.subject_layout,
                          this,
                          immutableListOf())
    val instructionsLayoutManager = LinearLayoutManager(this)
    instructionsLayoutManager.orientation = LinearLayoutManager.VERTICAL

    val instructionsForCurrentLanguage: RecyclerView = binding.instructions
    instructionsForCurrentLanguage.layoutManager = instructionsLayoutManager
    instructionsForCurrentLanguage.adapter = instructionsForCurrentLanguageAdapter
    instructionsForCurrentLanguage.setHasFixedSize(true)


    val unparsableInstructionsAdapter =
      UnparsableInstructionsAdapter(R.layout.unparsable_instruction_layout,
                                    this,
                                    immutableListOf())
    val unparsableInstructionsLayoutManager = LinearLayoutManager(this)
    instructionsLayoutManager.orientation = LinearLayoutManager.VERTICAL

    val unparsableInstructions: RecyclerView = binding.unparsableInstructions
    unparsableInstructions.layoutManager = unparsableInstructionsLayoutManager
    unparsableInstructions.adapter = unparsableInstructionsAdapter


    if (viewModel.needToRefreshInstructions) {
      viewModel.getInstructions()
    }

    viewModel.setCurrentLanguage(
      RxJavaInterop.toV2Observable(
        RxTabLayout.selections(binding.languages))
        .doOnNext { tab ->
          Log.d(this.javaClass.simpleName, "RxTabLayout.selections")
          Log.d("selected tab", tab.text as String)
        }
        .map { tab -> tab.tag as String }
    )
//    presenterChanSubscription = presenterChan.subscribe { msg ->
//      Log.d("Presenter message", msg.toString())
//      val x = when (msg) {
//        is PresenterMessage.SnackbarMessage.CouldNotReadInstructions -> {
//          val snackbar = Snackbar.make(binding.root,
//                                       msg.message,
//                                       Snackbar.LENGTH_INDEFINITE)
//          val snackbarTextView = snackbar.view.findViewById(
//                                   android.support.design.R.id.snackbar_text) as? TextView
//          snackbarTextView?.maxLines = 3
//          snackbar.show()
//        }
//
//        is PresenterMessage.SnackbarMessage.CouldNotPlayInstruction -> {
//          val message = "The ${msg.language} ${msg.subject} instruction could not be played.\n(${msg.absolutePath})"
//          val snackbar = Snackbar.make(binding.root, message, 5000)
//          val snackbarTextView = snackbar.view.findViewById(
//                                   android.support.design.R.id.snackbar_text) as? TextView
//          snackbarTextView?.maxLines = 3
//          snackbar.show()
//        }
//      }
//    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setUpActivity(savedInstanceState)

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissionWriteExternalStorage()
    }
    else {
      // TODO: make it go
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    if (requestCode == PERMISSION_REQUEST_FOR_WRITE_EXTERNAL_STORAGE) {
      if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        // TODO: make it go
      }
      else {
        requestPermissionWriteExternalStorage()
      }
    }
    else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  override fun onBackPressed() {
  }

//  override fun onDestroy() {
//    presenterChanSubscription.dispose()
//    super.onDestroy()
//  }

  fun addAppVersionInfo(appVersionInfo: String) {
    // 2017-01-23 Cort Spellman
    // This is not working statically for some reason.
    // Data binding would not put viewModel.appVersionInfo in a text view.
    // It would put an equivalent hard-coded string but not the value of
    // viewModel.appVersionInfo.
    // I don't understand because that value is a string; the Build.Config
    // values are evaluated eagerly when viewModel is constructed in App.
    val contentMain = binding.contentMain

    val viewAppVersionInfo = AppCompatTextView(this)
    viewAppVersionInfo.text = appVersionInfo
    viewAppVersionInfo.gravity = Gravity.CENTER

    val lp = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT,
                                           ConstraintLayout.LayoutParams.WRAP_CONTENT)
    lp.bottomToBottom = contentMain.id
    lp.leftToLeft = contentMain.id
    lp.rightToRight = contentMain.id

    contentMain.addView(viewAppVersionInfo, lp)
  }

  fun refreshUnparsableInstructions(
    unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>
  ) {
    Log.d(this.javaClass.simpleName, "refreshUnparsableInstructions")
    Log.d("unpars instructs", unparsableInstructions.toString())
    (binding.unparsableInstructions.adapter as UnparsableInstructionsAdapter)
      .refreshUnparsableInstructions(unparsableInstructions)
  }

  fun setLanguage(language: String?, languages: ImmutableList<String>) {
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

  fun refreshLanguageTabs(languages: ImmutableList<String>,
                          defaultLanguage: String) {
    // 2017-01-28 Cort Spellman
    // TODO: Use a viewpager -- this is stupid to be depending on the tabs
    // being in sync with another list.
    Log.d(this.javaClass.simpleName, "refreshLanguageTabs")
    Log.d("languages", languages.toString())
    val languageTabs = binding.languages
    val selectedTab = languageTabs.getTabAt(languageTabs.selectedTabPosition)

    languageTabs.removeAllTabs()

    languages.forEach { l ->
      languageTabs.addTab(
        languageTabs.newTab().setTag(l).setText(l))
    }

    if (selectedTab == null) {
      setLanguage(defaultLanguage, languages)
    }
    else {
      setLanguage(selectedTab.tag as? String, languages)
    }
  }

  fun refreshInstructionsForCurrentLanguage(instructions: ImmutableList<Instruction>) {
    Log.d(this.javaClass.simpleName, "refreshInstructionsForCurrentLanguage")
    Log.d("instructions", instructions.toString())
    (binding.instructions.adapter as InstructionsAdapter)
      .refreshInstructions(instructions)
  }
}



//fun viewInitializing() {
//  frameLayout {
//    size(FILL, FILL)
//    DSL.gravity(CENTER)
//
//    appCompatTextView {
//      size(WRAP, WRAP)
//      text("loading")
//      textColor(android.graphics.Color.BLACK)
//    }
//  }
//}
//
//fun viewLanguage(dispatch: (com.brianegan.bansa.Action) -> BansaState,
//                 language: String) {
//  frameLayout {
//    size(WRAP, FILL)
//
//    appCompatTextView {
//      size(WRAP, WRAP)
//      minimumWidth(dip(72))
//      layoutGravity(BOTTOM)
//      textAlignment(View.TEXT_ALIGNMENT_CENTER)
////              backgroundColor(android.graphics.Color.argb(32, 0, 255, 0))
//      textColor(android.graphics.Color.WHITE)
//      margin(dip(16), dip(0))
//      // TODO: Bottom padding should be 12px if l spans two lines.
//      padding(dip(12), dip(0), dip(12), dip(20))
//      minLines(1)
//      maxLines(2)
//      ellipsize(TextUtils.TruncateAt.END)
//      text(language)
//    }
//
//    onClick { v -> dispatch(Action.SetLanguage(language)) }
//  }
//}
//
//fun viewLanguages(dispatch: (com.brianegan.bansa.Action) -> BansaState,
//                  c: Context,
//                  languages: ImmutableSet<String>) {
//  horizontalScrollView {
//    size(FILL, dip(48))
//    AppCompatv7DSL.gravity(LEFT)
//    backgroundColor(ContextCompat.getColor(c, R.color.colorPrimary))
//
//    linearLayoutCompat {
//      size(WRAP, FILL)
//      AppCompatv7DSL.orientation(LinearLayoutCompat.HORIZONTAL)
////        backgroundColor(android.graphics.Color.argb(32, 255, 0, 0))
//      languages.map { l -> viewLanguage(dispatch, l) }
//    }
//  }
//}
//
//fun viewNoSubjects (message: String, storageDirPath: String) {
//  linearLayoutCompat {
//    size(FILL, WRAP)
//    AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//
//    appCompatTextView {
//      text(message)
//      textColor(android.graphics.Color.BLACK)
//    }
//
//    appCompatTextView {
//      text("We're loading files manually for now so do the following to get started:\n1. Close the app: touch the menu button (square) on the device > either touch the x in the app window's title bar or swipe the app window to the left.\n2. Ensure your instruction sound-files are in .ogg format (or one of the audio formats listed at\nhttps://developer.android.com/guide/appendix/media-formats.html\nthough .ogg is said to play best).\n3. Ensure your instruction audio-files are named <x-ray subject>_<language>_<cue time in milliseconds>.ogg (or appropriate file extension)\n     Ex: chest_english_9000.ogg\nInclude spaces and/or punctuation in the subject or language via URI encoding:\nhttps://en.wikipedia.org/wiki/Percent-encoding\n     Ex: one%20%2f%20two%20%28three%29_english_1000.ogg will be parsed to\n             subject: one / two (three)\n             language: english\n             cue time: 1000\n4. Connect the device to your computer via USB.\n5. Ensure the device is in file transfer mode: Swipe down from the top of the device screen; one of the notifications should say \"USB for charging\" or \"USB for photo transfer\" or \"USB for file transfers\" or something like that. If the current mode isn't \"USB for file transfers\", then touch the notification and then select \"USB for file transfers\".\n6. Open the device in your file explorer (e.g., Windows Explorer on Windows, Finder on Mac, etc.) and copy the instructions to ${storageDirPath}.\n7. Re-launch the app.\nIf this procedure doesn't result in the main app-screen displaying languages and x-ray subjects that can be touched to play their respective instruction files, then call me: 979-436-2192.")
//      textColor(android.graphics.Color.BLACK)
//    }
//  }
//}
//
//fun viewSubject(dispatch: (com.brianegan.bansa.Action) -> BansaState,
//                instruction: Instruction) {
//  appCompatTextView {
//    size(FILL, WRAP)
//    text(instruction.subject + " - " + instruction.language)
//    margin(dip(0), dip(16))
//    textColor(android.graphics.Color.BLACK)
//    onClick { v ->
//      dispatch(Action.NavigateTo(Scene.Instruction()))
//      dispatch(Action.PlayInstruction(instruction))
//    }
//  }
//}
//
//fun viewSubjects(dispatch: (com.brianegan.bansa.Action) -> BansaState,
//                 instructions: ImmutableSet<Instruction>) {
//  scrollView {
//    size(FILL, dip(0))
//    weight(1f)
//
//    linearLayoutCompat {
//      size(FILL, WRAP)
//      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
////        backgroundColor(android.graphics.Color.argb(32, 0, 0, 255))
//      instructions.map { i -> viewSubject(dispatch, i) }
//    }
//  }
//}
//
//fun failureMessage(unparsableInstruction: UnparsableInstruction): String {
//  return when (unparsableInstruction.failure) {
//    is InstructionParsingFailure.FileNameFormatFailure ->
//      "${unparsableInstruction.fileName}: Not of the format <x-ray subject>_<language>_<cue time in milliseconds>.ogg (or appropriate file extension)."
//
//    is InstructionParsingFailure.CueTimeFailure ->
//      "${unparsableInstruction.fileName}: Segment following the second underscore, which needs to be the cue time in milliseconds, cannot be parsed to a number."
//  }
//}
//
//fun viewUnparsableSubject(unparsableInstruction: UnparsableInstruction) {
//  appCompatTextView {
//    size(FILL, WRAP)
//    text(failureMessage(unparsableInstruction))
//    margin(dip(0), dip(0), dip(0), dip(8))
//    textColor(android.graphics.Color.RED)
//  }
//}
//
//fun viewUnparsableSubjects(unparsableInstructions: ImmutableSet<UnparsableInstruction>) {
//  scrollView {
//    size(FILL, dip(200))
//    weight(1f)
//
//    linearLayoutCompat {
//      size(FILL, WRAP)
//      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//      backgroundColor(android.graphics.Color.argb(0, 32, 0, 255))
//
//      appCompatTextView {
//        size(FILL, WRAP)
//        text("The following instruction files could not be read:")
//        margin(dip(0), dip(0))
//        textColor(android.graphics.Color.RED)
//      }
//
//      unparsableInstructions.map(::viewUnparsableSubject)
//
//      appCompatTextView {
//        size(FILL, WRAP)
//        text("Ensure the instruction audio-files are named <x-ray subject>_<language>_<cue time in milliseconds>.ogg (or appropriate file extension)\n     Ex: chest_english_9000.ogg\nInclude spaces and/or punctuation in the subject or language via URI encoding:\nhttps://en.wikipedia.org/wiki/Percent-encoding\n     Ex: one%20%2f%20two%20%28three%29_english_1000.ogg will be parsed to\n             subject: one / two (three)\n             language: english\n             cue time: 1000")
//        margin(dip(0), dip(10), dip(0), dip(0))
//        textColor(android.graphics.Color.RED)
//      }
//    }
//  }
//}
//
//
//
//fun viewMainSuccess(c: Context, store: Store<BansaState>) {
//  linearLayoutCompat {
//    size(FILL, WRAP)
//    AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//
//    if (store.state.instructions.isEmpty()) {
//      viewNoSubjects(store.state.canReadInstructionFilesMessage,
//                     "<device>/InternalStorage/${c.packageName}")
//
//    }
//    else {
//      val dispatch = { x: com.brianegan.bansa.Action -> store.dispatch(x) }
//
//      viewLanguages(dispatch, c, store.state.languages)
//      viewSubjects(dispatch,
//                   store.state.instructions.filter { i ->
//                     i.language == store.state.language
//                   }.toImmutableSet())
//    }
//
//    if (store.state.unparsableInstructions.isNotEmpty()) {
//      viewUnparsableSubjects(store.state.unparsableInstructions)
//    }
//  }
//}
//
//fun viewMainError(message: String) {
//  appCompatTextView {
//    text("Cannot read files:\n${message}")
//    textColor(android.graphics.Color.BLACK)
//  }
//}
//
//fun viewMain(c: Context, store: Store<BansaState>) {
//  if (store.state.canReadInstructionFiles) {
//    viewMainSuccess(c, store)
//  } else {
//    viewMainError(store.state.canReadInstructionFilesMessage)
//  }
//}
//
//fun viewInstruction(store: Store<BansaState>) {
//  linearLayoutCompat {
//    AppCompatv7DSL.orientation(LinearLayout.VERTICAL)
//
//    linearLayoutCompat {
//      size(WRAP, WRAP)
//      layoutGravity(LEFT)
//      AppCompatv7DSL.orientation(LinearLayout.VERTICAL)
//
//      appCompatTextView {
//        text(store.state.subjectToDisplay)
//        textColor(android.graphics.Color.BLACK)
//      }
//
//      appCompatTextView {
//        text(store.state.languageToDisplay)
//        textColor(android.graphics.Color.BLACK)
//      }
//    }
//
//    store.state.instructionLoadingMessage?.let {
//      text(store.state.instructionLoadingMessage)
//      textColor(android.graphics.Color.BLACK)
//    }
//
//    store.state.countDownValue?.let {
//      appCompatTextView {
//        text(store.state.countDownValue.toString())
//        textColor(android.graphics.Color.BLACK)
//        textSize(sip(32F))
//      }
//    }
//
//    store.state.cueMessage?.let {
//      appCompatTextView {
//        text(store.state.cueMessage)
//        textColor(android.graphics.Color.BLACK)
//        textSize(sip(32F))
//      }
//    }
//  }
//}

//class RootView(val c: Context) : RenderableViewCoordinatorLayout(c) {
//  var store: Store<BansaState>
//
//  init {
//    this.store = (c.applicationContext as App).store
//  }
//
//  override fun view() {
//    linearLayoutCompat {
//      size(FILL, FILL)
//      AppCompatv7DSL.orientation(LinearLayout.VERTICAL)
//      BaseDSL.layoutGravity(BaseDSL.TOP)
//
//      if (store.state.isInitializing) {
//        viewInitializing()
//      }
//      else {
//        // 2016-09-21 Cort Spellman
//        // Bind the result of the when expression in order to force the compiler to
//        // check for exhaustiveness.
//        // Be on the lookout for sealed whens in Kotlin, which always check for
//        // exhaustiveness.
//        val x = when (store.state.navigationStack.peek()) {
//          is Scene.Main -> viewMain(c, store)
//          is Scene.Instruction -> viewInstruction(store)
//        }
//      }
//    }
//
//    appCompatTextView {
//      size(FILL, WRAP)
//      BaseDSL.layoutGravity(BaseDSL.BOTTOM)
//      text("Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}")
//      textColor(android.graphics.Color.BLACK)
//      DSL.gravity(BaseDSL.CENTER)
//      BaseDSL.textSize(BaseDSL.sip(12F))
//    }
//  }
//}
