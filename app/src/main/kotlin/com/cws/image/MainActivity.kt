package com.cws.image

import android.content.Context
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutCompat
import android.text.TextUtils
import android.view.View
import com.brianegan.bansa.Store
import com.github.andrewoma.dexx.kollection.ImmutableSet
import com.github.andrewoma.dexx.kollection.toImmutableSet
import trikita.anvil.BaseDSL
import trikita.anvil.DSL.*
import trikita.anvil.RenderableView
import trikita.anvil.appcompat.v7.AppCompatv7DSL
import trikita.anvil.appcompat.v7.AppCompatv7DSL.*

class MainActivity : AppCompatActivity() {
  lateinit var store: Store<State>

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    store = (application as App).store
    setContentView(RootView(this))
  }

  override fun onBackPressed() {
    if (store.state.navigationStack.size() > 1) {

      // 2016-10-02 Cort Spellman
      // FIXME: Can this go in a listener on the store or something?
      if (store.state.navigationStack.peek() is Scene.Instruction) {
        store.dispatch(Action.AbortInstructionSequence())
      }
      store.dispatch(Action.NavigateBack())
    }
    else {
      finish()
    }
  }
}

fun viewLanguage(dispatch: (com.brianegan.bansa.Action) -> State,
                 language: String) {
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
      text(language)
    }
    onClick { v -> dispatch(Action.SetLanguage(language)) }
  }
}

fun viewLanguages(dispatch: (com.brianegan.bansa.Action) -> State,
                  c: Context,
                  languages: ImmutableSet<String>) {
  horizontalScrollView {
    size(FILL, dip(48))
    AppCompatv7DSL.gravity(LEFT)
    backgroundColor(ContextCompat.getColor(c, R.color.colorPrimary))
    linearLayoutCompat {
      size(WRAP, FILL)
      AppCompatv7DSL.orientation(LinearLayoutCompat.HORIZONTAL)
//        backgroundColor(android.graphics.Color.argb(32, 255, 0, 0))
      languages.map { l -> viewLanguage(dispatch, l)}
    }
  }
}

fun viewSubject(dispatch: (com.brianegan.bansa.Action) -> State,
                instruction: Instruction) {
  appCompatTextView {
    size(FILL, WRAP)
    text(instruction.subject + " - " + instruction.language)
    margin(dip(0), dip(16))
    textColor(android.graphics.Color.BLACK)
    onClick { v ->
      dispatch(Action.NavigateTo(Scene.Instruction()))
      dispatch(Action.PlayInstruction(instruction))
    }
  }
}

fun viewSubjects(dispatch: (com.brianegan.bansa.Action) -> State,
                 instructions: ImmutableSet<Instruction>) {
  scrollView {
    size(FILL, dip(0))
    BaseDSL.weight(1f)
    linearLayoutCompat {
      size(FILL, WRAP)
      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//        backgroundColor(android.graphics.Color.argb(32, 0, 0, 255))
      instructions.map { i -> viewSubject(dispatch, i)}
    }
  }
}

fun viewMain(c: Context, store: Store<State>) {
  val dispatch = { x: com.brianegan.bansa.Action -> store.dispatch(x)}

  linearLayoutCompat {
    size(FILL, FILL)
    AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
    viewLanguages(dispatch, c, store.state.languages)
    viewSubjects(dispatch,
                 store.state.instructions.filter { i -> i.language == store.state.language }
                     .toImmutableSet())
  }
}

fun viewInstruction(store: Store<State>) {
  appCompatTextView {
    text(store.state.languageToDisplay)
    text(store.state.subjectToDisplay)
    textColor(android.graphics.Color.BLACK)
  }
}

class RootView : RenderableView {
  lateinit var c: Context
  lateinit var store: Store<State>

  constructor(c: Context) : super(c) {
    this.c = c
    this.store = (c.applicationContext as App).store
  }

  override fun view() {
    // 2016-09-21 Cort Spellman
    // Bind the result of the when expression in order to force the compiler to
    // check for exhaustiveness.
    val x = when (store.state.navigationStack.peek()) {
      is Scene.Main -> viewMain(c, store)
      is Scene.Instruction -> viewInstruction(store)
    }
  }
}
