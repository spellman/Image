package com.cws.image

import android.util.Log
import com.github.andrewoma.dexx.kollection.ImmutableSet
import com.github.andrewoma.dexx.kollection.toImmutableSet
import io.reactivex.Single
import java.io.File
import java.net.URLDecoder

interface InstructionsGateway {
  fun getInstructionFiles(): Single<ImmutableSet<File>>
}

data class ParsedInstructions(
  val instructions: ImmutableSet<Instruction>,
  val unparsableInstructions: ImmutableSet<UnparsableInstruction>
)

class GetInstructions(
  val instructionsGateway: InstructionsGateway
) {
  fun getInstructions(): Single<ParsedInstructions> {
    Log.d(this.javaClass.simpleName, "About to get instructions")
    return instructionsGateway.getInstructionFiles()
      .map { instructionFiles ->
        Log.d(this.javaClass.simpleName, "Loaded instruction files; about to parse instructions")
        val parseResults = instructionFiles.map { file ->
          fileToInstruction(file)
        }

        Log.d(this.javaClass.simpleName, "Parsed instructions")
        ParsedInstructions(
          unparsableInstructions = parseResults.filterIsInstance<Result.Err<UnparsableInstruction, Instruction>>()
            .map { x -> x.errValue }
            .toImmutableSet(),
          instructions = parseResults.filterIsInstance<Result.Ok<UnparsableInstruction, Instruction>>()
            .map { x -> x.okValue }
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
}
