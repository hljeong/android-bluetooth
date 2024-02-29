package com.example.bluetoothcommunication

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.icu.util.Output
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.example.bluetoothcommunication.ui.theme.BluetoothCommunicationTheme
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


/**
 * [A]
 *
 * private val enableBtWithPermissionLauncher =
 *     registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
 *         toast(
 *             if (result.resultCode == RESULT_OK) "Bluetooth turned on"
 *             else "Bluetooth failed to turn on"
 *         )
 *     }
 * private fun enableBtWithPermission() {
 *     enableBtWithPermissionLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
 * }
 *
 */

/**
 * [B]
 *
 * private val requestPermissionAndEnableBtLauncher =
 *     registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
 *         if (isGranted) enableBtWithPermission()
 *         else toast("Bluetooth connect permission request failed")
 *     }
 * @RequiresApi(Build.VERSION_CODES.S)
 * private fun requestPermissionAndEnableBt() {
 *     requestPermissionAndEnableBtLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
 * }
 *
 */

const val UUID: String = "bc44c38c-886a-4559-882e-1e4f533d7112"

const val MESSAGE_READ: Int = 0
const val MESSAGE_WRITE: Int = 1


@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothIOHandler: Handler = Handler(mainLooper) {
        when (it.what) {
            MESSAGE_READ -> {
                val data = it.obj as ByteArray
                dialog("Read $data")
                true
            }

            MESSAGE_WRITE -> {
                true
            }

            else -> {
                false
            }
        }
    }
    
    // https://stackoverflow.com/a/67582633
    // [A] is shortened to below with a utility builder function
    private val enableBtWithPermission = buildActivity(
        activity = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        toast(
            if (it.resultCode == RESULT_OK) "Bluetooth turned on"
            else "Bluetooth failed to turn on "
        )
    }

    // https://developer.android.com/training/permissions/requesting
    // [B] is shortened to below with a utility builder function
    private val requestPermissionAndEnableBt = buildActivity(
        activity = Manifest.permission.BLUETOOTH_CONNECT,
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        if (it) enableBtWithPermission()
        else toast("Bluetooth connect permission request failed")
    }

    private val requestPermissionToScan = buildActivity(
        activity = Manifest.permission.BLUETOOTH_SCAN,
        contract = ActivityResultContracts.RequestPermission(),
    ) {}

    private val requestPermissionToAccessCoarseLocation = buildActivity(
        activity = Manifest.permission.ACCESS_COARSE_LOCATION,
        contract = ActivityResultContracts.RequestPermission(),
    ) {}

    private val requestPermissionToAccessFineLocation = buildActivity(
        activity = Manifest.permission.ACCESS_FINE_LOCATION,
        contract = ActivityResultContracts.RequestPermission(),
    ) {}

    private val viewModel: BluetoothScreenViewModel = BluetoothScreenViewModel()

    @SuppressLint("MissingPermission")
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> toast("Discovery started")

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> toast("Discovery finished")

                // Device discovered
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        )!!
                    val name: String? = device.name
                    val address = device.address
                    viewModel.addDiscoveredDevice("$name-$address")
                    // dialog("Discovered $name: $address")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
        }
        registerReceiver(broadcastReceiver, filter)

        setContent {
            BluetoothCommunicationTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BluetoothScreen(
                        viewModel = viewModel,
                        bluetoothOn = { bluetoothOn() },
                        discover = { discover() },
                        connect = { address -> connect(address) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(broadcastReceiver)
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothOn() {
        if (!bluetoothAdapter?.isEnabled!!) {

            checkPermissionAndDo(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                rationale = "Need bluetooth connect permissions",
                action = enableBtWithPermission,
                requestPermissionAndAction = requestPermissionAndEnableBt,
            )

        } else {
            toast("Bluetooth is already on")
        }
    }

    @SuppressLint("MissingPermission")
    private fun discover() {
        try {
            checkPermissionAndDo(
                permission = Manifest.permission.BLUETOOTH_SCAN,
                rationale = "Need bluetooth scan permissions",
                action = {},
                requestPermissionAndAction = requestPermissionToScan,
            )

            checkPermissionAndDo(
                permission = Manifest.permission.ACCESS_COARSE_LOCATION,
                rationale = "Need location permissions for discovery",
                action = {},
                requestPermissionAndAction = requestPermissionToAccessCoarseLocation,
            )

            checkPermissionAndDo(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                rationale = "Need location permissions for discovery",
                action = {},
                requestPermissionAndAction = requestPermissionToAccessFineLocation,
            )

            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()

            } else if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter.startDiscovery()

            } else {
                toast("Bluetooth is not on")
            }
        } catch (e: Exception) {
            dialog(e.message!!)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showPaired() {
        // https://developer.android.com/develop/connectivity/bluetooth/find-bluetooth-devices
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
        pairedDevices.forEach {
            val name = it.name
            val address = it.address

            dialog("Already paired: $name: $address")
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ListenThread : Thread() {
        private val bluetoothServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord("Bluetooth Server Socket", java.util.UUID.fromString(UUID))
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val bluetoothSocket: BluetoothSocket? = try {
                    bluetoothServerSocket?.accept()
                } catch (e: IOException) {
                    runOnUiThread { dialog(e.message!!, "Error") }
                    shouldLoop = false
                    null
                }

                bluetoothSocket?.also { socket ->
                    ConnectedThread(socket).let {
                        it.start()

                        // do stuff...
                    }

                    bluetoothServerSocket?.close()
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                bluetoothServerSocket?.close()
            } catch (e: IOException) {
                runOnUiThread { dialog(e.message!!, "Error") }
            }
        }
    }

    private fun listen() {
        if (bluetoothAdapter?.isEnabled != true) {
            toast("Bluetooth is not on")
            return
        }

        ListenThread().start()
    }

    // https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices#connect-client
    @SuppressLint("MissingPermission")
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val bluetoothSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(java.util.UUID.fromString(UUID))
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            bluetoothSocket?.let {socket ->
                try {
                    socket.connect()

                    ConnectedThread(socket).let {
                        it.start()

                        // do stuff...
                        // it.write(ByteArray(1024))
                    }
                } catch (e: Exception) {
                    runOnUiThread { dialog(e.message!!, "Error") }
                }
            }
        }

        fun cancel() {
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                runOnUiThread { dialog(e.message!!, "Error") }
            }
        }
    }

    // https://developer.android.com/develop/connectivity/bluetooth/transfer-data
    private inner class ConnectedThread(private val bluetoothSocket: BluetoothSocket) : Thread() {

        private val inputStream: InputStream = bluetoothSocket.inputStream
        private val outputStream: OutputStream = bluetoothSocket.outputStream
        private val buffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int

            while (true) {
                numBytes = try {
                    inputStream.read(buffer)
                } catch (e: IOException) {
                    runOnUiThread { dialog(e.message!!, "Error") }
                    break
                }

                val readMsg = bluetoothIOHandler.obtainMessage(MESSAGE_READ, numBytes, -1, buffer)
                readMsg.sendToTarget()
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                runOnUiThread { dialog(e.message!!, "Error") }
                return
            }

            val writtenMsg = bluetoothIOHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
            writtenMsg.sendToTarget()
        }

        fun cancel() {
            try {
                bluetoothSocket.close()
            } catch (e: IOException) {
                runOnUiThread { dialog(e.message!!, "Error") }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(address: String) {
        if (bluetoothAdapter?.isEnabled != true) {
            toast("Bluetooth is not on")
            return
        }

        ConnectThread(bluetoothAdapter.getRemoteDevice(address)).start()
    }

    // Utility

    private fun havePermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun needRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            permission
        )
    }

    private fun <I, O> buildActivity(
        activity: I,
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>,
    ): () -> Unit {
        val launcher = registerForActivityResult(contract, callback)
        return { launcher.launch(activity) }
    }

    // https://developer.android.com/training/permissions/requesting
    private fun checkPermissionAndDo(
        permission: String,
        rationale: String,
        action: () -> Unit,
        requestPermissionAndAction: () -> Unit,
    ) {
        when {
            havePermission(permission) -> action()
            needRationale(permission)-> toast(rationale)
            else -> requestPermissionAndAction()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    private fun dialog(message: String, title: String = "Dialog") {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setMessage(message).setTitle(title)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}


class BluetoothScreenViewModel : ViewModel() {
    val discoveredDevices = listOf<String>().toMutableStateList()

    fun addDiscoveredDevice(device: String) {
        discoveredDevices.add(device)
    }

    fun clear() {
        discoveredDevices.clear()
    }
}

@Composable
fun BluetoothScreen(
    viewModel: BluetoothScreenViewModel,
    bluetoothOn: () -> Unit,
    discover: () -> Unit,
    connect: (String) -> Unit,
) {

    Column {
        Button(
           onClick = bluetoothOn
        ) {
           Text(text = "Bluetooth On")
        }

        Button(
            onClick = discover
        ) {
            Text(text = "Discover")
        }

        Button(
            onClick = { viewModel.clear() }
        ) {
            Text(text = "Clear")
        }

        LazyColumn {
            items(viewModel.discoveredDevices) {
                Button(
                    onClick = { connect(it.split("-")[1]) }
                ) {
                    Text(it)
                }
            }
        }
   }
}

@Preview(showBackground = true)
@Composable
fun BluetoothScreenPreview() {
    BluetoothCommunicationTheme {
        BluetoothScreen(
            viewModel = BluetoothScreenViewModel(),
            bluetoothOn = {},
            discover = {},
            connect = {},
        )
    }
}