package com.cws.image

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.Subject
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.*
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

sealed class RequestModel {
  class GetInstructions : RequestModel() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class PlayInstruction(val instruction: Instruction) : RequestModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class SetLanguage(val language: String) : RequestModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |language: ${language}""".trimMargin()
    }
  }
}

sealed class ResponseModel {
  class Instructions(
    val parsedInstructions: ParsedInstructions
  ) : ResponseModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |parsedInstructions: ${parsedInstructions}""".trimMargin()
    }
  }

  class Language(val language: String) : ResponseModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |language: ${language}""".trimMargin()
    }
  }

  class InstructionToPlay(val instruction: Instruction) : ResponseModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }
}




data class UnparsableInstructionViewModel(
  val fileName: String,
  val failureMessage: String
)



data class ViewModel(
  val app: App,
  val activity: MainActivity,
  var needToRefreshInstructions: Boolean = true,
  var instructionFilesReadFailureMessage: String? = null,
  var instructions: ImmutableSet<Instruction> = immutableSetOf(),
  var instructionsForCurrentLanguage: ImmutableSet<Instruction> = immutableSetOf(),
  var unparsableInstructions: ImmutableSet<UnparsableInstructionViewModel> = immutableSetOf(),
  var languages: ImmutableSet<String> = immutableSetOf(),
  var language: String? = null
) {
  val appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}"

  fun getInstructions() {
    Log.d(this.javaClass.simpleName, "getInstructions")
    Log.d("REQUEST MODEL", "(none)")
    Log.d("view model", this.toString())
    i_getInstructions(app.ensureInstructionsDir, app.getInstructionsGateway)
      .subscribe(
        { responseModel ->
          Log.d(this.javaClass.simpleName, "getInstructions")
          Log.d("RESPONSE MODEL", responseModel.toString())
          Log.d("view model pre", this.toString())
          val parsedInstructions = responseModel.parsedInstructions
          instructions = parsedInstructions.instructions

          unparsableInstructions =
            parsedInstructions.unparsableInstructions
              .map { u: UnparsableInstruction ->
                UnparsableInstructionViewModel(
                  u.fileName,
                  instructionParsingFailureToMessage(u.failure)
                )
              }.toImmutableSet()

          activity.refreshUnparsableInstructions(
            unparsableInstructions.sortedBy { u -> u.fileName }.toImmutableList()
          )

          languages = instructions.map { i -> i.language }.toImmutableSet()

          activity.refreshLanguageTabs(sortLanguages(languages),
                                       defaultLanguage())
          Log.d("view model post", this.toString())
        },
        { throwable ->
          // TODO
          throw throwable
        })
  }

  fun setCurrentLanguage(languageSelections: Observable<String>) {
    languageSelections.subscribe { newLanguage ->
      instructionsForCurrentLanguage =
        instructions.filter { i -> i.language == newLanguage }.toImmutableSet()
      activity.refreshInstructionsForCurrentLanguage(
        instructionsForCurrentLanguage.sortedBy { i -> i.subject }.toImmutableList()
      )
    }
  }

  fun sortLanguages(ls : Iterable<String>) : ImmutableList<String> {
    return ls.sortedWith(
      compareBy { l: String -> l == defaultLanguage() }
        .thenBy { l: String -> l })
      .toImmutableList()
  }

  fun defaultLanguage(): String {
    return "english"
  }

  fun instructionParsingFailureToMessage(f: InstructionParsingFailure) : String {
    val context = app.applicationContext
    val r = context.resources
    val name = when (f) {
      is InstructionParsingFailure.FileNameFormatFailure ->
        "instruction_file_name_format_failure_explanation"

      is InstructionParsingFailure.CueTimeFailure ->
        "instruction_cue_time_failure_explanation"
    }

    return r.getString(
      r.getIdentifier(name, "string", context.packageName))
  }
}






fun i_getInstructions(
  ensureInstructionsDir: EnsureInstructionsDirExistsAndIsAccessibleFromPC,
  getInstructionsGateway: GetInstructionsGateway
): Single<ResponseModel.Instructions> {
  return ensureInstructionsDir.ensureInstructionsDirExistsAndIsAccessibleFromPC()
           .map { instructionsDir ->
             val parseResults = getInstructionsGateway.getInstructionFiles()
                                  .map { file -> fileToInstruction(file) }

             ResponseModel.Instructions(
               ParsedInstructions(
                 parseResults.filterIsInstance<Result.Ok<UnparsableInstruction,        Instruction>>()
                   .map { x -> x.okValue }
                   .toImmutableSet(),
                 parseResults.filterIsInstance<Result.Err<UnparsableInstruction,        Instruction>>()
                   .map { x -> x.errValue }
                   .toImmutableSet()
               )
             )
           }
}

fun fileToInstruction(file: File): Result<UnparsableInstruction, Instruction> {
  Log.d("parse instruction", "Instruction file to parse: ${file.absolutePath}")
  return try {
    val (subject, language, cueTime) =
      file.name.substringBeforeLast(".")
        .split('_')
        .map { s -> URLDecoder.decode(s, "UTF-8") }

    Log.d("parse instruction", "subject: ${subject}    language: ${language}    cueStartTime: ${cueTime}")

    Result.Ok(
      Instruction(
        subject = subject,
        language = language,
        absolutePath = file.absolutePath,
        cueStartTime = cueTime.toLong()))
  }
  catch (e: IndexOutOfBoundsException) {
    Log.e("parse instruction", "parse failure: ${e}")
    Result.Err(
      UnparsableInstruction(file.name,
                            InstructionParsingFailure.FileNameFormatFailure()))
  }
  catch (e: NumberFormatException) {
    e.printStackTrace()
    Log.e("parse instruction", "parse failure: ${e}")
    Result.Err(
      UnparsableInstruction(file.name,
                            InstructionParsingFailure.CueTimeFailure()))
  }
}






class EnsureInstructionsDirExistsAndIsAccessibleFromPC(
  val storageDir: File,
  val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb: File,
  val context: Context
) {

  fun ensureInstructionsDirExistsAndIsAccessibleFromPC(): Single<File> {
    ensureInstructionsDirExists()
    return ensureInstructionsDirIsAccessibleFromPC()
  }

  fun ensureInstructionsDirExists() {
    if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")
    }
    else {
      if (!storageDir.isDirectory) {
        if (!isExternalStorageWritable()) {
          throw IOException("There is no directory at instructions-directory absolutePath, ${storageDir.absolutePath} and it can't be created because external storage is not wri       table.")
        }
        else {
          if (!storageDir.mkdirs()) {
            throw IOException("Could not create directory ${storageDir.absolutePath}, even though external storage is writable.")
          }
        }
      }
    }
  }

  fun ensureInstructionsDirIsAccessibleFromPC(): Single<File> {
    return if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
      if (!isExternalStorageWritable()) {
        throw IOException("There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")
      }
      else {
        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
          throw IOException("Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.")
        }
        else {
          Log.d("instructions dir", "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
          Single.create<File>(
            { emitter ->
              MediaScannerConnection.scanFile(
                context,
                arrayOf(
                  tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.toString()),
                null,
                { path, uri ->
                  if (uri is Uri) {
                    Log.d("instructions dir", "Scanned ${path}:")
                    Log.d("instructions dir", "-> uri=${uri}")
                    emitter.onSuccess(storageDir)
                  }
                  else {
                    emitter.onError(
                      IOException("A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed."))
                  }
                })
            })
        }
      }
    }
    else {
      Single.just(storageDir)
    }
  }
}







class FileSystemGetInstructionsGateway(
  val storageDir: File,
  val filesToSkip: ImmutableSet<File>
) : GetInstructionsGateway {
  override fun getInstructionFiles(): ImmutableSet<File> {
    if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")
    }

    return storageDir.listFiles()
             .filter { file -> !filesToSkip.contains(file) }
             .toImmutableSet()
  }
}



interface GetInstructionsGateway {
  fun getInstructionFiles(): ImmutableSet<File>
}



data class Instruction(
  val subject: String,
  val language: String,
  val absolutePath: String,
  val cueStartTime: Long
)

sealed class InstructionParsingFailure {
  class FileNameFormatFailure : InstructionParsingFailure()
  class CueTimeFailure : InstructionParsingFailure()
}

data class UnparsableInstruction(
  val fileName: String,
  val failure: InstructionParsingFailure
)

data class ParsedInstructions(
  val instructions: ImmutableSet<Instruction>,
  val unparsableInstructions: ImmutableSet<UnparsableInstruction>
)







data class InstructionTiming(val cueStartTime: Long,
                             val instructionAudioDuration: Long)

private fun playInstruction(instruction: Instruction) {
//  val mp = MediaPlayer()
//  mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
//  mp.setOnPreparedListener { mp ->
//    outChan.onNext(
//      Result.Ok<Instruction, InstructionTiming>(
//        InstructionTiming(msg.instruction.cueStartTime,
//                          mp.duration.toLong())))
//  }
//  mp.setOnErrorListener { mp, what, extra ->
//    // 2016-11-23 Cort Spellman
//    // TODO: This is too coarse - recover from errors as appropriate
//    //       and tailor the message/log to the error:
//    // See the possible values of what and extra at
//    // https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
//    outChan.onNext(
//      Result.Err<Instruction, InstructionTiming>(
//        msg.instruction))
//    true
//  }
//  mp.setOnCompletionListener { mp ->
//    outChan.onNext("instruction-complete")
//  }
//
//  try {
//    mp.setDataSource(msg.instruction.absolutePath)
//    mediaPlayer = mp
//    mp.prepareAsync()
//  }
//  catch (e: IOException) {
//    store.dispatch(
//      Action.CouldNotPlayInstruction(action.instruction))
//  }
//  catch (e: IllegalArgumentException) {
//    store.dispatch(
//      Action.CouldNotPlayInstruction(action.instruction))
//  }
}
