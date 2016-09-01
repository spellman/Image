package com.cws.image

import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.andrewoma.dexx.kollection.*
import trikita.jedux.Action
import trikita.jedux.Store
import java.io.File

data class NormalizedInstructionsResult(val instructions: ImmutableSet<InstructionIdent>,
                                        val instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>)

fun normalizeInstructions(instructions: ImmutableSet<Instruction>): NormalizedInstructionsResult {
  return NormalizedInstructionsResult(
      instructions = instructions.map(::ident).toImmutableSet(),
      instructionsBySubjectLanguagePair = instructions.map { i -> Pair(ident(i), i) }
          .groupBy { p: Pair<InstructionIdent, Instruction> -> p.first }
          .map { p: Map.Entry<InstructionIdent, List<Pair<InstructionIdent, Instruction>>> ->
            Pair(p.key, p.value.last().second)
          }
          .toImmutableMap())
}

data class SetInstructionsAndLanguagesActionValue(val canReadInstructionFiles: Boolean,
                                                  val canReadInstructionFilesMessage: String,
                                                  val instructions: ImmutableSet<Instruction>)

data class InstructionFilesResult(val canReadInstructionFiles: Boolean,
                                  val canReadInstructionFilesMessage: String,
                                  val instructions: ImmutableSet<InstructionIdent>,
                                  val instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                                  val languages: ImmutableSet<String>)

fun reduceInstructionsAndLanguages(action: Action<Actions, *>, state: InstructionFilesResult): InstructionFilesResult {
  return when(action.type) {
    Actions.SET_INSTRUCTIONS_AND_LANGUAGES -> {
      // 2016-08-16 Cort Spellman
      // I want to say instructions is ImmutableSet<Instruction> but the compiler won't let me.
      // https://stackoverflow.com/questions/13154463/how-can-i-check-for-generic-type-in-kotlin
      // I think the guy who answered, Andrey Breslav, works on Kotlin.
      val v = action.value as SetInstructionsAndLanguagesActionValue
      val normalizedInstructions = normalizeInstructions(v.instructions)
      return InstructionFilesResult(
          v.canReadInstructionFiles,
          v.canReadInstructionFilesMessage,
          normalizedInstructions.instructions,
          normalizedInstructions.instructionsBySubjectLanguagePair,
          getLanguages(normalizedInstructions.instructionsBySubjectLanguagePair)) }
    else -> state
  }
}

fun setInstructionsAndLanguages(canReadInstructionFiles: Boolean,
                                canReadInstructionFilesMessage: String,
                                instructions: ImmutableSet<Instruction>): Action<Actions, SetInstructionsAndLanguagesActionValue> {
  return Action(Actions.SET_INSTRUCTIONS_AND_LANGUAGES,
                SetInstructionsAndLanguagesActionValue(canReadInstructionFiles,
                                                       canReadInstructionFilesMessage,
                                                       instructions))
}

data class RefreshInstructionsActionValue(
             val mediaScannerConnection: MediaScannerConnection,
             val appDir: File,
             val instructionsFilesUpdateFn: (ImmutableSet<Instruction>) -> Action<Actions, SetInstructionsAndLanguagesActionValue>)

fun refreshInstructions(mediaScannerConnection: MediaScannerConnection,
                        appDir: File,
                        updateFn: (ImmutableSet<Instruction>) -> Action<Actions, SetInstructionsAndLanguagesActionValue>): Action<Actions, RefreshInstructionsActionValue> {
  return Action(Actions.REFRESH_INSTRUCTIONS,
                RefreshInstructionsActionValue(mediaScannerConnection, appDir, updateFn))
}

class MediaScannerConnectionClient: MediaScannerConnection.MediaScannerConnectionClient {
  override fun onMediaScannerConnected() {}

  override fun onScanCompleted(path: String?, uri: Uri?) {
    Log.d("ExternalStorage", "Scanned ${path}:")
    Log.d("ExternalStorage", "-> uri=${uri}")
  }
}

