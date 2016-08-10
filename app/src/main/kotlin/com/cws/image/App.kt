package com.cws.image

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import trikita.anvil.RenderableView
import trikita.jedux.Action
import trikita.jedux.Logger
import trikita.jedux.Store
import java.io.File

val languages = immutableListOf("english",
                                "spanish",
                                "tagalog",
                                "french",
                                "german",
                                "russian",
                                "ethiopian")

data class InstructionIdent(val subject: String, val language: String)

val instructions =
    immutableListOf(
        InstructionIdent("chest", "english"),
        InstructionIdent("leg", "english"),
        InstructionIdent("arm", "english"),
        InstructionIdent("chest", "spanish"),
        InstructionIdent("leg", "spanish"),
        InstructionIdent("arm", "spanish"),
        InstructionIdent("chest", "tagalog"),
        InstructionIdent("leg", "tagalog"),
        InstructionIdent("arm", "tagalog"),
        InstructionIdent("chest", "french"),
        InstructionIdent("leg", "french"),
        InstructionIdent("arm", "french"),
        InstructionIdent("chest", "german"),
        InstructionIdent("leg", "german"),
        InstructionIdent("arm", "german"),
        InstructionIdent("chest", "russian"),
        InstructionIdent("leg", "russian"),
        InstructionIdent("arm", "russian"),
        InstructionIdent("chest", "ethiopian"),
        InstructionIdent("leg", "ethiopian"),
        InstructionIdent("arm", "ethiopian")
    )

