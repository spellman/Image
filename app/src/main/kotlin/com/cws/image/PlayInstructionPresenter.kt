package com.cws.image

import android.os.SystemClock
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.run
import java.util.concurrent.TimeUnit

sealed class CueTimerEvent {
  class Prepared : CueTimerEvent()
  class Started : CueTimerEvent()
}
class PlayInstructionPresenter(
  private val activity: PlayInstructionActivity,
  private val mediaPlayerFragment: PlayInstructionFragment,
  private val instruction: InstructionViewModel,
  cueTimerHasInitialized: Observable<Boolean>
  ) {
  private val compositeDisposable = CompositeDisposable()
  val cueTimerEvents: PublishSubject<CueTimerEvent> = PublishSubject.create()
  val viewIsReady: PublishSubject<Unit> = PublishSubject.create()

  init {
    compositeDisposable.add(
      mediaPlayerFragment.instructionEvents.subscribe(
        { event ->
          if (event is InstructionEvent.ReadyToPrepare) {
            prepareInstructionAudio()
          }
        },
        { e ->
          activity.finishWithInstructionError(instruction, e.message as String)
        },
        { activity.finishWithInstructionComplete() }
      )
    )

    compositeDisposable.add(
      cueTimerHasInitialized
        .subscribeOn(Schedulers.io())
        .observeOn(Schedulers.computation())
        .subscribe { _ -> prepareCueTimer() }
    )

    compositeDisposable.add(
      mediaPlayerFragment.audioFocusEvents.subscribe(
        { event ->
          val unused = when (event) {
            is AudioFocusEvent.DelayInObtainingAudiofocus -> showDelayMessage()

            is AudioFocusEvent.AudiofocusLoss -> {
              stopInstruction()
              activity.finishWithInstructionError(
                instruction,
                "Lost audiofocus, having requested and gained exclusive audiofocus.")
            }
          }
        },
        { e ->
          activity.finishWithInstructionError(instruction, e.message as String)
        }
      )
    )

    compositeDisposable.add(
      Observable.combineLatest(
        mediaPlayerFragment.instructionEvents,
        cueTimerEvents,
        viewIsReady,
        Function3 { instructionEvent: InstructionEvent, cueTimerEvent: CueTimerEvent, _: Unit ->
          Pair(instructionEvent, cueTimerEvent)
        }
      )
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { (instructionEvent, cueTimerEvent) ->
            if (instructionEvent is InstructionEvent.AudioPrepared
                && cueTimerEvent is CueTimerEvent.Prepared) {
              startInstruction()
            }
            else if (instructionEvent is InstructionEvent.InstructionStarted
                     && cueTimerEvent is CueTimerEvent.Prepared) {
              startCueTimer()
            }
            else if (instructionEvent is InstructionEvent.CueTimerFinished) {
              showCue()
            }
          },
          {_ -> },
          {}
        )
    )
  }

  fun currentTime(): Long {
    return SystemClock.uptimeMillis()
  }

  fun prepareCueTimer() {
    activity.prepareCueTimer()
    cueTimerEvents.onNext(CueTimerEvent.Prepared())
  }

  fun prepareInstructionAudio() {
    mediaPlayerFragment.prepareInstructionAudio(instruction.audioAbsolutePath)
  }

  fun notifyThatViewIsReady() {
    viewIsReady.onNext(Unit)
  }

  fun stopInstruction() {
    mediaPlayerFragment.stopInstructionAudio()
  }

  fun startInstruction() {
    Observable.timer(250L, TimeUnit.MILLISECONDS).subscribe { _ ->
      launch(CommonPool) {
        cueTimerEvents.onNext(CueTimerEvent.Started())
        mediaPlayerFragment.startInstruction(
          instruction.cueStartTimeMilliseconds,
          currentTime())

        run(UI) {
          startCueTimer()
        }
      }
    }
  }

  fun startCueTimer() {
    activity.startCueTimer()
  }

  fun showDelayMessage() {
    activity.showDelayMessage("Preparing...")
  }

  fun showCue() {
    activity.showCue()
  }

  fun onDestroy() {
    compositeDisposable.dispose()
  }
}
