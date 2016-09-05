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
    store.dispatch(setActivity(this))
    store.dispatch(showCurrentView())
  }

  override fun onDestroy() {
    store.dispatch(clearActivity())
    super.onDestroy()
  }

  override fun onBackPressed() {
    store.dispatch(navigateBack())
  }
}

class LanguagesView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var languages: ImmutableSet<String>

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context,
              dispatch: (Action<Actions, *>) -> State,
              languages: ImmutableSet<String>) : this(c) {
    this.dispatch = dispatch
    this.languages = languages
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

class InstructionsView : RenderableView {
  var c: Context
  lateinit var dispatch: (Action<Actions, *>) -> State
  lateinit var instructions: ImmutableSet<Instruction>

  constructor(c: Context) : super(c) {
    this.c = c
  }
  constructor(c: Context,
              dispatch: (Action<Actions, *>) -> State,
              instructions: ImmutableSet<Instruction>,
              language: String) : this(c) {
    this.dispatch = dispatch
    this.instructions = getVisibleInstructions(instructions, language)
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
              dispatch(setInstruction(i))
              dispatch(navigateTo("instruction"))
            }
          }
        }
      }
    }
  }
}

abstract class TopLevelView : RenderableView {
  var c: Context
  var store: Store<Action<Actions, *>, State>
  var dispatch: (Action<Actions, *>) -> State


  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
    this.dispatch = { x -> store.dispatch(x) }
  }
}

class MainView(c: Context) : TopLevelView(c) {
  override fun view() {
    linearLayoutCompat {
      size(FILL, FILL)
      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
      LanguagesView(c,
                    dispatch,
                    getLanguages(store.state.instructionsState)).view()
      InstructionsView(c,
                       dispatch,
                       getInstructions(store.state.instructionsState),
                       getLanguage(store.state)).view()
    }
  }
}

class InstructionView(c: Context) : TopLevelView(c) {
  override fun view() {
    appCompatTextView {
      text(getInstruction(store.state).toString())
      textColor(android.graphics.Color.BLACK)
    }
  }
}