val instructionsBySubjectLanguagePair =
    immutableMapOf(
        Pair(InstructionIdent("chest", "english"),
             Instruction(subject = "chest",
                         language = "english",
                         path = "chest/english/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "english"),
             Instruction(subject = "leg",
                         language = "english",
                         path = "leg/english/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "english"),
             Instruction(subject = "arm",
                         language = "english",
                         path = "arm/english/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "spanish"),
             Instruction(subject = "chest",
                         language = "spanish",
                         path = "chest/spanish/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "spanish"),
             Instruction(subject = "leg",
                         language = "spanish",
                         path = "leg/spanish/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "spanish"),
             Instruction(subject = "arm",
                         language = "spanish",
                         path = "arm/spanish/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "tagalog"),
             Instruction(subject = "chest",
                         language = "tagalog",
                         path = "chest/tagalog/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "tagalog"),
             Instruction(subject = "leg",
                         language = "tagalog",
                         path = "leg/tagalog/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "tagalog"),
             Instruction(subject = "arm",
                         language = "tagalog",
                         path = "arm/tagalog/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "french"),
             Instruction(subject = "chest",
                         language = "french",
                         path = "chest/french/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "french"),
             Instruction(subject = "leg",
                         language = "french",
                         path = "leg/french/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "french"),
             Instruction(subject = "arm",
                         language = "french",
                         path = "arm/french/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "german"),
             Instruction(subject = "chest",
                         language = "german",
                         path = "chest/german/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "german"),
             Instruction(subject = "leg",
                         language = "german",
                         path = "leg/german/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "german"),
             Instruction(subject = "arm",
                         language = "german",
                         path = "arm/german/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "russian"),
             Instruction(subject = "chest",
                         language = "russian",
                         path = "chest/russian/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "russian"),
             Instruction(subject = "leg",
                         language = "russian",
                         path = "leg/russian/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "russian"),
             Instruction(subject = "arm",
                         language = "russian",
                         path = "arm/russian/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("chest", "ethiopian"),
             Instruction(subject = "chest",
                         language = "ethiopian",
                         path = "chest/ethiopian/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("leg", "ethiopian"),
             Instruction(subject = "leg",
                         language = "ethiopian",
                         path = "leg/ethiopian/path",
                         cueTiming = 1000)),
        Pair(InstructionIdent("arm", "ethiopian"),
             Instruction(subject = "arm",
                         language = "ethiopian",
                         path = "arm/ethiopian/path",
                         cueTiming = 1000))
    )

data class Instruction(val subject: String,
                       val language: String,
                       val path: String,
                       val cueTiming: Int)

fun ident(instruction: Instruction): InstructionIdent {
  return InstructionIdent(subject = instruction.subject, language = instruction.language)
}

data class IsInstructionsDirReadableActionValue(val isReadable: Boolean,
                                                val reason: String?)

data class State(val isInstructionsDirReadable: IsInstructionsDirReadableActionValue,
                 val languages: ImmutableList<String>,
                 val language: String,
                 val instructions: ImmutableList<InstructionIdent>,
                 val instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                 val navigationStack: NavigationStack)

enum class Actions {
  INIT,
  SET_IS_INSTRUCTIONS_DIR_READABLE,
  SHOW_CURRENT_VIEW,
  NAVIGATE_TO,
  NAVIGATE_BACK,
  SET_LANGUAGE,
  PLAY_INSTRUCTION
}

data class NavigationActionValue(val value: Any?, val activity: Activity)

fun setIsInstructionsDirReadable(isReadable: Boolean, reason: String?): Action<Actions, IsInstructionsDirReadableActionValue> {
  return Action(Actions.SET_IS_INSTRUCTIONS_DIR_READABLE,
                IsInstructionsDirReadableActionValue(isReadable, reason))
}

fun setLanguage(language: String): Action<Actions, String> {
  return Action(Actions.SET_LANGUAGE,
                language)
}

fun playInstruction(instruction: Instruction): Action<Actions, Instruction> {
  return Action(Actions.PLAY_INSTRUCTION,
                instruction)
}

fun showCurrentView(activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.SHOW_CURRENT_VIEW,
                NavigationActionValue(null, activity))
}

fun navigateTo(navigationFrame: NavigationFrame, activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.NAVIGATE_TO,
                NavigationActionValue(navigationFrame, activity))
}

fun navigateBack(activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.NAVIGATE_BACK,
                NavigationActionValue(null, activity))
}

fun reduceIsInstructionsDirReadable(state: IsInstructionsDirReadableActionValue, action: Action<Actions, *>): IsInstructionsDirReadableActionValue {
  return when(action.type) {
    Actions.SET_IS_INSTRUCTIONS_DIR_READABLE -> {
      val isReadable = action.value
      if (isReadable is IsInstructionsDirReadableActionValue) isReadable
      else state
    }

    else -> state
  }
}

fun reduceLanguage(state: String, action: Action<Actions, *>): String {
  return when(action.type) {
    Actions.SET_LANGUAGE -> {
      val language = action.value
      if (language is String) language
      else state
    }

    else -> state
  }
}

fun reduceNavigation(state: NavigationStack, action: Action<Actions, *>): NavigationStack {
  return when(action.type) {
    Actions.NAVIGATE_TO -> {
      val navigationFrame = action.value
      if (navigationFrame is NavigationFrame) state.push(navigationFrame)
      else state
    }

    Actions.NAVIGATE_BACK -> state.pop()

    else -> state
  }
}

class Reducer: Store.Reducer<Action<Actions, *>, State> {
  override fun reduce(action: Action<Actions, *>, state: State): State {
    return state.copy(isInstructionsDirReadable = reduceIsInstructionsDirReadable(state.isInstructionsDirReadable, action),
                      language = reduceLanguage(state.language, action),
                      navigationStack = reduceNavigation(state.navigationStack, action))
  }
}

class Navigator: Store.Middleware<Action<Actions, *>, State> {
  fun makeView(scene: String, props: Any?, context: Context): RenderableView {
    return when(scene) {
      "main" -> MainView(context)
      "instruction" -> InstructionView(context, props as InstructionProps)
      else -> MainView(context)
    }
  }

  fun render(activity: Activity, navigationStack: NavigationStack) {
    val frameToRender = navigationStack.peek()
    val v = makeView(frameToRender.scene,
                     frameToRender.props,
                     activity)
    activity.setContentView(v)
  }

  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    when(action.type) {
      Actions.SHOW_CURRENT_VIEW -> {
        val value = action.value
        if (value is NavigationActionValue) {
          render(value.activity, store.state.navigationStack)
        }
      }

      Actions.NAVIGATE_TO -> {
        val value = action.value
        if (value is NavigationActionValue) {
          next.dispatch(Action(action.type, value.value))
          render(value.activity, store.state.navigationStack)
        }
      }

      Actions.NAVIGATE_BACK -> {
        val value = action.value
        if (value is NavigationActionValue) {
          if (store.state.navigationStack.frames.size == 1
              || store.state.navigationStack.frames.isEmpty()) {
            value.activity.finish()
          } else {
            println("ABOUT TO DISPATCH NAVIGATE_BACK TO REDUCER")
            next.dispatch(Action(action.type, value.value))
            render(value.activity, store.state.navigationStack)
          }
        }
      }

      else -> next.dispatch(action)
    }
  }
}

fun getInstruction(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                   k: InstructionIdent): Instruction? {
  return instructionsBySubjectLanguagePair[k]
}

fun getInstructions(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                    ks: ImmutableList<InstructionIdent>): ImmutableList<Instruction> {
  return ks.mapNotNull { k -> getInstruction(instructionsBySubjectLanguagePair, k) }
           .toImmutableList()
}

fun getInstructions(state: State): ImmutableList<Instruction> {
  return getInstructions(state.instructionsBySubjectLanguagePair,
                         state.instructions)
}

fun getVisibleInstructions(instructions: ImmutableList<Instruction>, language: String): ImmutableList<Instruction> {
  return instructions.filter { i -> i.language == language }.toImmutableList()
}

class App : Application() {
  val appDir = File(Environment.getExternalStorageDirectory(), "com.cws.image")
  val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb =
      File(appDir, ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb")

  val initialLanguage = "english"

  val normalizedInstructions = normalizeInstructions(readInstructionsFromStorage(appDir))

  val initialState =
      State(isInstructionsDirReadable = IsInstructionsDirReadableActionValue(
          false,
          "Initially assume instructions dir is not readable because it hasn't been checked for readability."),
            languages = languages,
            language = initialLanguage,
            instructions = normalizedInstructions.instructions,
            instructionsBySubjectLanguagePair = normalizedInstructions.instructionsBySubjectLanguagePair,
            navigationStack = NavigationStack(
                immutableListOf(
                    NavigationFrame("main", null))))

  val store: Store<Action<Actions, *>, State> = Store(Reducer(),
                                                      initialState,
                                                      Logger("Image"),
                                                      Navigator())
  override fun onCreate() {
    super.onCreate()
    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
    store.subscribe(Anvil::render)
    ensureExternalFilesDirExistsAndIsAccessibleViaUsbConnection(appDir)
  }

  fun fileToInstruction(file: File): Instruction? {
    val n: String = file.name.substringBeforeLast(".")
    val (subject, language, cueTiming) = n.split('_')

    println()
    println("Instructions file to parse: ${file.absolutePath}")
    println("subject: ${subject}    language: ${language}    cueTiming: ${cueTiming}")
    try {
      return Instruction(subject = subject,
                         language = language,
                         path = file.absolutePath,
                         cueTiming = cueTiming.toInt())
    }
    catch (e: NumberFormatException) {
      Log.e("parse instructions file", e.toString())
      return null
    }
  }

  fun readInstructionsFromStorage(appDir: File): ImmutableList<Instruction> {
    appDir.listFiles().forEach { file -> println(file.absolutePath) }
    val r = appDir.listFiles({ file -> file != tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb })
                 .mapNotNull {file -> fileToInstruction(file)}
                 .toImmutableList()
    println()
    println("Parsed instructions:")
    println(r)
    return r
  }

  data class NormalizedInstructionsResult(val instructions: ImmutableList<InstructionIdent>,
                                          val instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>)

  fun normalizeInstructions(instructions: ImmutableList<Instruction>): NormalizedInstructionsResult {
    return NormalizedInstructionsResult(
             instructions = instructions.map(::ident).toImmutableList(),
             instructionsBySubjectLanguagePair = instructions.map { i -> Pair(ident(i), i)}
                                                             .groupBy { p: Pair<InstructionIdent, Instruction> -> p.first }
                                                             .map { p: Map.Entry<InstructionIdent, List<Pair<InstructionIdent, Instruction>>> ->
                                                                    Pair(p.key, p.value.last().second) }
                                                             .toImmutableMap())
  }

  fun ensureExternalFilesDirExistsAndIsAccessibleViaUsbConnection(appDir: File) {
    if (!isExternalStorageReadable()) {
      store.dispatch(setIsInstructionsDirReadable(false,
                                                  "External storage is not readable."))
    }
    else {
      Log.i("files", ".")
      Log.i("files", ".")
      Log.i("files", "appDir.absolutePath")
      Log.i("files", appDir.absolutePath)
      Log.i("files", ".")
      Log.i("files", ".")

      if (!appDir.isDirectory || appDir.listFiles().isEmpty()) {
        if (!isExternalStorageWritable()) {
          store.dispatch(setIsInstructionsDirReadable(false,
                                                      "There is no directory at instructions-directory path, ${appDir.absolutePath} or the directory is empty; it can't be created because external storage is not writable."))
        }
        else {
          appDir.deleteRecursively()
          if (!appDir.mkdirs()) {
            store.dispatch(setIsInstructionsDirReadable(false,
                                                        "Could not make directory ${appDir.absolutePath}, even though external storage is writable."))
          }
          else {
            if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
              store.dispatch(setIsInstructionsDirReadable(false,
                                                          "Could not make file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable."))
            }
            else {
              MediaScannerConnection.scanFile(this as Context,
                                              arrayOf(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString()),
                                              null,
                                              { path: String, uri: Uri ->
                                                Log.i("ExternalStorage", "Scanned ${path}:")
                                                Log.i("ExternalStorage", "-> uri=${uri}")
                                              })
              store.dispatch(setIsInstructionsDirReadable(true,
                                                          "Made directory ${appDir.absolutePath} and file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB)."))
            }
          }
        }
      }
      else {
        store.dispatch(setIsInstructionsDirReadable(true,
                                                    "${appDir.absolutePath} is a directory and ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} is a file (to make directory appear when device is mounted via USB)."))
      }
    }
  }
}



data class NavigationFrame(val scene: String, val props: Any?)

class NavigationStack(val frames: ImmutableList<NavigationFrame>) {
  fun push(nf: NavigationFrame): NavigationStack {
    return NavigationStack(frames.plus(nf))
  }

  fun pop(): NavigationStack {
    return NavigationStack(frames.dropLast(1))
  }

  fun peek(): NavigationFrame {
    return frames.last()
  }
}



fun isExternalStorageWritable(): Boolean {
  val s = Environment.getExternalStorageState()
  return Environment.MEDIA_MOUNTED == s
}

fun isExternalStorageReadable(): Boolean {
  val s = Environment.getExternalStorageState()
  return Environment.MEDIA_MOUNTED == s
         || Environment.MEDIA_MOUNTED_READ_ONLY == s
}
