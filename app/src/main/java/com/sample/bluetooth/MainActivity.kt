package com.sample.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.sample.bluetooth.ble.BluetoothUtil
import com.sample.bluetooth.ui.DialogUtil
import com.sample.bluetooth.ui.DialogUtil.PERMISSION_REQUEST_CODE
import com.sample.bluetooth.util.ACTION_BLE_START_SCAN
import com.sample.bluetooth.util.ACTION_BLUETOOTH_SCANNING
import com.sample.bluetooth.util.ACTION_BLUETOOTH_SCANNING_STOP
import com.sample.bluetooth.util.ACTION_CACHE_CLIENT_MESSENGER
import com.sample.bluetooth.util.ACTION_SENSOR_DATA
import com.sample.bluetooth.util.ACTION_UPDATE_UI_TOAST
import com.sample.bluetooth.util.createToast
import com.sample.bluetooth.util.hasPermission
import com.sample.bluetooth.util.hasRequiredBluetoothPermissions
import com.sample.bluetooth.util.requestRelevantRuntimePermissions


class MainActivity : AppCompatActivity() {

    private lateinit var btnScan: Button
    private lateinit var btnTest2: Button
    private lateinit var btnTest3: Button

    private var serviceMessenger: Messenger? = null
    private val clientMessenger by lazy {
        Messenger(ClientHandler(this@MainActivity))
    }

    private val bluetoothUtil by lazy {
        BluetoothUtil(this@MainActivity)
    }

    internal class ClientHandler(
        private val activity: MainActivity
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_UPDATE_UI_TOAST -> {
                    activity.createToast(activity, "discovery services completed")
                }

                ACTION_BLUETOOTH_SCANNING -> {
                    activity.runOnUiThread {
                        activity.btnScan.text = "STOP SCAN"
                    }
                }

                ACTION_BLUETOOTH_SCANNING_STOP -> {
                    activity.runOnUiThread {
                        activity.btnScan.text = "START SCAN"
                    }
                }

                ACTION_SENSOR_DATA -> {
                    (msg.obj as String).apply {
                        val xx = split(":")
                        activity.findViewById<TextView>(R.id.tv_data).text =
                            "temp:${xx[0]}℃\nhumi:${xx[1]}%"
                    }

                }
            }
        }
    }

    // private var isScanning = false
    //     set(value) {
    //         field = value
    //         runOnUiThread {
    //             btnScan.text = if (value) "Stop Scan" else "Start Scan"
    //         }
    //     }

    // private var mBluetoothLeService: BluetoothLeService? = null

    private val mBluetoothEnablingResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            // Bluetooth is enabled, good to go
            bluetoothStartScan()
        } else {
            // User dismissed or denied Bluetooth prompt again and again until user enable bluetooth
            promptEnableBluetooth()
        }
    }

    private inner class BluetoothServiceLeConn : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // mBluetoothLeService = (service as BluetoothLeService.InnerBinder).getService()
            serviceMessenger = Messenger(service)
            sendClientMessengerToService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // mBluetoothLeService = null
        }
    }

    private fun sendClientMessengerToService() {
        Message.obtain().apply {
            what = ACTION_CACHE_CLIENT_MESSENGER
            replyTo = clientMessenger
            serviceMessenger!!.send(this)
        }
    }

    // -------------------------------------------------------------------------------------------

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Button 1
        btnScan = findViewById(R.id.btn_test)
        btnScan.setOnClickListener {
            if (!hasRequiredBluetoothPermissions()) {
                requestRelevantRuntimePermissions(
                    ::requestLocationPermission, ::requestBluetoothPermissions
                )
            } else {
                bluetoothStartScan()
            }
        }

        // Button 2
        btnTest2 = findViewById(R.id.btn_test_2)
        btnTest2.setOnClickListener {
            // enableNotify()
        }

        // Button 3
        btnTest3 = findViewById(R.id.btn_test_3)
        btnTest3.setOnClickListener {

        }
    }

    override fun onResume() {
        super.onResume()

        bindService(
            Intent(this@MainActivity, BluetoothLeService::class.java),
            BluetoothServiceLeConn(),
            Context.BIND_AUTO_CREATE
        )

        if (!bluetoothUtil.isBluetoothEnable()) {
            promptEnableBluetooth()
        }
    }

    // private fun Activity.requestRelevantRuntimePermissions() {
    //     if (hasRequiredBluetoothPermissions()) return
    //     when {
    //         Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> requestLocationPermission()
    //         Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> requestBluetoothPermissions()
    //     }
    // }

    private fun requestLocationPermission() {
        DialogUtil.showReqPermissions(DialogUtil.PermissionType.Location, this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {
        DialogUtil.showReqPermissions(DialogUtil.PermissionType.Bluetooth, this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
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
                requestRelevantRuntimePermissions(
                    ::requestLocationPermission, ::requestBluetoothPermissions
                )
            }

            allGranted && hasRequiredBluetoothPermissions() -> {
                // todo core method ...
                bluetoothStartScan()
            }

            else -> {
                // Unexpected scenario encountered when handling permissions
                // recreate()
            }
        }
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

        if (!bluetoothUtil.isBluetoothEnable()) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                mBluetoothEnablingResult.launch(this)
            }
        }
    }

    private fun bluetoothStartScan() {
        Message.obtain().apply {
            what = ACTION_BLE_START_SCAN
            serviceMessenger?.send(this)
        }
    }

    private companion object {
        private const val TAG = "MainActivity"

    }
}