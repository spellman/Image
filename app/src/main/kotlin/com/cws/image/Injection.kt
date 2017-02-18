package com.cws.image

import android.content.Context
import com.github.andrewoma.dexx.kollection.immutableSetOf

fun provideInstructionsRepository(app: App): InstructionsRepository {
  return InstructionsRepository(app as Context,
                                app.storageDir,
                                app.tokenFile)
}

val audioFileFormats = immutableSetOf(
  "aac",
  "flac",
  "mkv",
  "mp3",
  "mp4",
  "m4a",
  "ogg",
  "wav"
)

val iconFileFormats = immutableSetOf(
  "jpg",
  "png"
)

fun provideGetInstructions(app: App): GetInstructions {
  return GetInstructions.getInstance(provideInstructionsRepository(app),
                                     audioFileFormats,
                                     iconFileFormats)
}
