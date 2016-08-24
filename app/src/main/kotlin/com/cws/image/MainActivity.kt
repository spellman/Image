package com.cws.image

import android.app.Activity
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
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val store = (application as App).store
    store.dispatch(showCurrentView(this))
  }

  override fun onBackPressed() {
    val store = (application as App).store
    store.dispatch(navigateBack(this))
  }
}

data class LanguagesProps(val languages: ImmutableSet<String>)

class LanguagesView : RenderableView {
  var c: Context
  var store: Store<Action<Actions, *>, State>
  lateinit var languages: ImmutableSet<String>

  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
  }
  constructor(c: Context, props: LanguagesProps) : this(c) {
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
            onClick { v -> store.dispatch(setLanguage(l)) }
          }
        }
      }
    }
  }
}

data class InstructionsProps(val instructions: ImmutableSet<Instruction>,
                             val language: String)

class InstructionsView : RenderableView {
  var c: Context
  var store: Store<Action<Actions, *>, State>
  lateinit var instructions: ImmutableSet<Instruction>

  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
  }
  constructor(c: Context, props: InstructionsProps) : this(c) {
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
              store.dispatch(navigateTo(NavigationFrame("instruction", InstructionProps(i)),
                                        c as Activity))
            }
          }
        }
      }
    }
  }
}

class MainView : RenderableView {
  var c: Context
  var store: Store<Action<Actions, *>, State>

  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
  }

  override fun view() {
    linearLayoutCompat {
      size(FILL, FILL)
      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
      LanguagesView(c, LanguagesProps(getLanguages(store.state))).view()
      InstructionsView(c, InstructionsProps(getInstructions(store.state),
                                            getLanguage(store.state))).view()
    }
  }
}

data class InstructionProps(val instruction: Instruction)

class InstructionView : RenderableView {
  var c: Context
  var store: Store<Action<Actions, *>, State>
  lateinit var instruction: Instruction

  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
  }
  constructor(c: Context, props: InstructionProps) : this(c) {
    this.instruction = props.instruction
  }

  override fun view() {
    appCompatTextView {
      text(instruction.toString())
      textColor(android.graphics.Color.BLACK)
    }
  }
}
