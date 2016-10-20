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

data class GetInstructionsPartialResult(
               val storageDir: File,
               var tokenFile: File,
               var parsedInstructions: ImmutableSet<Instruction>,
               var parseFailures: ImmutableSet<String>)

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

  fun fileToInstruction(file: File): Result<String, Instruction> {
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
      Result.Err("Instruction file name ${file.name} cannot be interpreted because it is not of the format <x-ray subject>_<language>_<cue time in milliseconds>.ogg (or appropriate file extension)\n     Ex: chest_english_9000.ogg\nInclude spaces and/or punctuation in the subject or language via URI encoding:\nhttps://en.wikipedia.org/wiki/Percent-encoding\n     Ex: one%20%2f%20two%20%28three%29_english_1000.ogg will be parsed to\n             subject: one / two (three)\n             language: english\n             cue time: 1000")
    }
    catch (e: NumberFormatException) {
      e.printStackTrace()
      Log.e("parse failure", e.toString())
      Result.Err("Instruction file name ${file.name} cannot be interpreted because the segment following the second underscore, which needs to be the cue time in milliseconds, cannot be parsed to a number.\nEnsure your instruction audio-files are named <x-ray subject>_<language>_<cue time in milliseconds>.ogg (or appropriate file extension)\n     Ex: chest_english_9000.ogg\nInclude spaces and/or punctuation in the subject or language via URI encoding:\nhttps://en.wikipedia.org/wiki/Percent-encoding\n     Ex: one%20%2f%20two%20%28three%29_english_1000.ogg will be parsed to\n             subject: one / two (three)\n             language: english\n             cue time: 1000")
    }
  }

  val getStorageDir: (String) -> Observable<Result<Action, File>> = { packageName ->
    if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")
//      Observable.just<Result<Action, File>>(
//          Result.Err(
//              Action.SetInstructionsAndLanguages(
//                  canReadInstructionFiles = false,
//                  canReadInstructionFilesMessage = "External storage is not readable.",
//                  parsedInstructions = immutableSetOf())))

    }
    else {
      val rootDir = Environment.getExternalStorageDirectory()
      if (!rootDir.isDirectory) {
        throw IOException("External storage is not readable.")
//        Observable.just<Result<Action, File>>(
//            Result.Err(
//                Action.SetInstructionsAndLanguages(
//                    canReadInstructionFiles = false,
//                    canReadInstructionFilesMessage = "External storage is not readable.",
//                    parsedInstructions = immutableSetOf())))
      }
      else {
        val storageDir = File(rootDir, packageName)
        if (!storageDir.isDirectory) {
          if (!isExternalStorageWritable()) {
            throw IOException("There is no directory at instructions-directory path, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")
//            Observable.just<Result<Action, File>>(
//                Result.Err(
//                    Action.SetInstructionsAndLanguages(
//                        canReadInstructionFiles = false,
//                        canReadInstructionFilesMessage = "There is no directory at instructions-directory path, ${storageDir.absolutePath} and it can't be created because external storage is not writable.",
//                        parsedInstructions = immutableSetOf())))

          }
          else {
            if (!storageDir.mkdirs()) {
              throw IOException("Could not create directory ${storageDir.absolutePath}, even though external storage is writable.")
//              Observable.just<Result<Action, File>>(
//                  Result.Err(
//                      Action.SetInstructionsAndLanguages(
//                          canReadInstructionFiles = false,
//                          canReadInstructionFilesMessage = "Could not create directory ${storageDir.absolutePath}, even though external storage is writable.",
//                          parsedInstructions = immutableSetOf())))
            }
            else {
              Observable.just<Result<Action, File>>(Result.Ok(storageDir))
            }
          }
        }
        else {
          Observable.just<Result<Action, File>>(Result.Ok(storageDir))
        }
      }
    }
  }

  val ensureTokenFileExists: (Result<Action, File>) -> Observable<Result<Action, GetInstructionsPartialResult>> = { res ->
    when (res) {
      is Result.Err -> Observable.just<Result<Action, GetInstructionsPartialResult>>(
                           Result.Err(res.errValue))

      is Result.Ok -> {
        val storageDir = res.okValue
        val tokenFileName = ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb"
        val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb =
            File(storageDir, tokenFileName)

        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
          if (!isExternalStorageWritable()) {
            throw IOException("There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")
//            Observable.just<Result<Action, GetInstructionsPartialResult>>(
//                Result.Err(
//                    Action.SetInstructionsAndLanguages(
//                        canReadInstructionFiles = false,
//                        canReadInstructionFilesMessage = "There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable.",
//                        parsedInstructions = immutableSetOf())))
          }
          else {
            if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
              throw IOException("Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.")
//              Observable.just<Result<Action, GetInstructionsPartialResult>>(
//                  Result.Err(
//                      Action.SetInstructionsAndLanguages(
//                          canReadInstructionFiles = false,
//                          canReadInstructionFilesMessage = "Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.",
//                          parsedInstructions = immutableSetOf())))
            }
            else {
              Log.d("ExternalStorage",
                    "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
              Observable.create<Result<Action, GetInstructionsPartialResult>> { emitter ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString()),
                    null,
                    { path, uri ->
                      if (uri is Uri) {
                        Log.d("ExternalStorage", "Scanned ${path}:")
                        Log.d("ExternalStorage", "-> uri=${uri}")
                        emitter.onNext(
                            Result.Ok(
                                GetInstructionsPartialResult(
                                    storageDir = res.okValue,
                                    tokenFile = tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb,
                                    parsedInstructions = immutableSetOf(),
                                    parseFailures = immutableSetOf())))
                      }
                      else {
                        emitter.onError(IOException("A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed."))
//                        emitter.onNext(
//                            Result.Err(
//                                Action.SetInstructionsAndLanguages(
//                                    canReadInstructionFiles = false,
//                                    canReadInstructionFilesMessage = "A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed.",
//                                    parsedInstructions = immutableSetOf())))
                      }
                    })
              }
            }
          }
        }
        else {
          Observable.just<Result<Action, GetInstructionsPartialResult>>(
              Result.Ok(
                  GetInstructionsPartialResult(
                      storageDir = res.okValue,
                      tokenFile = tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb,
                      parsedInstructions = immutableSetOf(),
                      parseFailures = immutableSetOf())))
        }
      }
    }
  }

  val readInstructionsFromStorage: (Result<Action, GetInstructionsPartialResult>) -> Observable<Result<Action, GetInstructionsPartialResult>> = { res ->
    when (res) {
      is Result.Err -> Observable.just<Result<Action, GetInstructionsPartialResult>>(
                         Result.Err(res.errValue))

      is Result.Ok -> {
        val storageDir = res.okValue.storageDir
        val filesToSkip = immutableSetOf(res.okValue.tokenFile)

        storageDir.listFiles().forEach { file ->
          Log.d("storageDir file", file.absolutePath) }

        val parseResults: List<Result<String, Instruction>> =
            storageDir.listFiles()
                .filter { file -> !filesToSkip.contains(file) }
                .map { file -> fileToInstruction(file) }

        Observable.just<Result<Action, GetInstructionsPartialResult>>(
            Result.Ok(
                res.okValue.copy(
                    parsedInstructions =
                        parseResults.filterIsInstance<Result.Ok<String, Instruction>>()
                            .map { x -> x.okValue }.toImmutableSet(),
                    parseFailures =
                        parseResults.filterIsInstance<Result.Err<String, Instruction>>()
                            .map { x -> x.errValue }.toImmutableSet())))
      }
    }
  }

  val ensureFilesAreUpToDate: (Result<Action, GetInstructionsPartialResult>) -> Observable<Action> = { res ->
    when (res) {
      is Result.Err -> Observable.just<Action>(res.errValue)

      is Result.Ok -> {
        val parsedInstructions = res.okValue.parsedInstructions
        val parseFailures = res.okValue.parseFailures

        val msg = if (parsedInstructions.isEmpty()) {
                    "No instructions found in ${res.okValue.storageDir.absolutePath}."
                  }
                  else {
                    "Read instructions from ${res.okValue.storageDir.absolutePath}."
                  }

        Observable.just<Action>(
            Action.SetInstructionsAndLanguages(
                canReadInstructionFiles = true,
                canReadInstructionFilesMessage = msg,
                instructions = parsedInstructions,
                unparsableInstructions = parseFailures))
      }
    }
  }

  fun refreshInstructions(store: Store<State>) {
    if (ContextCompat.checkSelfPermission(context,
                                          Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      store.dispatch(
          Action.SetInstructionsAndLanguages(
              canReadInstructionFiles = false,
              canReadInstructionFilesMessage = "Permission to write to external storage is required and not granted.",
              instructions = immutableSetOf(),
              unparsableInstructions = immutableSetOf()))
    }
    else {
      getStorageDir(context.packageName)
          .flatMap(ensureTokenFileExists)
          .retry(5)
          .flatMap(readInstructionsFromStorage)
          .flatMap(ensureFilesAreUpToDate)
          .subscribe(
              { action -> store.dispatch(action) },
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
