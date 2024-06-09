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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sample.bluetooth.ble.BluetoothUtil
import com.sample.bluetooth.ui.DialogUtil
import com.sample.bluetooth.util.ACTION_BLE_START_SCAN
import com.sample.bluetooth.util.ACTION_CACHE_CLIENT_MESSENGER
import com.sample.bluetooth.util.ACTION_UPDATE_UI_TOAST
import com.sample.bluetooth.util.createToast
import com.sample.bluetooth.util.hasPermission
import com.sample.bluetooth.util.hasRequiredBluetoothPermissions


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

    private val mPromptDialog: DialogUtil by lazy {
        DialogUtil(this@MainActivity)
    }

    internal class ClientHandler(
        private val activity: MainActivity
    ) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                ACTION_UPDATE_UI_TOAST -> {
                    activity.createToast(activity, "discovery services completed")
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

            // todo ....
            // Bluetooth is enabled, good to go
            bluetoothStartScan()

        } else {
            // User dismissed or denied Bluetooth prompt
            // again and again until user enable bluetooth
            promptEnableBluetooth()
        }
    }

    inner class BluetoothServiceLeConn : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // mBluetoothLeService = (service as BluetoothLeService.InnerBinder).getService()
            serviceMessenger = Messenger(service)

            Message.obtain().apply {
                what = ACTION_CACHE_CLIENT_MESSENGER
                replyTo = clientMessenger
                serviceMessenger!!.send(this)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // mBluetoothLeService = null
        }
    }

    // -------------------------------------------------------------------------------------------

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
            if (!hasRequiredBluetoothPermissions()) {
                requestRelevantRuntimePermissions()
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

        if (!bluetoothUtil.isBluetoothEnable())
            promptEnableBluetooth()
    }

    // -------------------------------------------------------------------------------------------
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredBluetoothPermissions())
            return

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> requestLocationPermission()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> requestBluetoothPermissions()
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
                requestRelevantRuntimePermissions()
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

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
}