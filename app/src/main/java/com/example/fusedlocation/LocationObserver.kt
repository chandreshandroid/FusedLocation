package com.example.fusedlocation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

/**
 * @Author: Chandresh Patel
 * @Date: 01-11-2021
 */
class LocationObserver(private val mActivity: AppCompatActivity) : DefaultLifecycleObserver {

    private var currentLocationCallback: CurrentLocationCallback? = null


    private lateinit var mLocationCallback: LocationCallback

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Provides access to the Location Settings API.
     */
    private lateinit var mSettingsClient: SettingsClient

    /**
     * Stores parameters for requests to the FusedLocationProviderApi.
     */
    private var mLocationRequest = LocationRequest.create()

    /**
     * Stores the types of location services the client is interested in using. Used for checking
     * settings to determine if the device has optimal location settings.
     */
    private lateinit var mLocationSettingsRequest: LocationSettingsRequest

    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>

    private lateinit var locationEnableDialogRequest: ActivityResultLauncher<IntentSenderRequest>


    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        registerLocationPermissionObject(owner)
        registerLocationEnableObject(owner)
        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mActivity)
        mSettingsClient = LocationServices.getSettingsClient(mActivity)
    }

    /**
     * Registering a callback for an location permission result
     * It's called when location disable and we ask for enable.
     */
    private fun registerLocationEnableObject(owner: LifecycleOwner) {
        locationEnableDialogRequest = mActivity.activityResultRegistry.register(
            "locationEnableDialog", owner, ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startLocationUpdates()
            } else {
                currentLocationCallback?.deniedLocationPermission(DENIED_LOCATION_ENABLE)
            }
        }
    }

    /**
     * Registering a callback for an location permission result
     * It's called when user allow to access location.
     */
    private fun registerLocationPermissionObject(owner: LifecycleOwner) {
        locationPermissionRequest = mActivity.activityResultRegistry.register(
            "location", owner,
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                    // Precise location access granted.
                    startLocationUpdates()
                }
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                    // Only approximate location access granted.
                    startLocationUpdates()
                }
                else -> {
                    // No location access granted.
                    currentLocationCallback?.deniedLocationPermission(DENIED_LOCATION_PERMISSION)
                }
            }
        }

    }

    /**
     * Location callback get last location
     */

    private fun createLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                currentLocationCallback?.handleNewLocation(locationResult.lastLocation)

            }
        }
    }

    /**
     * Uses a [LocationSettingsRequest.Builder] to build
     * a [LocationSettingsRequest] that is used for checking
     * if a device has the needed location settings.
     */
    private fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        mLocationSettingsRequest = builder.build()
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     *
     *
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     *
     *
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private fun createLocationRequest(
        locationPriority: Int = HIGH_ACCURACY,
        interval: Long = 10000,
        fastestInterval: Long = 5000,
        isWaitForAccurateLocation: Boolean = true
    ) {
        mLocationRequest = LocationRequest.create().apply {
            this.interval = interval
            this.fastestInterval = fastestInterval
            priority = when (locationPriority) {
                NO_POWER -> LocationRequest.PRIORITY_NO_POWER
                LOW_POWER -> LocationRequest.PRIORITY_LOW_POWER
                BALANCED_POWER_ACCURACY -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
                HIGH_ACCURACY -> LocationRequest.PRIORITY_HIGH_ACCURACY
                else -> LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            this.isWaitForAccurateLocation = isWaitForAccurateLocation
        }
    }


    /**
     * get Current Location update
     */
    fun connect(currentLocationCallback: CurrentLocationCallback) {
        this.currentLocationCallback = currentLocationCallback
        startLocationUpdates()
    }


    /**
     * Stop Location update
     */
    fun disconnect() {
        stopLocationUpdates()
    }


    /**
     * Requests location updates from the FusedLocationApi. Note: we don't call this unless location
     * runtime permission has been granted.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        //Check Permission
        if (hasLocationPermission()) {
            // Begin by checking if the device has the necessary location settings.
            mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener {
                    Log.i(TAG, "All location settings are satisfied.")
                    mFusedLocationClient.requestLocationUpdates(
                        mLocationRequest,
                        mLocationCallback, Looper.myLooper()!!,
                    )
                }
                .addOnFailureListener {
                    when ((it as ApiException).statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                            Log.i(
                                TAG,
                                "Location settings are not satisfied. Attempting to upgrade " + "location settings "
                            )
                            try {
                                // Show the dialog by calling startResolutionForResult(), and check the
                                // result in onActivityResult().
                                val rae = it as ResolvableApiException
                                locationEnableDialogRequest.launch(
                                    IntentSenderRequest.Builder(rae.resolution).build()
                                )
                            } catch (sie: IntentSender.SendIntentException) {
                                Log.i(TAG, "PendingIntent unable to execute request.")
                            }

                        }
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                            val errorMessage =
                                "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                            Log.e(TAG, errorMessage)
                            Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show()
                        }

                    }
                }
        } else {
            askLocationPermission()
        }

    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private fun stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback)
    }

    private fun askLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }


    /**
     * Check the Location has already permission or not
     */
    private fun hasLocationPermission(): Boolean {
        val arrPermissionName = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        for (i in arrPermissionName.indices) {
            if (checkSelfPermission(
                    mActivity,
                    arrPermissionName[i]
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Callback for Location events.
     */
    interface CurrentLocationCallback {
        fun handleNewLocation(location: Location)
        /**
         * Error Code
         * 0 for denied to access location means permission
         * 1 for denied to enable location service
         */
        fun deniedLocationPermission(errorCode : Int)


    }

    companion object {

        val TAG = LocationObserver::class.java.simpleName.toString()
        const val DENIED_LOCATION_PERMISSION = 0
        const val DENIED_LOCATION_ENABLE = 1
        /**
         * Location Priority
         */
        const val NO_POWER = 0
        const val LOW_POWER = 1
        const val BALANCED_POWER_ACCURACY = 2
        const val HIGH_ACCURACY = 3

    }


}
