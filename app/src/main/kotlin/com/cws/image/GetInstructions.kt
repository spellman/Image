package com.cws.image

import android.util.Log
import com.github.andrewoma.dexx.kollection.ImmutableSet
import com.github.andrewoma.dexx.kollection.toImmutableSet
import io.reactivex.Single
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.URLDecoder

interface InstructionsGateway {
  fun getInstructionFiles(): Single<ImmutableSet<File>>
}

data class ParsedResourcess(
  val instructions: ImmutableSet<Instruction>,
  val icons: ImmutableSet<Icon>,
  val unparsableFiles: ImmutableSet<UnparsableFile>
)

class GetInstructions(
  val instructionsGateway: InstructionsGateway,
  val audioFileFormats: ImmutableSet<String>,
  val iconFileFormats: ImmutableSet<String>
) {
  companion object Factory {
    private var instance: GetInstructions? = null
    fun getInstance(
      instructionsGateway: InstructionsGateway,
      audioFileFormats: ImmutableSet<String>,
      iconFileFormats: ImmutableSet<String>
      ): GetInstructions {
      val instanceSnapshot = instance

      if (instanceSnapshot == null) {
        val newInstanceSnapshot = GetInstructions(instructionsGateway,
                                                  audioFileFormats,
                                                  iconFileFormats)
        instance = newInstanceSnapshot
        return newInstanceSnapshot
      }
      return instanceSnapshot
    }
  }
  var parsedResourcess: ParsedResourcess? = null

  fun getInstructions(): Single<ParsedResourcess> {
    Log.d(this.javaClass.simpleName, "About to get instructions")
    if (parsedResourcess != null) {
      Log.d(this.javaClass.simpleName, "Returning saved parsed-instructions.")
      return Single.just(parsedResourcess)
    }
    else {
      Log.d(this.javaClass.simpleName, "No parsed-instructions saved. Loading and parsing instructions now.")
      return instructionsGateway.getInstructionFiles()
        .map { instructionFiles ->
          Log.d(this.javaClass.simpleName,
                "Loaded instruction files; about to parse instructions")
          val parseResults = instructionFiles.map { file ->
            parse(file)
          }

          val unparsableFiles = parseResults.filterIsInstance<Result.Err<UnparsableFile, Resource>>()
            .map { x -> x.errValue }
            .toImmutableSet()
          Log.d(this.javaClass.simpleName, "unparsableFiles: ${unparsableFiles}")
          val resources = parseResults.filterIsInstance<Result.Ok<UnparsableFile, Resource>>()
            .map { x -> x.okValue }
          Log.d(this.javaClass.simpleName, "resources: ${resources}")

          parsedResourcess = ParsedResourcess(
            unparsableFiles = unparsableFiles,
            instructions = resources.filterIsInstance<Instruction>().toImmutableSet(),
            icons = resources.filterIsInstance<Icon>().toImmutableSet()
          )
          Log.d(this.javaClass.simpleName, "Parsed resources")
          Log.d(this.javaClass.simpleName, parsedResourcess.toString())
          parsedResourcess
        }
    }
  }

  fun parse(file: File): Result<UnparsableFile, Resource> {
    return try {
      val fileName = URLDecoder.decode(file.name, "UTF-8")
      val fileExtension = fileName.substringAfterLast(".")
      if (audioFileFormats.contains(fileExtension)) {
        fileToInstruction(file, fileName)
      }
      else if (iconFileFormats.contains(fileExtension)){
        fileToIcon(file, fileName)
      }
      else {
        return Result.Err(
          UnparsableFile(file.absolutePath, ParsingFailure.FileFormat()))
      }
    }
    catch (e: UnsupportedEncodingException) {
      Log.e("parse file", "parse failure: ${e}")
      Result.Err<UnparsableFile, Resource>(
        UnparsableFile(file.name,
                       ParsingFailure.FileNameEncoding()))
    }
  }

  fun fileToInstruction(file: File, fileName: String): Result<UnparsableFile, Instruction> {
    Log.d("parse instruction", "Instruction file to parse: ${file.absolutePath}")
    return try {
      val (subject, language, cueTime) =
        fileName.substringBeforeLast(".").split('_')

      Log.d("parse instruction", "subject: ${subject}    language: ${language}    cueStartTimeMilliseconds: ${cueTime}")

      Result.Ok(
        Instruction(
          subject = subject,
          language = language,
          absolutePath = file.absolutePath,
          cueStartTime = cueTime.toLong()))
    }
    catch (e: IndexOutOfBoundsException) {
      e.printStackTrace()
      Log.e("parse instruction", "parse failure: ${e}")
      Result.Err(
        UnparsableFile(file.name,
                       ParsingFailure.InstructionFileNameFormat()))
    }
    catch (e: NumberFormatException) {
      e.printStackTrace()
      Log.e("parse instruction", "parse failure: ${e}")
      Result.Err(
        UnparsableFile(file.name,
                       ParsingFailure.InstructionCueTime()))
    }
  }

  fun fileToIcon(file: File, fileName: String): Result<UnparsableFile, Icon> {
    Log.d("parse icon", "Icon file to parse: ${file.absolutePath}")
    val subject = fileName.substringBeforeLast(".")

    Log.d("parse icon", "subject: ${subject}")

    return Result.Ok(
      Icon(
        subject = subject,
        absolutePath = file.absolutePath))
  }
}
