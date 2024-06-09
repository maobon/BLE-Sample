package com.sample.bluetooth.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context


class BluetoothUtil(context: Context) {

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun isBluetoothEnable() = mBluetoothAdapter.isEnabled
}