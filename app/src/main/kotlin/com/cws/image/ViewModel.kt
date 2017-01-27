package com.cws.image

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import io.reactivex.subjects.Subject
import java.io.File
import java.io.IOException
import java.net.URLDecoder

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
    val parsedInstructions: Result<String, ParsedInstructions>
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





data class ViewModel(
  val appVersionInfo: String,

  // 2017-01-23 Cort Spellman
  // instructionFilesReadFailureMessage is displayed in a snackbar so it's not
  // actually part of the view model. Rather, it's part of the application
  // state. Ideas?
  var instructionFilesReadFailureMessage: String?,

  // 2017-01-23 Cort Spellman
  // instructions is really not part of the view model.
  // Rather, it's part of the application state. Ideas?
  var instructions: ImmutableSet<Instruction>,

  val instructionsForCurrentLanguage: MutableList<Instruction>,
  val unparsableInstructions: MutableList<UnparsableInstructionViewModel>,
  val languages: MutableList<String>,
  var language: String?
)







class Controller(val msgChan: Subject<RequestModel>) {
  fun start(): Controller {
    return this
  }

  fun stop(): Controller {
    return this
  }

  fun getInstructions() {
    msgChan.onNext(RequestModel.GetInstructions())
  }

  fun setLanguage(language: String) {
    msgChan.onNext(RequestModel.SetLanguage(language))
  }

  fun playInstruction(instruction: Instruction) {
    msgChan.onNext(RequestModel.PlayInstruction(instruction))
  }
}







class Presenter(val context: Context,
                val viewModel: ViewModel,
                val updateChan: Observable<ResponseModel>,
                val msgChan: Subject<PresenterMessage>) {
  lateinit var updateChanSubscription: Disposable

  fun start(): Presenter {
    updateChanSubscription = updateChan.subscribe { responseModel ->
      Log.d("Response model", responseModel.toString())
      when (responseModel) {
        is ResponseModel.Instructions -> {
          val parsedInstructions = responseModel.parsedInstructions
          when (parsedInstructions) {
            is Result.Err -> {
              viewModel.instructionFilesReadFailureMessage = parsedInstructions.errValue
              msgChan.onNext(
                PresenterMessage.SnackbarMessage.CouldNotReadInstructions(
                  parsedInstructions.errValue))
            }
            is Result.Ok -> {
              val instructions = parsedInstructions.okValue.instructions
              viewModel.instructions = instructions

              viewModel.unparsableInstructions.clear()
              viewModel.unparsableInstructions.addAll(
                parsedInstructions.okValue.unparsableInstructions
                  .map { u: UnparsableInstruction ->
                    UnparsableInstructionViewModel(
                      u.fileName,
                      instructionParsingFailureToMessage(u.failure)
                    )
                  })

              viewModel.languages.clear()
              viewModel.languages.addAll(instructions.map { i -> i.language }
                                           .toImmutableSet())

              msgChan.onNext(PresenterMessage.InstructionsChanged())
            }
          }
        }

        is ResponseModel.Language -> {
          val language = responseModel.language
          viewModel.language = language

          viewModel.instructionsForCurrentLanguage.clear()
          viewModel.instructionsForCurrentLanguage.addAll(
            viewModel.instructions.filter { i -> i.language == language })

          msgChan.onNext(PresenterMessage.LanguageChanged())
        }

        is ResponseModel.InstructionToPlay -> {
          Intent()
        }

        else -> {}
      }
    }

    return this
  }

  fun stop(): Presenter {
    updateChanSubscription.dispose()

    return this
  }

  fun instructionParsingFailureToMessage(f: InstructionParsingFailure) : String {
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

//  private fun playInstruction(instruction: Instruction) {
//    val mp = MediaPlayer()
//    mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
//    mp.setOnPreparedListener { mp ->
//      updateChan.onNext(
//        Result.Ok<Instruction, InstructionTiming>(
//          InstructionTiming(instruction.cueStartTime,
//                            mp.duration.toLong())))
//    }
//    mp.setOnErrorListener { mp, what, extra ->
//      // 2016-11-23 Cort Spellman
//      // TODO: This is too coarse - recover from errors as appropriate
//      //       and tailor the message/log to the error:
//      // See the possible values of what and extra at
//      // https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
//      updateChan.onNext(
//        Result.Err<Instruction, InstructionTiming>(
//          instruction))
//      true
//    }
//    mp.setOnCompletionListener { mp ->
//      updateChan.onNext("instruction-complete")
//    }
//
//    try {
//      mp.setDataSource(instruction.absolutePath)
//      mediaPlayer = mp
//      mp.prepareAsync()
//    }
//    catch (e: IOException) {
//      store.dispatch(
//        Action.CouldNotPlayInstruction(instruction))
//    }
//    catch (e: IllegalArgumentException) {
//      store.dispatch(
//        Action.CouldNotPlayInstruction(instruction))
//    }
//  }
}







class Update(
  val getInstructions: GetInstructions,
  val setLanguage: SetLanguage,
  val msgChan: Observable<RequestModel>,
  val updateChan: Subject<ResponseModel>
) {
  lateinit var msgChanSubscription: Disposable
  lateinit var responseChanSubscription: Disposable

  fun start(): Update {
    msgChanSubscription = msgChan.subscribe { requestModel ->
      Log.d("Request model", requestModel.toString())

      when (requestModel) {
        is RequestModel.GetInstructions ->
          getInstructions.inChan.onNext(requestModel)

        is RequestModel.SetLanguage ->
          setLanguage.inChan.onNext(requestModel)

        is RequestModel.PlayInstruction ->
          updateChan.onNext(ResponseModel.InstructionToPlay(requestModel.instruction))

        else -> {}
      }
    }

    responseChanSubscription =
      Observable.merge(
        getInstructions.outChan,
        setLanguage.outChan
      ).subscribe { responseModel ->
        updateChan.onNext(responseModel)
      }

    return this
  }

  fun stop(): Update {
    msgChanSubscription.dispose()
    responseChanSubscription.dispose()

    return this
  }
}







class EnsureInstructionsDirExistsAndIsAccessibleFromPC(
  val storageDir: File,
  val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb: File,
  val context: Context
) {

  fun ensureInstructionsDirExistsAndIsAccessibleFromPC(): Observable<Result<String, File>> {
    return ensureInstructionsDirIsAccessibleFromPC(ensureInstructionsDirExists())
  }

  fun ensureInstructionsDirExists(): Result<String, File> {
    return if (!isExternalStorageReadable()) {
             Result.Err("External storage is not readable.")
           }
           else {
             if (!storageDir.isDirectory) {
               if (!isExternalStorageWritable()) {
                 Result.Err("There is no directory at instructions-directory absolutePath, ${storageDir.absolutePath} and it can't be created because external storage is not wri       table.")
               }
               else {
                 if (!storageDir.mkdirs()) {
                   Result.Err("Could not create directory ${storageDir.absolutePath}, even though external storage is writable.")
                 }
                 else {
                   Result.Ok(storageDir)
                 }
               }
             }
             else {
               Result.Ok(storageDir)
             }
    }
  }

  fun ensureInstructionsDirIsAccessibleFromPC(instructionsDir: Result<String, File>): Observable<Result<String, File>> {
    return when (instructionsDir) {
      is Result.Err -> Observable.just(instructionsDir)
      is Result.Ok -> {
        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
          if (!isExternalStorageWritable()) {
            Observable.just<Result<String, File>>(
              Result.Err(
                "There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable."))
          }
          else {
            if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
              Observable.just<Result<String, File>>(
                Result.Err(
                  "Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable."))
            }
            else {
              Log.d("instructions dir", "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
              Observable.create<Result<String, File>>(
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
                        emitter.onNext(Result.Ok(storageDir))
                      }
                      else {
                        emitter.onNext(
                          Result.Err(
                            "A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed."))
                      }
                    })
                })
            }
          }
        }
        else {
          Observable.just<Result<String, File>>(Result.Ok(storageDir))
        }
      }
    }
  }
}







