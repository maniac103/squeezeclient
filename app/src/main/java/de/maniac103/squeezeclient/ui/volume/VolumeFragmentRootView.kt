package de.maniac103.squeezeclient.ui.volume

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

class VolumeFragmentRootView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    fun setXOffsetRatio(offset: Float) {
        val width = width
        if (width > 0) {
            translationX = offset * width
        }
    }
}