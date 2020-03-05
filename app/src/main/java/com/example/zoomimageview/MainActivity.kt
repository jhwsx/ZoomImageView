package com.example.zoomimageview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    private val picList = listOf(
        R.drawable.pic_1,
        R.drawable.pic_2,
        R.drawable.pic_3,
        R.drawable.pic_4,
        R.drawable.pic_5
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewpager.adapter = MyPagerAdapter(picList)
    }

    private class MyPagerAdapter(private val data: List<Int>) : PagerAdapter() {

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val zoomImageView = ZoomImageView(container.context)
            zoomImageView.setImageResource(data[position])
            container.addView(zoomImageView)
            return zoomImageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view == obj
        }

        override fun getCount(): Int {
            return data.size
        }

    }
}