class FileSystemGetInstructionsGateway(
  val storageDir: File,
  val filesToSkip: ImmutableSet<File>
) : GetInstructionsGateway {
  override fun getInstructionFiles(): Result<String, ImmutableSet<File>> {
    if (!isExternalStorageReadable()) {
      Result.Err<String, ImmutableSet<File>>("External storage is not readable.")
    }

    return Result.Ok<String, ImmutableSet<File>>(
             storageDir.listFiles()
               .filter { file -> !filesToSkip.contains(file) }
               .toImmutableSet())
  }
}



interface GetInstructionsGateway {
  fun getInstructionFiles(): Result<String, ImmutableSet<File>>
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

data class UnparsableInstructionViewModel(
  val fileName: String,
  val failureMessage: String
)

data class ParsedInstructions(
  val instructions: ImmutableSet<Instruction>,
  val unparsableInstructions: ImmutableSet<UnparsableInstruction>
)

class GetInstructions(
  val ensureInstructionsDir: EnsureInstructionsDirExistsAndIsAccessibleFromPC,
  val getInstructionsGateway: GetInstructionsGateway,
  val inChan: Subject<RequestModel.GetInstructions>,
  val outChan: Subject<ResponseModel.Instructions>
) {
  lateinit var inChanSubscription: Disposable

  fun start(): GetInstructions {
    inChanSubscription = inChan.subscribe { msg ->
      ensureInstructionsDir.ensureInstructionsDirExistsAndIsAccessibleFromPC()
        .subscribe { instructionsDirResult ->
          when (instructionsDirResult) {
            is Result.Err ->
              outChan.onNext(
                ResponseModel.Instructions(
                  Result.Err<String, ParsedInstructions>(instructionsDirResult.errValue)))

            is Result.Ok ->
              outChan.onNext(ResponseModel.Instructions(getInstructions()))
          }
        }
    }

    return this
  }

  fun stop(): GetInstructions {
    inChanSubscription.dispose()

    return this
  }

  fun getInstructions(): Result<String, ParsedInstructions> {
    val instructionFiles = getInstructionsGateway.getInstructionFiles()
    return when (instructionFiles) {
      is Result.Err -> Result.Err<String, ParsedInstructions>(instructionFiles.errValue)

      is Result.Ok -> {
        val parseResults = instructionFiles.okValue
          .map { file -> fileToInstruction(file) }

        Result.Ok<String, ParsedInstructions>(
          ParsedInstructions(
            parseResults.filterIsInstance<Result.Ok<UnparsableInstruction, Instruction>>()
              .map { x -> x.okValue }
              .toImmutableSet(),
            parseResults.filterIsInstance<Result.Err<UnparsableInstruction, Instruction>>()
              .map { x -> x.errValue }
              .toImmutableSet()
          )
        )
      }
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
}







class SetLanguage(
  val inChan: Subject<RequestModel.SetLanguage>,
  val outChan: Subject<ResponseModel.Language>
) {
  lateinit var inChanSubscription: Disposable

  fun start(): SetLanguage {
    inChanSubscription = inChan.subscribe { msg ->
      outChan.onNext(ResponseModel.Language(msg.language))
    }

    return this
  }

  fun stop(): SetLanguage {
    inChanSubscription.dispose()

    return this
  }
}







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
