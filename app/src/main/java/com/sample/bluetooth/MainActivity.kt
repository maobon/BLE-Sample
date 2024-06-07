package com.sample.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button

    private var mBluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread {
                btnScan.text = if (value) "Stop Scan" else "Start Scan"
            }
        }

    private val mPromptDialog: DialogUtil by lazy {
        DialogUtil(this@MainActivity)
    }

    private val mBluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val mBleScanner by lazy {
        mBluetoothAdapter.bluetoothLeScanner
    }

    private val mBluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            // Bluetooth is enabled, good to go
            // createToast(this@MainActivity, "Bluetooth good 2 GO")
            startBleScan()

        } else {
            // User dismissed or denied Bluetooth prompt
            // again and again until user enable bluetooth
            promptEnableBluetooth()
        }
    }

    @Suppress("all")
    private val mScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (isScanning) stopBleScan()

            result.device.apply {
                Log.d(TAG, "connecting!!! ---> : ${this.name} + ${this.address}")
                connectGatt(this@MainActivity, false, mGattCallback, BluetoothDevice.TRANSPORT_LE)
            }
        }

        override fun onScanFailed(errorCode: Int) {

        }
    }

    private val mGattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    // stash BluetoothGatt instance ...
                    this@MainActivity.mBluetoothGatt = gatt

                    createToast(this@MainActivity, "Connected OK")
                    discoveryServices()

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }

            } else {
                Log.w(
                    "BluetoothGattCallback",
                    "Error $status encountered for $deviceAddress! Disconnecting..."
                )
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered ${services.size} services for ${device.address}"
                )

                // See implementation just above this section
                printGattTable()
                // Consider connection setup as complete here

                enableNotify()
            }
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {

            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i(
                            "BluetoothGattCallback",
                            "Read characteristic --> $uuid:\n${value.toHexString()}"
                        )
                    }

                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }

                    else -> {
                        Log.e(
                            "BluetoothGattCallback",
                            "Characteristic read failed for $uuid, error: $status"
                        )
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            val uuid = characteristic.uuid
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {

                    val hexString = value.toHexString()
                    Log.i(
                        "BluetoothGattCallback", "Read characteristic $uuid:\n $hexString"
                    )

                    // todo ...............
                    // Log.d(
                    //     TAG, "onCharacteristicRead: ppp=${
                    //         littleEndianConversion(value)
                    //     }"
                    // )
                }

                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                }

                else -> {
                    Log.e(
                        "BluetoothGattCallback",
                        "Characteristic read failed for $uuid, error: $status"
                    )
                }
            }
        }

        @Deprecated("Deprecated for Android 13+")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {

            with(characteristic) {
                Log.i(
                    "BluetoothGattCallback",
                    "Characteristic $uuid changed | value: ${value.toHexString()}"
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray
        ) {

            val newValueHex = value.toHexString()
            with(characteristic) {
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: $newValueHex")
            }

            // ...
            val str = String(value)
            Log.d(TAG, "onCharacteristicChanged: str: $str")

            // temp
            val kk = with(str) {
                substring(indexOf("T="), lastIndexOf(" "))
            }
            Log.d(TAG, "onCharacteristicChanged: $kk")

            // temp
            val k = with(str) {
                substring(indexOf("H="), length - 1)
            }
            Log.d(TAG, "onCharacteristicChanged: $k")
        }
    }

    @SuppressLint("MissingPermission")
    private fun readBatteryLevel(gatt: BluetoothGatt) {

        val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        val batteryLevelChar =
            gatt.getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)

        if (batteryLevelChar?.isReadable() == true) {
            gatt.readCharacteristic(batteryLevelChar)
        }
    }


    //

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val XIAOMI_MIJIA_SENSOR_NAME = "MJ_HT_V1"

        private const val XIAOMI_ENV_SERVICE = "226c0000-6476-4566-7562-66734470666d"
        private const val XIAOMI_ENV_CHARACTERISTIC = "226caa55-6476-4566-7562-66734470666d"

        private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Button 1
        btnScan = findViewById(R.id.btn_test)
        btnScan.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }

        // Button 2
        btnTest2 = findViewById(R.id.btn_test_2)
        btnTest2.setOnClickListener {
            enableNotify()
        }

        // Button 3
        btnTest3 = findViewById(R.id.btn_test_3)
        btnTest3.setOnClickListener {

        }
    }

    override fun onResume() {
        super.onResume()

        if (!mBluetoothAdapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    @SuppressLint("MissingPermission")
    private fun discoveryServices() {
        Handler(Looper.getMainLooper()).postDelayed({
            this@MainActivity.mBluetoothGatt?.discoverServices()
        }, 100)
    }


    private fun enableNotify() {
        val serviceUuid = UUID.fromString(XIAOMI_ENV_SERVICE)
        val charUuid = UUID.fromString(XIAOMI_ENV_CHARACTERISTIC)

        mBluetoothGatt?.getService(serviceUuid)?.let {
            Log.d(TAG, "enableNotify: getService successfully: ${it.uuid}")
            val characteristic = it.getCharacteristic(charUuid)
            Log.d(TAG, "enableNotify: characteristic => $characteristic")

            enableNotifications(characteristic)
        }

        // val characteristic = mBluetoothGatt?.getService(serviceUuid)?.getCharacteristic(
        //     charUuid
        // )


        // characteristic?.descriptors?.forEach {
        //     Log.w(TAG, "*** descriptors uuid: ${it.uuid}")
        // }

    }

    // ....
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredBluetoothPermissions()) return

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() = runOnUiThread {
        mPromptDialog.show(
            "Location permission required",
            "Starting from Android M (6.0), the system requires apps to be granted " + "location access in order to scan for BLE devices."
        ) { _, _ ->
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ), PERMISSION_REQUEST_CODE
            )
        }

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() = runOnUiThread {
        mPromptDialog.show(
            "Bluetooth permission required",
            "Starting from Android 12, the system requires apps to be granted " + "Bluetooth access in order to scan for and connect to BLE devices."
        ) { _, _ ->
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT
                ), PERMISSION_REQUEST_CODE
            )
        }
    }


    // ....
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST_CODE) return

        val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
            it.second == PackageManager.PERMISSION_DENIED && !ActivityCompat.shouldShowRequestPermissionRationale(
                this, it.first
            )
        }

        val containsDenial = grantResults.any {
            it == PackageManager.PERMISSION_DENIED
        }
        val allGranted = grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }

        when {
            containsPermanentDenial -> {
                // TODO: Handle permanent denial (e.g., show AlertDialog with justification)
                // Note: The user will need to navigate to App Settings and manually grant
                // permissions that were permanently denied
            }

            containsDenial -> {
                // requestRelevantRuntimePermissions()
            }

            allGranted && hasRequiredBluetoothPermissions() -> {
                startBleScan()
            }

            else -> {
                // Unexpected scenario encountered when handling permissions
                recreate()
            }
        }
    }

    @Suppress("all")
    private fun startBleScan() {
        if (!hasRequiredBluetoothPermissions()) {
            requestRelevantRuntimePermissions()

        } else {
            isScanning = true

            val scanFilter = ScanFilter.Builder().setDeviceName(XIAOMI_MIJIA_SENSOR_NAME).build()

            val scanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

            val filters = mutableListOf<ScanFilter>().apply {
                this.add(scanFilter)
            }

            mBleScanner.startScan(filters, scanSettings, mScanCallback)
        }
    }

    @Suppress("all")
    private fun stopBleScan() {
        mBleScanner.stopScan(mScanCallback)
        isScanning = false
    }

    /**
     * Prompts the user to enable Bluetooth via a system dialog.
     *
     * For Android 12+, [Manifest.permission.BLUETOOTH_CONNECT] is required to use
     * the [BluetoothAdapter.ACTION_REQUEST_ENABLE] intent.
     */
    private fun promptEnableBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            // Insufficient permission to prompt for Bluetooth enabling
            Log.d(TAG, "Insufficient permission to prompt for Bluetooth enabling")
            return
        }

        if (!mBluetoothAdapter.isEnabled) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                mBluetoothEnablingResult.launch(this)
            }
        }
    }

    //
    @SuppressLint("MissingPermission")
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }

            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        mBluetoothGatt?.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, payload, writeType)
            } else {
                // Fall back to deprecated version of writeCharacteristic for Android <13
                gatt.legacyCharacteristicWrite(characteristic, payload, writeType)
            }
        } ?: error("Not connected to a BLE device!")
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.legacyCharacteristicWrite(
        characteristic: BluetoothGattCharacteristic, value: ByteArray, writeType: Int
    ) {
        characteristic.writeType = writeType
        characteristic.value = value
        writeCharacteristic(characteristic)
    }


    //
    @SuppressLint("MissingPermission")
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        mBluetoothGatt?.let { gatt ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, payload)
            } else {
                // Fall back to deprecated version of writeDescriptor for Android <13
                gatt.legacyDescriptorWrite(descriptor, payload)
            }

        } ?: error("Not connected to a BLE device!")
    }

    @SuppressLint("MissingPermission")
    @TargetApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.legacyDescriptorWrite(
        descriptor: BluetoothGattDescriptor, value: ByteArray
    ): Boolean {
        descriptor.value = value
        return writeDescriptor(descriptor)
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)

        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }

            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    @SuppressLint("MissingPermission")
    private fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e(
                "ConnectionManager",
                "${characteristic.uuid} doesn't support indications/notifications"
            )
            return
        }

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (mBluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e(
            "ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }
}