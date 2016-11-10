package com.cws.image

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.support.v4.content.ContextCompat
import android.util.Log
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store
import com.github.andrewoma.dexx.kollection.*
import java.io.File
import java.net.URLDecoder
import io.reactivex.Observable
import java.io.IOException

// 2016-10-19 Cort Spellman
// I made this generic Result by modifying the Result class here:
// https://github.com/kittinunf/Result/blob/master/result/src/main/kotlin/com/github/kittinunf/result/Result.kt
sealed class Result<out A, out B> {
  class Err<out A: Any, out B: Any>(val errValue: A) : Result<A, B>() {
    override fun toString() = "${this.javaClass.canonicalName} Err: ${errValue}"

    override fun hashCode(): Int = errValue.hashCode()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Err<*, *> && errValue == other.errValue
    }
  }

  class Ok<out A: Any, out B: Any>(val okValue: B) : Result<A, B>() {
    override fun toString() = "${this.javaClass.canonicalName} Ok: ${okValue}"

    override fun hashCode(): Int = okValue.hashCode()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Ok<*, *> && okValue == other.okValue
    }
  }
}

sealed class InstructionParsingFailure {
  class FileNameFormatFailure() : InstructionParsingFailure()
  class CueTimeFailure() : InstructionParsingFailure()
}

data class UnparsableInstruction(val fileName: String,
                                 val failure: InstructionParsingFailure)

data class GetInstructionsPartialResult(
               val storageDir: File,
               var tokenFile: File,
               var parsedInstructions: ImmutableSet<Instruction>,
               var parseFailures: ImmutableSet<UnparsableInstruction>)

class InstructionFiles(val context: Context) : Middleware<State> {
  fun isExternalStorageWritable(): Boolean {
    val s = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == s
  }

  fun isExternalStorageReadable(): Boolean {
    val s = Environment.getExternalStorageState()
    return immutableSetOf(Environment.MEDIA_MOUNTED,
                          Environment.MEDIA_MOUNTED_READ_ONLY
                         ).contains(s)
  }

  fun fileToInstruction(file: File): Result<UnparsableInstruction, Instruction> {
    Log.d("fileToInstruction", "Instructions file to parse: ${file.absolutePath}")
    return try {
      val (subject, language, cueTime) =
          file.name.substringBeforeLast(".")
              .split('_')
              .map { s -> URLDecoder.decode(s, "UTF-8") }

      Log.d("parsed values", "subject: ${subject}    language: ${language}    cueStartTime: ${cueTime}")

      Result.Ok(
          Instruction(
              subject = subject,
              language = language,
              path = file.absolutePath,
              cueStartTime = cueTime.toLong()))
    }
    catch (e: IndexOutOfBoundsException) {
      Log.e("parse failure", e.toString())
      Result.Err(
          UnparsableInstruction(file.name,
                                InstructionParsingFailure.FileNameFormatFailure()))
    }
    catch (e: NumberFormatException) {
      e.printStackTrace()
      Log.e("parse failure", e.toString())
      Result.Err(
          UnparsableInstruction(file.name,
                                InstructionParsingFailure.CueTimeFailure()))
    }
  }

  val getStorageDir: (String) -> Observable<File> = { packageName ->
    if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")

    }
    else {
      val rootDir = Environment.getExternalStorageDirectory()
      if (!rootDir.isDirectory) {
        throw IOException("External storage is not readable.")
      }
      else {
        val storageDir = File(rootDir, packageName)
        if (!storageDir.isDirectory) {
          if (!isExternalStorageWritable()) {
            throw IOException("There is no directory at instructions-directory path, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")

          }
          else {
            if (!storageDir.mkdirs()) {
              throw IOException("Could not create directory ${storageDir.absolutePath}, even though external storage is writable.")
            }
            else {
              Observable.just<File>(storageDir)
            }
          }
        }
        else {
          Observable.just<File>(storageDir)
        }
      }
    }
  }

  val ensureTokenFileExists: (File) -> Observable<GetInstructionsPartialResult> = { storageDir ->
    val tokenFileName = ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb"
    val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb =
        File(storageDir, tokenFileName)

    if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
      if (!isExternalStorageWritable()) {
        throw IOException("There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")
      }
      else {
        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
          throw IOException("Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.")
        }
        else {
          Log.d("ExternalStorage",
                "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
          Observable.create<GetInstructionsPartialResult> { emitter ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString()),
                null,
                { path, uri ->
                  if (uri is Uri) {
                    Log.d("ExternalStorage", "Scanned ${path}:")
                    Log.d("ExternalStorage", "-> uri=${uri}")
                    emitter.onNext(
                        GetInstructionsPartialResult(
                            storageDir = storageDir,
                            tokenFile = tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb,
                            parsedInstructions = immutableSetOf(),
                            parseFailures = immutableSetOf()))
                  }
                  else {
                    emitter.onError(IOException("A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed."))
                  }
                })
          }
        }
      }
    }
    else {
      Observable.just<GetInstructionsPartialResult>(
          GetInstructionsPartialResult(
              storageDir = storageDir,
              tokenFile = tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb,
              parsedInstructions = immutableSetOf(),
              parseFailures = immutableSetOf()))
    }
  }

  val readInstructionsFromStorage: (GetInstructionsPartialResult) -> GetInstructionsPartialResult = { r ->
    val storageDir = r.storageDir
    val filesToSkip = immutableSetOf(r.tokenFile)

    storageDir.listFiles().forEach { file ->
      Log.d("storageDir file", file.absolutePath) }

    val parseResults: List<Result<UnparsableInstruction, Instruction>> =
        storageDir.listFiles()
            .filter { file -> !filesToSkip.contains(file) }
            .map { file -> fileToInstruction(file) }

    r.copy(
        parsedInstructions =
        parseResults.filterIsInstance<Result.Ok<String, Instruction>>()
            .map { x -> x.okValue }.toImmutableSet(),
        parseFailures =
        parseResults.filterIsInstance<Result.Err<UnparsableInstruction, Instruction>>()
            .map { x -> x.errValue }.toImmutableSet())
  }

  fun refreshInstructions(store: Store<State>) {
    if (ContextCompat.checkSelfPermission(context,
                                          Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      store.dispatch(
          Action.SetInstructionsAndLanguages(
              canReadInstructionFiles = false,
              canReadInstructionFilesMessage = "Permission to write to external storage is required and has not been granted.",
              instructions = immutableSetOf(),
              unparsableInstructions = immutableSetOf()))
    }
    else {
      getStorageDir(context.packageName)
          .flatMap(ensureTokenFileExists)
          .retry(5)
          .map(readInstructionsFromStorage)
          .subscribe(
              { r ->
                store.dispatch(
                    Action.SetInstructionsAndLanguages(
                        canReadInstructionFiles = true,
                        canReadInstructionFilesMessage = "Read instructions from ${r.storageDir.absolutePath}.",
                        instructions = r.parsedInstructions,
                        unparsableInstructions = r.parseFailures)) },
              { err ->
                  store.dispatch(
                      Action.SetInstructionsAndLanguages(
                          canReadInstructionFiles = false,
                          canReadInstructionFilesMessage = err.message as? String ?: "",
                          instructions = immutableSetOf(),
                          unparsableInstructions = immutableSetOf())) })
    }
  }

  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    when (action) {
      is Action.RefreshInstructions -> {
        next.dispatch(action)
        refreshInstructions(store)
        store.dispatch(Action.DidInitialize())
      }

      else -> next.dispatch(action)
    }
  }
}
