package com.example.EML327Test

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.github.pires.obd.commands.SpeedCommand
import com.github.pires.obd.commands.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.*


class MainActivity : AppCompatActivity() {

    private var deviceStrs: ArrayList<String> = ArrayList<String>()
    private val devices: ArrayList<String> = ArrayList<String>()
    lateinit var deviceAddress: String
    var btAdapter: BluetoothAdapter? = null
    lateinit var socket: BluetoothSocket
    private val mObdResetCommand = ObdResetCommand()
    private val mSpeedCommand = SpeedCommand()

   /* private val initialConfigCommands
        get() = listOf(
            ObdResetCommand(),
            EchoOffCommand(),
            LineFeedOffCommand(),
            TimeoutCommand(42),
            SelectProtocolCommand(ObdProtocols.AUTO),
            AmbientAirTemperatureCommand()
        )*/

   /* private val commandList
        get() = listOf(
            SpeedCommand(),
            RPMCommand(),
            ThrottlePositionCommand(),
            EngineCoolantTemperatureCommand(),
            MassAirFlowCommand()
        )*/

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView: TextView = findViewById(R.id.tvText)
        val btnStart: Button = findViewById(R.id.btnStart)
        val btnStop: Button = findViewById(R.id.btnStop)
        val btnScan: Button = findViewById(R.id.btnScan)

        btAdapter = BluetoothAdapter.getDefaultAdapter()

        //check if bluetooth is available or not
        if (btAdapter == null){
            textView.setText("Bluetooth is not available")
        }
        else {
            textView.setText("Bluetooth is available")
        }

        btnScan.setOnClickListener {
            if (btAdapter != null) {
                Log.d("MyApp", "btAdapter = $btAdapter")
                btAdapter?.startDiscovery()
                val pairedDevices = btAdapter?.bondedDevices
                if (pairedDevices!!.size > 0) {
                    pairedDevices.forEach {
                        deviceStrs.add(it.name)
                        devices.add(it.address)
                    }
                }
                Log.d("MyApp", "pairedDevices = $pairedDevices")

                // show list
                val alertDialog = AlertDialog.Builder(this)

                val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
                        this, android.R.layout.simple_list_item_1,
                        deviceStrs
                )
                alertDialog.setSingleChoiceItems(adapter, -1) { dialog, which ->
                    val position: Int = (dialog as AlertDialog).getListView().getCheckedItemPosition()
                    deviceAddress = devices[which]
                    dialog.dismiss()
                    Log.d("MyApp", "deviceAddress = $deviceAddress")
                    val device: BluetoothDevice = btAdapter!!.getRemoteDevice(deviceAddress)

                    connectSocket(device)
                }

                alertDialog.setTitle("Choose Bluetooth device")
                alertDialog.show()
            } else {
                textView.setText("Turn on the Bluetooth")
            }
        }

        btnStart.setOnClickListener {
            startObdCommandFlow()
            textView.setText(mObdResetCommand.formattedResult + " " + mSpeedCommand.formattedResult)
        }
        btnStop.setOnClickListener {
            socket.close()
        }
    }

    private fun connectSocket(device: BluetoothDevice)  = flow{
        emit(device)
        btAdapter!!.cancelDiscovery()
        try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
            socket.connect()
        } catch (e: Exception) {
            emit(e.message ?: "Failed to connect")
        }
    }.flowOn(Dispatchers.IO)


    fun startObdCommandFlow() = flow {
        try {
           emit(mObdResetCommand.run(socket?.inputStream, socket?.outputStream))

        } catch (e: Exception) {
            e.printStackTrace()
        }
        while (socket?.isConnected == true) { // indefinite loop to keep running commands
            try {
              emit(mSpeedCommand.run(socket?.inputStream, socket?.outputStream)) // blocking call
            // read complete, emit value
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }.flowOn(Dispatchers.IO) // all operations happen on IO thread
}
