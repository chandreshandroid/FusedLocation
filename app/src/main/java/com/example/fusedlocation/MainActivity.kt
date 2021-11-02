package com.example.fusedlocation

import android.location.Location
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var locationObserver: LocationObserver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //initialize location Observer it's must be in onCreate method
        locationObserver = LocationObserver(this)
        lifecycle.addObserver(locationObserver!!)

        findViewById<Button>(R.id.startLocationBtn).setOnClickListener {
            locationObserver?.connect(
                object : LocationObserver.CurrentLocationCallback {
                    override fun handleNewLocation(location: Location) {
                        findViewById<TextView>(R.id.output).text = location.toString()
                        //Stop location update
                        locationObserver?.disconnect()
                    }

                    override fun deniedLocationPermission(errorCode: Int) {
                        if (errorCode == LocationObserver.DENIED_LOCATION_ENABLE) {
                            Toast.makeText(
                                this@MainActivity,
                                "Denied For Location Service Enable",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                "Denied For Access Location",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                },
            )
        }

        findViewById<Button>(R.id.stopLocationBtn).setOnClickListener {
            locationObserver?.disconnect()
        }

    }
}