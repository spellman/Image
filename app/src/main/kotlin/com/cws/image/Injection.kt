package com.cws.image

import android.content.Context

fun provideInstructionsRepository(app: App): InstructionsRepository {
  return InstructionsRepository(app as Context,
                                app.storageDir,
                                app.tokenFile)
}

fun provideGetInstructions(app: App): GetInstructions {
  return GetInstructions(provideInstructionsRepository(app))
}
