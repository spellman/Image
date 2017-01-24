package com.cws.image

import android.support.v4.view.PagerAdapter
import android.view.View
import android.view.ViewGroup

class SingleViewPagerAdapter(
  // 2017-01-17 Cort Spellman
  // TODO: How do I tie this into DataBinding?
  // See how to use DataBinding with a RecyclerView adapter, maybe other adapter.
  // Do similar.
  val languages: MutableList<String>,
  val view: View
) : PagerAdapter() {
  override fun instantiateItem(container: ViewGroup?,
                               position: Int): Any {
    container?.getChildAt(0) ?: container?.addView(view)
    view.tag = languages[position]
    return view
  }

  override fun destroyItem(container: ViewGroup?,
                           position: Int,
                           `object`: Any?) {
    // There is only one view; no need to destroy anything.
   }

  override fun getCount(): Int {
    return languages.count()
  }

  override fun isViewFromObject(view: View?, `object`: Any?): Boolean {
    return (`object` as? View) == view
  }

  override fun getPageTitle(position: Int): CharSequence {
    return languages[position]
  }

  override fun getItemPosition(`object`: Any?): Int {
    (`object` as? View)?.let {
      return languages.indexOf(it.tag as String)
    }
    return POSITION_NONE
  }

  fun addLanguage(language: String) {
    languages.add(language)
  }
}
