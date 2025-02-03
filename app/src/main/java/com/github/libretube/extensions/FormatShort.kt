package com.github.libretube.extensions

import android.icu.text.CompactDecimalFormat
import com.github.libretube.helpers.LocaleHelper

fun Long?.formatShort(): String = CompactDecimalFormat
    .getInstance(LocaleHelper.getAppLocale(), CompactDecimalFormat.CompactStyle.SHORT)
    .format(this ?: 0)
