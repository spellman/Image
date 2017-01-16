package com.cws.image

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import java.io.File
import java.io.IOException
import java.net.URLDecoder

sealed class RequestModel {
  class GetInstructions() : RequestModel() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class PlayInstruction(val instruction: Instruction) : RequestModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class EnsureInstructionsDirExistsAndIsAccessibleFromPC : RequestModel() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }
}

sealed class ResponseModel {
  class InstructionsDirResponse(
    val instructionsDir: Result<String, File>
  ): ResponseModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instructionsDir: ${instructionsDir}""".trimMargin()
    }
  }

  class Instructions(
    val parsedInstructions: Result<String, ParsedInstructions>
  ) : ResponseModel() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |parsedInstructions: ${parsedInstructions}""".trimMargin()
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
  var instructionFilesReadFailureMessage: String?,
  var instructions: ImmutableSet<Instruction>,
  var unparsableInstructions: ImmutableSet<UnparsableInstruction>,
  var languages: ImmutableSet<String>,
  var language: String
)







class Controller(val msgChan: FlowableProcessor<RequestModel>) {
  fun start(): Controller {
    return this
  }

  fun stop(): Controller {
    return this
  }

  fun getInstructions() {
    msgChan.onNext(RequestModel.GetInstructions())
  }

  fun playInstruction(instruction: Instruction) {
    msgChan.onNext(RequestModel.PlayInstruction(instruction))
  }

  fun ensureInstructionsDirExistsAndIsAccessibleFromPC() {
    msgChan.onNext(RequestModel.EnsureInstructionsDirExistsAndIsAccessibleFromPC())
  }
}







class Presenter(val viewModel: ViewModel,
                val controller: Controller,
                val updateCh: Flowable<ResponseModel>) {
  lateinit var updateChanSubscription: Disposable

  fun start(): Presenter {
    updateChanSubscription = updateCh.subscribe { responseModel ->
      when (responseModel) {
        is ResponseModel.InstructionsDirResponse -> {
          val instructionsDir = responseModel.instructionsDir
          when (instructionsDir) {
            is Result.Err -> {
              viewModel.instructionFilesReadFailureMessage = instructionsDir.errValue
            }
            is Result.Ok -> controller.getInstructions()
          }
        }

        is ResponseModel.Instructions -> {
          val parsedInstructions = responseModel.parsedInstructions
          when (parsedInstructions) {
            is Result.Err -> {
              viewModel.instructionFilesReadFailureMessage = parsedInstructions.errValue
            }
            is Result.Ok -> {
              val instructions: ImmutableSet<Instruction> = parsedInstructions.okValue.instructions
              viewModel.instructions = instructions
              viewModel.unparsableInstructions = parsedInstructions.okValue.unparsableInstructions
              viewModel.languages = instructions.map { i -> i.language }.toImmutableSet()
            }
          }
        }

        is ResponseModel.InstructionToPlay -> {
          playInstruction(responseModel.instruction)
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

//  private fun playInstruction(instruction: Instruction) {
//    val mp = MediaPlayer()
//    mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
//    mp.setOnPreparedListener { mp ->
//      updateCh.onNext(
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
//      updateCh.onNext(
//        Result.Err<Instruction, InstructionTiming>(
//          instruction))
//      true
//    }
//    mp.setOnCompletionListener { mp ->
//      updateCh.onNext("instruction-complete")
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
  val ensureInstructionsDirExistsAndIsAccessibleFromPC: EnsureInstructionsDirExistsAndIsAccessibleFromPC,
  val getInstructions: GetInstructions,
  val msgChan: Flowable<RequestModel>,
  val updateChan: FlowableProcessor<ResponseModel>
) {
  lateinit var msgChanSubscription: Disposable
  lateinit var responseChanSubscription: Disposable

  fun start(): Update {
    msgChanSubscription = msgChan.subscribe { requestModel ->
      Log.d("Update", "Incoming request model: ${requestModel}")

      when (requestModel) {
        is RequestModel.EnsureInstructionsDirExistsAndIsAccessibleFromPC ->
          ensureInstructionsDirExistsAndIsAccessibleFromPC.inChan.onNext(requestModel)

        is RequestModel.GetInstructions ->
          getInstructions.inChan.onNext(requestModel)
        else -> {}
      }
    }

    responseChanSubscription =
      Flowable.merge(
        ensureInstructionsDirExistsAndIsAccessibleFromPC.outChan,
        getInstructions.outChan
      ).subscribe { responseModel ->
        Log.d("Update", "Outgoing response model: ${responseModel}")
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
  val context: Context,
  val inChan: FlowableProcessor<RequestModel.EnsureInstructionsDirExistsAndIsAccessibleFromPC>,
  val outChan: FlowableProcessor<ResponseModel.InstructionsDirResponse>
) {
  lateinit var inChanSubscription: Disposable

  fun start(): EnsureInstructionsDirExistsAndIsAccessibleFromPC {
    inChanSubscription = inChan.subscribe { msg ->
      ensureInstructionsDirIsAccessibleFromPC(
        ensureInstructionsDirExists()).subscribe { res ->
        outChan.onNext(ResponseModel.InstructionsDirResponse(res))
      }
    }

    return this
  }

  fun stop(): EnsureInstructionsDirExistsAndIsAccessibleFromPC {
    inChanSubscription.dispose()

    return this
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

  fun ensureInstructionsDirIsAccessibleFromPC(instructionsDir: Result<String, File>): Flowable<Result<String, File>> {
    return when (instructionsDir) {
      is Result.Err -> Flowable.just(instructionsDir)
      is Result.Ok -> {
        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
          if (!isExternalStorageWritable()) {
            Flowable.just<Result<String, File>>(
              Result.Err(
                "There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable."))
          }
          else {
            if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
              Flowable.just<Result<String, File>>(
                Result.Err(
                  "Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable."))
            }
            else {
              Log.d("instructions dir", "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
              Flowable.create<Result<String, File>>(
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
                },
                BackpressureStrategy.BUFFER)
            }
          }
        }
        else {
          Flowable.just<Result<String, File>>(Result.Ok(storageDir))
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

data class ParsedInstructions(
  val instructions: ImmutableSet<Instruction>,
  val unparsableInstructions: ImmutableSet<UnparsableInstruction>
)

class GetInstructions(
  val getInstructionsGateway: GetInstructionsGateway,
  val inChan: FlowableProcessor<RequestModel.GetInstructions>,
  val outChan: FlowableProcessor<ResponseModel.Instructions>
) {
  lateinit var inChanSubscription: Disposable

  fun start(): GetInstructions {
    inChanSubscription = inChan.subscribe { msg ->
      outChan.onNext(ResponseModel.Instructions(getInstructions()))
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
