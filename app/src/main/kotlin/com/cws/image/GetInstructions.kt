package com.cws.image

import com.github.andrewoma.dexx.kollection.ImmutableSet
import com.github.andrewoma.dexx.kollection.toImmutableSet
import io.reactivex.Single
import timber.log.Timber
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
    if (parsedResourcess != null) {
      return Single.just(parsedResourcess)
    }
    else {
      return instructionsGateway.getInstructionFiles()
        .map { instructionFiles ->
          val parseResults = instructionFiles.map { file ->
            parse(file)
          }

          val unparsableFiles = parseResults.filterIsInstance<Result.Err<UnparsableFile, Resource>>()
            .map { x -> x.errValue }
            .toImmutableSet()
          val resources = parseResults.filterIsInstance<Result.Ok<UnparsableFile, Resource>>()
            .map { x -> x.okValue }

          parsedResourcess = ParsedResourcess(
            unparsableFiles = unparsableFiles,
            instructions = resources.filterIsInstance<Instruction>().toImmutableSet(),
            icons = resources.filterIsInstance<Icon>().toImmutableSet()
          )
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
        Timber.e(
          Exception(
            "File extension indicates neither an audio file nor an icon file: ${fileName}"))
        return Result.Err(
          UnparsableFile(file.absolutePath, ParsingFailure.FileFormat()))
      }
    }
    catch (e: UnsupportedEncodingException) {
      Timber.e(
        Exception(
          "Filename does not consist of valid UTF-8 characters: ${file}", e))
      Result.Err<UnparsableFile, Resource>(
        UnparsableFile(file.name,
                       ParsingFailure.FileNameEncoding()))
    }
  }

  fun fileToInstruction(file: File, fileName: String): Result<UnparsableFile, Instruction> {
    return try {
      val (subject, language, cueTime) =
        fileName.substringBeforeLast(".").split('_')

      Result.Ok(
        Instruction(
          subject = subject,
          language = language,
          absolutePath = file.absolutePath,
          cueStartTime = cueTime.toLong()))
    }
    catch (e: IndexOutOfBoundsException) {
      Timber.e(
        Exception(
          "Invalid instruction file name format: ${file}"))
      Result.Err(
        UnparsableFile(file.name,
                       ParsingFailure.InstructionFileNameFormat()))
    }
    catch (e: NumberFormatException) {
      Timber.e(
        NumberFormatException(
          "Invalid instruction cue time in file name: ${file}"))
      Result.Err(
        UnparsableFile(file.name,
                       ParsingFailure.InstructionCueTime()))
    }
  }

  fun fileToIcon(file: File, fileName: String): Result<UnparsableFile, Icon> {
    val subject = fileName.substringBeforeLast(".")

    return Result.Ok(
      Icon(
        subject = subject,
        absolutePath = file.absolutePath))
  }
}
