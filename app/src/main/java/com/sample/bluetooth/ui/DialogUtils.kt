package com.sample.bluetooth.ui

import android.Manifest
import android.app.Activity
import android.content.DialogInterface.OnClickListener
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.sample.bluetooth.R

object DialogUtil {

    const val PERMISSION_REQUEST_CODE = 100

    enum class PermissionType {
        Location, Bluetooth
    }

    private fun create(
        activity: Activity, title: String, message: String, onClickListener: OnClickListener
    ) = with(activity) {
        runOnUiThread {
            AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false)
                .setPositiveButton(android.R.string.ok, onClickListener).show()
        }
    }

    fun showReqPermissions(permissionType: PermissionType, activity: Activity) {
        when (permissionType) {
            PermissionType.Location -> {
                create(
                    activity,
                    activity.getString(R.string.title_location),
                    activity.getString(R.string.prompt_content_location)
                ) { _, _ ->
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                    ActivityCompat.requestPermissions(
                        activity, permissions, PERMISSION_REQUEST_CODE
                    )
                }
            }

            PermissionType.Bluetooth -> {
                create(
                    activity,
                    activity.getString(R.string.title_bluetooth),
                    activity.getString(R.string.prompt_content_bluetooth)
                ) { _, _ ->
                    val permissions = arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                    )
                    ActivityCompat.requestPermissions(
                        activity, permissions, PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

}