class InstructionFiles: Store.Middleware<Action<Actions, *>, State> {
  val tokenFileName = ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb"
  val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule())

  fun isExternalStorageWritable(): Boolean {
    val s = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == s
  }

  fun isExternalStorageReadable(): Boolean {
    val s = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == s
        || Environment.MEDIA_MOUNTED_READ_ONLY == s
  }

  fun fileToInstruction(file: File): Instruction? {
    val n: String = file.name.substringBeforeLast(".")
    val (subject, language, cueTiming) = n.split('_')

    Log.d("fileToInstruction", "Instructions file to parse: ${file.absolutePath}")
    Log.d("parsed values", "subject: ${subject}    language: ${language}    cueTiming: ${cueTiming}")
    try {
      return Instruction(subject = subject,
                         language = language,
                         path = file.absolutePath,
                         cueTiming = cueTiming.toInt())
    }
    catch (e: NumberFormatException) {
      Log.e("parse instructions file",
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(e))
      return null
    }
  }

  fun readInstructionsFromStorage(appDir: File, filesToSkip: ImmutableSet<File>): ImmutableSet<Instruction> {
    appDir.listFiles().forEach { file -> Log.d("appDir file", file.absolutePath) }
    val r = appDir.listFiles({ file -> !filesToSkip.contains(file) })
                  .mapNotNull {file -> fileToInstruction(file)}
                  .toImmutableSet()
    Log.d("Parsed instructions",
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(r))
    return r
  }

  fun haveInstructionsFilesToRead(dir: File): Boolean {
    return dir.isDirectory && dir.listFiles().isNotEmpty()
  }

  fun create(file: File): Boolean {
    return file.createNewFile()
  }

  fun refreshInstructions(store: Store<Action<Actions, *>, State>,
                          mediaScannerConnection: MediaScannerConnection,
                          appDir: File,
                          instructionsFilesUpdateFn: (ImmutableSet<Instruction>) -> Action<Actions, SetInstructionsAndLanguagesActionValue>) {
    val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb = File(appDir, tokenFileName)

    if (!isExternalStorageReadable()) {
      store.dispatch(
          setInstructionsAndLanguages(
              false,
              "External storage is not readable.",
              immutableSetOf()))
      return
    }

    if (!haveInstructionsFilesToRead(appDir)) {
      if (!isExternalStorageWritable()) {
        store.dispatch(
            setInstructionsAndLanguages(
                false,
                "There is no directory at instructions-directory path, ${appDir.absolutePath} or the directory is empty; it can't be created because external storage is not writable.",
                immutableSetOf()))
        return
      }

      appDir.deleteRecursively()

      if (!appDir.mkdirs()) {
        store.dispatch(
            setInstructionsAndLanguages(
                false,
                "Could not make directory ${appDir.absolutePath}, even though external storage is writable.",
                immutableSetOf()))
        return
      }

      if (!create(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb)) {
        store.dispatch(
            setInstructionsAndLanguages(
                false,
                "Could not make file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.",
                immutableSetOf()))
        return
      }

      mediaScannerConnection.connect()
      mediaScannerConnection.scanFile(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString(),
                                      null)

      Log.d("ExternalStorage", "Made directory ${appDir.absolutePath} and file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
    }

    store.dispatch(
        instructionsFilesUpdateFn(
            readInstructionsFromStorage(appDir, immutableSetOf(File(appDir, tokenFileName)))))
  }

  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    when(action.type) {
      Actions.REFRESH_INSTRUCTIONS -> {
        val v = action.value as RefreshInstructionsActionValue
        refreshInstructions(store,
                            v.mediaScannerConnection,
                            v.appDir,
                            v.instructionsFilesUpdateFn)
        next.dispatch(action)
      }

      else -> next.dispatch(action)
    }
  }
}
