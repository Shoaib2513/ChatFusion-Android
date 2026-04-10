package com.chatfusion.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.chatfusion.app.databinding.ActivityConnectivityBinding

class ConnectivityActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityConnectivityBinding
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        // Unit V: Sensors Initialization
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        // Unit V: Bluetooth Initialization
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        checkBluetoothAndPairedDevices()
    }

    private fun checkBluetoothAndPairedDevices() {
        if (bluetoothAdapter == null) {
            binding.tvBluetoothStatus.text = "Bluetooth not supported on this device"
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            binding.tvBluetoothStatus.text = "Bluetooth is disabled"
            return
        }

        // Check for permissions (required for Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val deviceList = mutableListOf<String>()

        pairedDevices?.forEach { device ->
            deviceList.add("${device.name} (${device.address})")
        }

        if (deviceList.isEmpty()) {
            binding.tvBluetoothStatus.text = "No paired devices found"
        } else {
            binding.tvBluetoothStatus.text = "Found ${deviceList.size} paired devices"
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
            // Using a simple list view logic via the binding (if rv was used, we'd need an adapter, but for brevity using simple text for Unit V demonstration)
            // Re-using the RecyclerView with a simple adapter
            binding.rvPairedDevices.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
            binding.rvPairedDevices.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<DeviceViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DeviceViewHolder {
                    val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                    return DeviceViewHolder(view as android.widget.TextView)
                }
                override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
                    holder.textView.text = deviceList[position]
                }
                override fun getItemCount(): Int = deviceList.size
            }
        }
    }

    private class DeviceViewHolder(val textView: android.widget.TextView) : androidx.recyclerview.widget.RecyclerView.ViewHolder(textView)

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                binding.tvLightSensor.text = "Light Intensity: ${event.values[0]} lx"
            }
            Sensor.TYPE_PROXIMITY -> {
                binding.tvProximitySensor.text = "Proximity: ${event.values[0]} cm"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
