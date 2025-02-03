package com.github.libretube.compat

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

object PictureInPictureCompat {

    fun isPictureInPictureAvailable(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    fun isInPictureInPictureMode(activity: Activity) = activity.isInPictureInPictureMode

    fun setPictureInPictureParams(activity: Activity, params: PictureInPictureParamsCompat) {
        if (isPictureInPictureAvailable(activity)) {
            activity.setPictureInPictureParams(params.toPictureInPictureParams())
        }
    }

    fun enterPictureInPictureMode(activity: Activity, params: PictureInPictureParamsCompat) {
        if (isPictureInPictureAvailable(activity)) {
            activity.enterPictureInPictureMode(params.toPictureInPictureParams())
        }
    }
}
