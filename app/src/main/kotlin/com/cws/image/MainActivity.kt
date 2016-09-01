package com.cws.image

import android.content.Context
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutCompat
import android.text.TextUtils
import android.view.View
import com.github.andrewoma.dexx.kollection.ImmutableSet
import trikita.anvil.BaseDSL
import trikita.anvil.DSL.*
import trikita.anvil.RenderableView
import trikita.anvil.appcompat.v7.AppCompatv7DSL
import trikita.anvil.appcompat.v7.AppCompatv7DSL.*
import trikita.jedux.Action
import trikita.jedux.Store

class MainActivity : AppCompatActivity() {
  lateinit var store: Store<Action<Actions, *>, State>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    store = (application as App).store
    store.dispatch(updateCurrentViewProps(
                     MainProps(dispatch = { x -> store.dispatch(x) },
                               state = store.state)))
    store.dispatch(setActivity(this))
    store.dispatch(showCurrentView())
  }

  override fun onDestroy() {
    store.dispatch(clearActivity())
    store.dispatch(updateCurrentViewProps(null))
    super.onDestroy()
  }

  override fun onBackPressed() {
    store.dispatch(navigateBack())
  }
}

data class LanguagesProps(val dispatch: (Action<Actions, *>) -> State,
                          val languages: ImmutableSet<String>)

class LanguagesView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var languages: ImmutableSet<String>

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context, props: LanguagesProps) : this(c) {
    this.dispatch = props.dispatch
    this.languages = props.languages
  }

  override fun view() {
    horizontalScrollView {
      size(FILL, dip(48))
      AppCompatv7DSL.gravity(LEFT)
      backgroundColor(ContextCompat.getColor(c, R.color.colorPrimary))
      linearLayoutCompat {
        size(WRAP, FILL)
        AppCompatv7DSL.orientation(LinearLayoutCompat.HORIZONTAL)
//        backgroundColor(android.graphics.Color.argb(32, 255, 0, 0))
        languages.map { l ->
          frameLayout {
            size(WRAP, FILL)
            appCompatTextView {
              size(WRAP, WRAP)
              minimumWidth(dip(72))
              BaseDSL.layoutGravity(BaseDSL.BOTTOM)
              textAlignment(View.TEXT_ALIGNMENT_CENTER)
//              backgroundColor(android.graphics.Color.argb(32, 0, 255, 0))
              textColor(android.graphics.Color.WHITE)
              BaseDSL.margin(dip(16), dip(0))
              // TODO: Bottom padding should be 12px if l spans two lines.
              BaseDSL.padding(dip(12), dip(0), dip(12), dip(20))
              minLines(1)
              maxLines(2)
              ellipsize(TextUtils.TruncateAt.END)
              text(l)
            }
            onClick { v -> dispatch(setLanguage(l)) }
          }
        }
      }
    }
  }
}

data class InstructionsProps(val dispatch: (Action<Actions, *>) -> State,
                             val instructions: ImmutableSet<Instruction>,
                             val language: String)

class InstructionsView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var instructions: ImmutableSet<Instruction>

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context, props: InstructionsProps) : this(c) {
    this.dispatch = props.dispatch
    this.instructions = getVisibleInstructions(props.instructions, props.language)
  }

  override fun view() {
    scrollView {
      size(FILL, dip(0))
      BaseDSL.weight(1f)
      linearLayoutCompat {
        size(FILL, WRAP)
        AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//        backgroundColor(android.graphics.Color.argb(32, 0, 0, 255))
        instructions.map { i ->
          appCompatTextView {
            size(FILL, WRAP)
            text(i.subject + " - " + i.language)
            margin(dip(0), dip(16))
            textColor(android.graphics.Color.BLACK)
            onClick { v ->
              dispatch(
                  navigateTo(NavigationFrame("instruction",
                                             InstructionProps(dispatch, i))))
            }
          }
        }
      }
    }
  }
}

data class MainProps(val dispatch: (Action<Actions, *>) -> State,
                     val state: State)

class MainView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var state: State

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context, props: MainProps) : this(c) {
    this.dispatch = props.dispatch
    this.state = props.state
  }

  override fun view() {
    linearLayoutCompat {
      size(FILL, FILL)
      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
      LanguagesView(c, LanguagesProps(dispatch, getLanguages(state))).view()
      InstructionsView(c, InstructionsProps(dispatch,
                                            getInstructions(state),
                                            getLanguage(state))).view()
    }
  }
}

data class InstructionProps(val dispatch: (Action<Actions, *>) -> State,
                            val instruction: Instruction)

class InstructionView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var instruction: Instruction

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context, props: InstructionProps) : this(c) {
    this.dispatch = props.dispatch
    this.instruction = props.instruction
  }

  override fun view() {
    appCompatTextView {
      text(instruction.toString())
      textColor(android.graphics.Color.BLACK)
    }
  }
}
