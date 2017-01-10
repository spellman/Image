package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.processors.FlowableProcessor
import java.io.File
import java.io.IOException
import java.net.URLDecoder

sealed class RequestModel {
  class GetInstructions : RequestModel()
  class PlayInstruction(val instruction: Instruction) : RequestModel()
}

sealed class ResponseModel {
  class Instructions(
    val parsedInstructions: Result<String, ParsedInstructions>
  ) : ResponseModel()

  class InstructionToPlay(val instruction: Instruction) : ResponseModel()
}





data class ViewModel(
  val appVersionInfo: String,
  var instructionFilesReadFailureMessage: String,
  var instructions: ImmutableSet<Instruction>,
  var unparsableInstructions: ImmutableSet<UnparsableInstruction>,
  var languages: ImmutableSet<String>,
  var language: String
)







class Controller(val msgCh: FlowableProcessor<RequestModel>) {
  fun getInstructions() {
    msgCh.onNext(RequestModel.GetInstructions())
  }

  fun playInstruction(instruction: Instruction) {
    msgCh.onNext(RequestModel.PlayInstruction(instruction))
  }
}







class Presenter(val viewModel: ViewModel,
                val updateCh: Observable<ResponseModel>) {
  lateinit var commandChSubscription: Disposable

  fun start() {
    updateCh.subscribe { responseModel ->
      when (responseModel) {
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
  }

  fun stop() {
    commandChSubscription.dispose()
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
  val msgCh: Flowable<RequestModel>,
  val getInstructionsCh: FlowableProcessor<RequestModel.GetInstructions>
) {
  lateinit var commandChSubscription: Disposable

  fun start() {
    msgCh.subscribe { requestModel ->
      when (requestModel) {
        is RequestModel.GetInstructions -> getInstructionsCh.onNext(requestModel)
        else -> {}
      }
    }
  }

  fun stop() {
    commandChSubscription.dispose()
  }
}







class FileSystemGetInstructionsGateway(val storageDir: File) : GetInstructionsGateway {
  override fun getInstructions(): Result<String, ImmutableSet<File>> {
    if (!isExternalStorageReadable()) {
      Result.Err<String, ImmutableSet<File>>("External storage is not readable.")
    }

    return Result.Ok<String, ImmutableSet<File>>(
             storageDir.listFiles().asList().toImmutableSet())
  }
}



interface GetInstructionsGateway {
  fun getInstructions(): Result<String, ImmutableSet<File>>
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
  val inCh: Observable<RequestModel.GetInstructions>,
  val outCh: FlowableProcessor<ResponseModel.Instructions>
) {
  lateinit var inChSubscription: Disposable

  fun start() {
    inChSubscription = inCh.subscribe { msg ->
      outCh.onNext(ResponseModel.Instructions(getInstructions()))
    }
  }

  fun stop() {
    inChSubscription.dispose()
  }

  fun getInstructions(): Result<String, ParsedInstructions> {
    val instructions = getInstructionsGateway.getInstructions()
    return when (instructions) {
      is Result.Err -> Result.Err<String, ParsedInstructions>(instructions.errValue)
      is Result.Ok -> {
        val parses = instructions.okValue.map { file -> fileToInstruction(file) }
        Result.Ok<String, ParsedInstructions>(
          ParsedInstructions(
            parses.filterIsInstance<Result.Ok<UnparsableInstruction, Instruction>>()
              .map { x -> x.okValue }
              .toImmutableSet(),
            parses.filterIsInstance<Result.Err<UnparsableInstruction, Instruction>>()
              .map { x -> x.errValue }
              .toImmutableSet()
          )
        )
      }
    }
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
          absolutePath = file.absolutePath,
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
}







data class InstructionTiming(val cueStartTime: Long,
                             val instructionAudioDuration: Long)

private fun playInstruction(instruction: Instruction) {
//  val mp = MediaPlayer()
//  mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
//  mp.setOnPreparedListener { mp ->
//    outCh.onNext(
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
//    outCh.onNext(
//      Result.Err<Instruction, InstructionTiming>(
//        msg.instruction))
//    true
//  }
//  mp.setOnCompletionListener { mp ->
//    outCh.onNext("instruction-complete")
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
