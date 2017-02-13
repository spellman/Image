package com.cws.image

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.github.andrewoma.dexx.kollection.ImmutableSet
import com.github.andrewoma.dexx.kollection.immutableSetOf
import com.github.andrewoma.dexx.kollection.toImmutableSet
import io.reactivex.Single
import java.io.File
import java.io.IOException

class InstructionsRepository(
  context: Context,
  storageDir: File,
  tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb: File
) : InstructionsGateway {
  val localFileSystemInstructionsStorage =
    LocalFileSystemInstructionsStorage(
      context, storageDir, tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb)

  override fun getInstructionFiles(): Single<ImmutableSet<File>> {
    Log.d(this.javaClass.simpleName, "About to get instruction files")
    return localFileSystemInstructionsStorage.getInstructionFiles()
  }
}

class LocalFileSystemInstructionsStorage(
  val context: Context,
  val storageDir: File,
  val tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb: File
) {
  val filesToSkip = immutableSetOf(tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb)

  fun isExternalStorageWritable(): Boolean {
    val s = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == s
  }

  fun isExternalStorageReadable(): Boolean {
    val s = Environment.getExternalStorageState()
    return immutableSetOf(Environment.MEDIA_MOUNTED,
                          Environment.MEDIA_MOUNTED_READ_ONLY
    ).contains(s)
  }

  fun getInstructionFiles(): Single<ImmutableSet<File>> {
    Log.d(this.javaClass.simpleName, "About to get instruction files")
    return ensureInstructionsDirExists()
      .doOnSuccess { dir ->
        Log.d(this.javaClass.simpleName, "Ensured instructions dir exists: ${dir}")
      }
      .flatMap { dir -> ensureInstructionsDirIsAccessibleFromPC() }
      .doOnSuccess { dir ->
        Log.d(this.javaClass.simpleName, "Ensured instructions dir is accessible from a PC: ${dir}")
      }
      .map { dir ->
        if (!isExternalStorageReadable()) {
          throw IOException("External storage is not readable.")
        }

        Log.d(this.javaClass.simpleName, "About to emit instruction files")

        storageDir.listFiles()
          .filter { file -> !filesToSkip.contains(file) }
          .toImmutableSet()
      }
  }

  fun ensureInstructionsDirExists(): Single<File> {
    Log.d(this.javaClass.simpleName, "About to ensure instructions dir exists")
    return if (!isExternalStorageReadable()) {
      throw IOException("External storage is not readable.")
    }
    else {
      if (!storageDir.isDirectory) {
        if (!isExternalStorageWritable()) {
          throw IOException("There is no directory at instructions-directory absolutePath, ${storageDir.absolutePath} and it can't be created because external storage is not wri       table.")
        }
        else {
          if (!storageDir.mkdirs()) {
            throw IOException("Could not create directory ${storageDir.absolutePath}, even though external storage is writable.")
          }
          else {
            Single.just(storageDir)
          }
        }
      }
      else {
        Single.just(storageDir)
      }
    }
  }

  fun ensureInstructionsDirIsAccessibleFromPC(): Single<File> {
    Log.d(this.javaClass.simpleName, "About to ensure instructions dir is accessible from a PC")
    return if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.isFile) {
      if (!isExternalStorageWritable()) {
        throw IOException(
          "There is no token file in the instructions directory, ${storageDir.absolutePath} and it can't be created because external storage is not writable.")
      }
      else {
        if (!tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.createNewFile()) {
          throw IOException(
            "Could not create file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, even though external storage is writable.")
        }
        else {
          Log.d("instructions dir",
                "Created file ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath} (to make directory appear when device is mounted via USB).")
          Single.create<File>(
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
                    emitter.onSuccess(storageDir)
                  }
                  else {
                    emitter.onError(
                      IOException(
                        "A token file, ${tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb.absolutePath}, was created but scanning it failed."))
                  }
                })
            })
        }
      }
    }
    else {
      Single.just(storageDir)
    }
  }
}
