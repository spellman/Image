package com.cws.image

import android.os.Environment
import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.SynchronousQueue

data class Instruction(val subject: String,
                       val language: String,
                       val absolutePath: String,
                       val cueStartTime: Long)

sealed class InstructionParsingFailure {
  class FileNameFormatFailure() : InstructionParsingFailure()
  class CueTimeFailure() : InstructionParsingFailure()
}

data class UnparsableInstruction(val fileName: String,
                                 val failure: InstructionParsingFailure)

data class State(
  val appVersionInfo: String
  )

data class ViewModel(
  val state: State
  ) {

  fun getInstructions() {

  }
}

class Dispatcher(val inCh: SynchronousQueue<Any>,
                 val outCh: SynchronousQueue<Any>,
                 val getInstructions: GetInstructions) {
  fun start() {
    while (true) {
      val action = inCh.take()
      when (action) {
        is Action.RefreshInstructions -> outCh.put(getInstructions.go())
        else -> {
        }
      }
    }
  }
}

class FileSystemInstructionsGateway(val storageDir: File) : InstructionsGateway {
  override fun getInstructions(): ImmutableSet<File> {
    if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")
    }
    else {
      return storageDir.listFiles().asList().toImmutableSet()
    }
  }
}

interface InstructionsGateway {
  fun getInstructions(): ImmutableSet<File>
}

class GetInstructions(val instructionsGateway: InstructionsGateway) {
  fun go (): ImmutableSet<Result<UnparsableInstruction, Instruction>> {
    return instructionsGateway.getInstructions()
             .map { file -> fileToInstruction(file) }
             .toImmutableSet()
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
