package com.sample.bluetooth

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.sample.bluetooth.ble.BluetoothLeUtil
import com.sample.bluetooth.ble.GattCallback
import com.sample.bluetooth.util.ACTION_BLE_START_SCAN
import com.sample.bluetooth.util.ACTION_CACHE_CLIENT_MESSENGER
import com.sample.bluetooth.util.ACTION_UPDATE_UI_TOAST
import com.sample.bluetooth.util.XIAOMI_ENV_SENSOR_CHARACTERISTIC
import com.sample.bluetooth.util.XIAOMI_ENV_SENSOR_SERVICE
import com.sample.bluetooth.util.XIAOMI_MIJIA_SENSOR_NAME
import com.sample.bluetooth.util.isReadable
import java.util.*

@Suppress("all")
class BluetoothLeService : Service() {

    private var mScanning = false
    private lateinit var mGattCallback: GattCallback

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mBleScanner by lazy {
        mBluetoothAdapter.bluetoothLeScanner
    }

    override fun onCreate() {
        super.onCreate()

        mGattCallback = GattCallback(object : GattCallback.ConnectionStateListener {
            override fun onConnected() {
                discoveryServices()
            }

            override fun onServiceDiscovered() {
                updateUI()
                enableNotify()
            }
        })
    }

    fun updateUI() {
        Message.obtain().apply {
            what = ACTION_UPDATE_UI_TOAST
            clientMessenger?.send(this)
        }
    }

    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bluetoothDevice = result.device

            if (mScanning) {
                stopBleScan()
            }

            // todo core connect to the target ...
            bluetoothDevice.apply {
                Log.d(TAG, "connecting!!! ---> : ${this.name} + ${this.address}")
                connectTargetDevice(this)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.d(TAG, "onScanFailed: error code: $errorCode")
        }
    }

    fun connectTargetDevice(bluetoothDevice: BluetoothDevice) {
        bluetoothDevice.connectGatt(
            this@BluetoothLeService,
            false,
            mGattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    // inner class InnerBinder : Binder() {
    //     fun getService() = this@BluetoothLeService
    // }

    private var clientMessenger: Messenger? = null

    private val mInnerHandler by lazy {
        InnerHandler(this@BluetoothLeService)
    }

    internal class InnerHandler(private val service: BluetoothLeService) :
        Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_CACHE_CLIENT_MESSENGER -> {
                    service.clientMessenger = msg.replyTo
                }

                ACTION_BLE_START_SCAN -> {
                    Log.d(TAG, "handleMessage: start ble scan is called")
                    service.startBleScan()
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return Messenger(mInnerHandler).binder
    }

    fun startBleScan() {
        if (mScanning) {
            stopBleScan()

        } else {
            mScanning = true

            val scanFilter = ScanFilter.Builder()
                .setDeviceName(XIAOMI_MIJIA_SENSOR_NAME)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            val filters = mutableListOf<ScanFilter>().apply {
                add(scanFilter)
            }

            mBleScanner.startScan(filters, scanSettings, mScanCallback)
        }
    }

    fun stopBleScan() {
        mBleScanner.stopScan(mScanCallback)
        mScanning = false
    }


    fun discoveryServices() {
        Handler(Looper.getMainLooper()).postDelayed({
            mGattCallback.mBluetoothGatt?.discoverServices()
        }, 100)
    }

    fun enableNotify() {
        val serviceUuid = UUID.fromString(XIAOMI_ENV_SENSOR_SERVICE)
        val charUuid = UUID.fromString(XIAOMI_ENV_SENSOR_CHARACTERISTIC)

        mGattCallback.mBluetoothGatt?.getService(serviceUuid)?.let {
            Log.d(TAG, "enableNotify: getService successfully: ${it.uuid}")
            val characteristic = it.getCharacteristic(charUuid)
            Log.d(TAG, "enableNotify: characteristic => $characteristic")

            BluetoothLeUtil(mGattCallback.mBluetoothGatt).enableNotifications(characteristic)
        }

        // val characteristic = mBluetoothGatt?.getService(serviceUuid)?.getCharacteristic(
        //     charUuid
        // )


        // characteristic?.descriptors?.forEach {
        //     Log.w(TAG, "*** descriptors uuid: ${it.uuid}")
        // }

    }

    companion object {
        private const val TAG = "BluetoothLeService"
    }

    // ---------------------------------------------------------------------------------------------
    fun readBatteryLevel(gatt: BluetoothGatt) {

        val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val batteryLevelChar =
            gatt.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)

        if (batteryLevelChar?.isReadable() == true) {
            gatt.readCharacteristic(batteryLevelChar)
        }
    }


}