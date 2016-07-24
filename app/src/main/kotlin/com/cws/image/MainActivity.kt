package com.cws.image

import android.content.Context
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutCompat
import android.text.TextUtils
import android.view.View
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import trikita.anvil.BaseDSL
import trikita.anvil.DSL.*
import trikita.anvil.RenderableView
import trikita.anvil.appcompat.v7.AppCompatv7DSL
import trikita.anvil.appcompat.v7.AppCompatv7DSL.*
import trikita.jedux.Action

class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(MainView(this))
  }
}

data class LanguagesProps(val languages: ImmutableList<String>)

class LanguagesView : RenderableView {
  var c: Context? = null
  var props: LanguagesProps = LanguagesProps(languages = immutableListOf())

  constructor(c: Context) : super(c) { this.c = c }
  constructor(c: Context, props: LanguagesProps) : this(c) { this.props = props }

  override fun view() {
    horizontalScrollView {
      size(FILL, dip(48))
      AppCompatv7DSL.gravity(LEFT)
      backgroundColor(ContextCompat.getColor(c, R.color.colorPrimary))
      linearLayoutCompat {
        size(WRAP, FILL)
        AppCompatv7DSL.orientation(LinearLayoutCompat.HORIZONTAL)
//        backgroundColor(android.graphics.Color.argb(32, 255, 0, 0))
        props.languages.map { l ->
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
            onClick { v -> store.dispatch(Action(Actions.SET_LANGUAGE, l)) }
          }
        }
      }
    }
  }
}

data class InstructionsProps(val instructions: ImmutableList<Instruction>)

class InstructionsView : RenderableView {
  var props: InstructionsProps = InstructionsProps(instructions = immutableListOf())

  constructor(c: Context) : super(c)
  constructor(c: Context, props: InstructionsProps) : this(c) { this.props = props }

  override fun view() {
    scrollView {
      size(FILL, dip(0))
      BaseDSL.weight(1f)
      linearLayoutCompat {
        size(FILL, WRAP)
        AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
//        backgroundColor(android.graphics.Color.argb(32, 0, 0, 255))
        props.instructions.map { i ->
          appCompatTextView {
            size(FILL, WRAP)
            text(i.subject + " - " + i.language)
            margin(dip(0), dip(16))
            textColor(android.graphics.Color.BLACK)
          }
        }
      }
    }
  }
}

class MainView(val c: Context) : RenderableView(c) {
  override fun view() {
    linearLayoutCompat {
      size(FILL, FILL)
      AppCompatv7DSL.orientation(LinearLayoutCompat.VERTICAL)
      LanguagesView(c, LanguagesProps(languages = languages)).view()
      InstructionsView(c,
                       InstructionsProps(instructions = getVisibleInstructions(getInstructions(store.state),
                                                                               store.state.language))
      ).view()
    }
  }
}
