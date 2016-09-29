package com.cws.image

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.Store
import com.github.andrewoma.dexx.kollection.*
import java.io.File
import java.net.URLDecoder

val instructionFiles = Middleware<State> { store, action, next ->
  val tokenFileName = ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb"

  // 2016-09-02 Cort Spellman
  // TODO: Check for storage permission and request it if necessary.
  // If you can't get the permission (i.e., don't have it after requesting),
  // then give that reason for not being able to make the directory.

  // 2016-09-02 Cort Spellman
  // TODO: Make part of the state whether the directory exists or not?
  fun isExternalStorageWritable(): Boolean {
    val s = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == s
  }

  fun isExternalStorageReadable(): Boolean {
    val s = Environment.getExternalStorageState()
    return immutableSetOf(
             Environment.MEDIA_MOUNTED,
             Environment.MEDIA_MOUNTED_READ_ONLY
           ).contains(s)
  }

  fun fileToInstruction(file: File): Instruction? {
    val n: String = file.name.substringBeforeLast(".")
    val (subject, language, cueTime) = n.split('_').map { s -> URLDecoder.decode(s, "UTF-8") }

    Log.d("fileToInstruction", "Instructions file to parse: ${file.absolutePath}")
    Log.d("parsed values", "subject: ${subject}    language: ${language}    cueStartTime: ${cueTime}")
    try {
      return Instruction(subject = subject,
                         language = language,
                         path = file.absolutePath,
                         cueStartTime = cueTime.toInt())
    }
    catch (e: NumberFormatException) {
      Log.e("parse instructions file", e.toString())
      return null
    }
  }

  fun readInstructionsFromStorage(appDir: File, filesToSkip: ImmutableSet<File>): ImmutableSet<Instruction> {
    appDir.listFiles().forEach { file -> Log.d("appDir file", file.absolutePath) }
    val r = appDir.listFiles({ file -> !filesToSkip.contains(file) })
                  .mapNotNull {file -> fileToInstruction(file)}
                  .toImmutableSet()
    Log.d("Parsed instructions", r.toString())
    return r
  }

  fun haveInstructionsFilesToRead(dir: File): Boolean {
    return dir.isDirectory && dir.listFiles().isNotEmpty()
  }

  fun create(file: File): Boolean {
    return file.createNewFile()
  }

  fun refreshInstructions(store: Store<State>,
                          context: Context,
                          appDir: File,
                          instructionsFilesUpdateFn: (ImmutableSet<Instruction>) -> Action.SetInstructionsAndLanguages) {
    val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb = File(appDir, tokenFileName)

    if (!isExternalStorageReadable()) {
      store.dispatch(
          Action.SetInstructionsAndLanguages(
              false,
              "External storage is not readable.",
              immutableSetOf()))
      return
    }

    if (!haveInstructionsFilesToRead(appDir)) {
      if (!isExternalStorageWritable()) {
        store.dispatch(
            Action.SetInstructionsAndLanguages(
                false,
                "There is no directory at instructions-directory path, ${appDir.absolutePath} or the directory is empty; it can't be created because external storage is not writable.",
                immutableSetOf()))
        return
      }

      appDir.deleteRecursively()

      if (!appDir.mkdirs()) {
        store.dispatch(
            Action.SetInstructionsAndLanguages(
                false,
                "Could not make directory ${appDir.absolutePath}, even though external storage is writable.",
                immutableSetOf()))
        return
      }

      if (!create(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb)) {
        store.dispatch(
            Action.SetInstructionsAndLanguages(
                false,
                "Could not make file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.",
                immutableSetOf()))
        return
      }

      MediaScannerConnection.scanFile(context,
                                      arrayOf(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString()),
                                      null,
                                      { path, uri ->
                                        Log.d("ExternalStorage", "Scanned ${path}:")
                                        Log.d("ExternalStorage", "-> uri=${uri}")
                                      })

      Log.d("ExternalStorage", "Made directory ${appDir.absolutePath} and file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
    }

    store.dispatch(
        instructionsFilesUpdateFn(
            readInstructionsFromStorage(appDir, immutableSetOf(File(appDir, tokenFileName)))))
  }

  when(action) {
    is Action.RefreshInstructions -> {
      refreshInstructions(store,
                          action.context,
                          action.appDir,
                          action.instructionFilesUpdateFn)
      next.dispatch(action)
    }

    else -> next.dispatch(action)
  }
}
