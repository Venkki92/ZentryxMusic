package com.zentryx.music.auto

import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal fun carDrawableIcon(carContext: CarContext, @DrawableRes resId: Int): CarIcon {
    return CarIcon.Builder(IconCompat.createWithResource(carContext, resId)).build()
}
