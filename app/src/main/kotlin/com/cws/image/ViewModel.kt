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
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

sealed class ViewModelMessage {
  class CouldNotReadInstructions(val message: String) : ViewModelMessage() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |message: ${message}""".trimMargin()
    }
  }

  class InstructionsChanged(
    val unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>,
    val languages: ImmutableList<String>
  ) : ViewModelMessage() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |unparsableInstructions: ${unparsableInstructions}
               |languages: ${languages}""".trimMargin()
    }
  }

  class LanguageChanged(
    val instructionsForCurrentLanguage: ImmutableList<Instruction>
  ) : ViewModelMessage() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instructionsForCurrentLanguage: ${instructionsForCurrentLanguage}""".trimMargin()
    }
  }

  class CouldNotPlayInstruction(
    val instruction: Instruction
  ) : ViewModelMessage() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class PreparedToPlayInstructionAudio : ViewModelMessage()

  class InstructionAudioCompleted : ViewModelMessage()
}

data class UnparsableInstructionViewModel(
  val fileName: String,
  val failureMessage: String
)

//data class InstructionTiming(val cueStartTime: Long,
//                             val instructionAudioDuration: Long)



data class ViewModel(
  val app: App,
  val msgChan: PublishSubject<ViewModelMessage>,
  var needToRefreshInstructions: Boolean,
  var instructionFilesReadFailureMessage: String?,
  var instructions: ImmutableSet<Instruction>,
  var instructionsForCurrentLanguage: ImmutableSet<Instruction>,
  var unparsableInstructions: ImmutableSet<UnparsableInstructionViewModel>,
  var languages: ImmutableSet<String>,
  var language: String?,
  var mediaPlayer: MediaPlayer?,
  var selectedInstruction: Instruction?
) {
  val appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}"

  fun getInstructions() {
    if (needToRefreshInstructions) {
      Log.d(this.javaClass.simpleName, "getInstructions")
      Log.d("REQUEST MODEL", "(none)")
      Log.d("view model", this.toString())
      i_getInstructions(app.ensureInstructionsDir, app.getInstructionsGateway)
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { parsedInstructions ->
            Log.d(this.javaClass.simpleName, "getInstructions")
            Log.d("RESPONSE MODEL", parsedInstructions.toString())
            Log.d("view model pre", this.toString())

            instructions = parsedInstructions.instructions

            unparsableInstructions =
              parsedInstructions.unparsableInstructions
                .map { u: UnparsableInstruction ->
                  UnparsableInstructionViewModel(
                    u.fileName,
                    instructionParsingFailureToMessage(u.failure)
                  )
                }.toImmutableSet()

            languages = instructions.map { i -> i.language }.toImmutableSet()

            msgChan.onNext(
              ViewModelMessage.InstructionsChanged(
                unparsableInstructions = unparsableInstructions.sortedBy { u -> u.fileName }
                  .toImmutableList(),
                languages = sortLanguages(languages),
                defaultLanguage = defaultLanguage()
              )
            )
            Log.d("view model post", this.toString())
          },
          { throwable ->
            // TODO
            throw throwable
          })

      needToRefreshInstructions = false
    }
  }

  fun setCurrentLanguage(newLanguage: String) {
    Log.d(this.javaClass.simpleName, "setLanguage")
    Log.d("REQUEST MODEL", newLanguage)
    Log.d("view model pre", this.toString())
    language = newLanguage
    instructionsForCurrentLanguage =
      instructions.filter { i -> i.language == newLanguage }.toImmutableSet()

    Log.d("view model post", this.toString())
    msgChan.onNext(
      ViewModelMessage.LanguageChanged(
        instructionsForCurrentLanguage = instructionsForCurrentLanguage.sortedBy {
          i -> i.subject
        }
          .toImmutableList()
      )
    )
  }

  fun prepareToPlayInstruction(instruction: Instruction) {
    Log.d(this.javaClass.simpleName, "prepareToPlayInstruction")
    Log.d("REQUEST MODEL", instruction.toString())
    Log.d("view model pre", this.toString())

    val mp = MediaPlayer()
    mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
    mp.setOnPreparedListener { mp ->
      mediaPlayer = mp
      selectedInstruction = instruction
      Log.d("view model prepared", this.toString())
      msgChan.onNext(ViewModelMessage.PreparedToPlayInstructionAudio())
    }
    mp.setOnErrorListener { mp, what, extra ->
      // 2016-11-23 Cort Spellman
      // TODO: This is too coarse - recover from errors as appropriate
      //       and tailor the message/log to the error:
      // See the possible values of what and extra at
      // https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
      msgChan.onNext(ViewModelMessage.CouldNotPlayInstruction(instruction))
      true
    }
    mp.setOnCompletionListener { mp ->
      Log.d("view model post", this.toString())
      msgChan.onNext(ViewModelMessage.InstructionAudioCompleted())
    }

    try {
      mp.setDataSource(instruction.absolutePath)
      mediaPlayer = mp
      mp.prepareAsync()
    }
    catch (e: IOException) {
      Log.e(this.javaClass.simpleName, e.cause.toString())
      msgChan.onNext(ViewModelMessage.CouldNotPlayInstruction(instruction))
    }
    catch (e: IllegalArgumentException) {
      Log.e(this.javaClass.simpleName, e.cause.toString())
      msgChan.onNext(ViewModelMessage.CouldNotPlayInstruction(instruction))
    }
  }

  fun sortLanguages(ls : Iterable<String>) : ImmutableList<String> {
    return ls.sortedWith(
      compareBy { l: String -> l != defaultLanguage() }
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

  fun handlePlayInstructionFailure(subject: String, language: String) {
    // 2017-01-30 Cort Spellman
    // TODO: Make Instruction implement parcelable or equivalent so you can
    // pass the instruction via the result intent.
    msgChan.onNext(
      ViewModelMessage.CouldNotPlayInstruction(
        Instruction(subject = subject,
                    language = language,
                    absolutePath = "",
                    cueStartTime = 0L)))
  }

  fun clearMediaPlayer() {
    mediaPlayer?.release()
    mediaPlayer = null
  }
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







// INTERACTORS
fun i_getInstructions(
  ensureInstructionsDir: EnsureInstructionsDirExistsAndIsAccessibleFromPC,
  getInstructionsGateway: GetInstructionsGateway
): Single<ParsedInstructions> {
  return ensureInstructionsDir.ensureInstructionsDirExistsAndIsAccessibleFromPC()
           .map { instructionsDir ->
              val parseResults = getInstructionsGateway.getInstructionFiles()
                                   .map { file -> fileToInstruction(file) }

              ParsedInstructions(
                parseResults.filterIsInstance<Result.Ok<UnparsableInstruction,        Instruction> >()
                  .map { x -> x.okValue }
                  .toImmutableSet(),
                parseResults.filterIsInstance<Result.Err<UnparsableInstruction,        Instruction> >()
                 .map { x -> x.errValue }
                 .toImmutableSet()
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







// GATEWAYS
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










