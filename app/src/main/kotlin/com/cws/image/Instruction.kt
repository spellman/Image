package com.cws.image

interface Resource

data class Instruction(
  val subject: String,
  val language: String,
  val absolutePath: String,
  val cueStartTime: Long
) : Resource

data class Icon(
  val subject: String,
  val absolutePath: String
) : Resource

sealed class ParsingFailure {
  class FileFormat : ParsingFailure()
  class FileNameEncoding : ParsingFailure()
  class InstructionCueTime : ParsingFailure()
  class InstructionFileNameFormat : ParsingFailure()
}

data class UnparsableFile(
  val fileName: String,
  val failure: ParsingFailure
)
