package com.mindorks.ridesharing.ui.maps

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mindorks.ridesharing.R
import com.mindorks.ridesharing.data.network.NetworkService
import com.mindorks.ridesharing.utils.AnimationUtils
import com.mindorks.ridesharing.utils.MapUtils
import com.mindorks.ridesharing.utils.PermissionUtils
import com.mindorks.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(), MapsView, OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }

    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private val nearByCabMarkerList = arrayListOf<Marker>()
    private var dropLatLng: LatLng? = null
    private var pickUpLatLng: LatLng? = null
    private var greyPolyLine: Polyline? = null
    private var blackPolyLine: Polyline? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var movingCabMarker: Marker? = null
    private var previousLatLngFromServer: LatLng? = null
    private var currentLatLngFromServer: LatLng? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        setUpClickListener()
    }


    private fun setUpClickListener() {
        pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }

        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener {
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!, dropLatLng!!)
        }

        nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent =
            Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun moveCamera(latLng: LatLng?) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animationCamera(latLng: LatLng?) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng): Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng) : Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitman())
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))

    }

    private fun enableMyLocationOnMap() {
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
        googleMap.isMyLocationEnabled = true
    }

    private fun setCurrentLocationAsPickUp() {
        pickUpLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    private fun setUpLocationListener() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        // for getting the current location update, after every 2 sec
        val locationRequest = LocationRequest().setInterval(2000).setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResut: LocationResult) {
                super.onLocationResult(locationResut)
                if (currentLatLng == null) {
                    for (location in locationResut.locations) {
                        if (currentLatLng == null) {
                            currentLatLng = LatLng(location.latitude, location.longitude)
                            setCurrentLocationAsPickUp()
                            enableMyLocationOnMap()
                            moveCamera(currentLatLng)
                            animationCamera(currentLatLng)
                            presenter.requestNearbyCabs(currentLatLng!!)
                        }
                    }
                }

                // updating the user's location on server every 2 sec using gps
            }
        }

        fusedLocationProviderClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private  fun checkAndShowRequestButton() {
        if(pickUpLatLng != null && dropLatLng != null) {
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }

    private fun reset() {
        statusTextView.visibility = View.GONE
        nextRideButton.visibility = View.GONE
        nearByCabMarkerList.forEach {
            it.remove()
        }
        nearByCabMarkerList.clear()
        currentLatLngFromServer = null
        previousLatLngFromServer = null
        if(currentLatLng != null) {
            moveCamera(currentLatLng)
            animationCamera(currentLatLng)
            setCurrentLocationAsPickUp()
            presenter.requestNearbyCabs(currentLatLng!!)
        } else {
            pickUpTextView.text = ""
        }

        pickUpTextView.isEnabled = true
        dropTextView.isEnabled = true
        dropTextView.text = ""
        movingCabMarker?.remove()
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()

        dropLatLng = null
        greyPolyLine = null
        blackPolyLine = null
        originMarker = null
        destinationMarker = null
        movingCabMarker = null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        when {
            PermissionUtils.isAccessFineLocationGranted(this) -> {
                when {
                    PermissionUtils.isLocationEnabled(this) -> {
                        // permission granted, ready to fetch the location
                        setUpLocationListener()
                    }
                    else -> {
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                }
            }
            else -> {
                PermissionUtils.requestAccessFineLocationPermisson(
                    this,
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            // ready to fetch the location
                            setUpLocationListener()
                        }
                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    } else {
                    Toast.makeText(this, "Location Permission not granted", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when(resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    when(requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    // error
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }

                Activity.RESULT_CANCELED -> {
                    // cancelled

                }
            }
        }
    }


    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun showNearbyCabs(latLngList: List<LatLng>) {
        nearByCabMarkerList.clear()
        for (latLng in latLngList) {
            val nearbyCabMarker = addCarMarkerAndGet(latLng)
            nearByCabMarkerList.add(nearbyCabMarker)
        }
    }

    override fun informCabBooked() {
        nearByCabMarkerList.forEach{
            // remove the nearby cars
            it.remove()
        }
        nearByCabMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latlngList: List<LatLng>) {
        Log.e("TAG", "showPath")
        val builder = LatLngBounds.builder()
        for (latLng in latlngList) {
            builder.include(latLng)
        }
        val bound = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bound, 2))
        val polyLineOptions = PolylineOptions()
        polyLineOptions.color(Color.GRAY)
        polyLineOptions.width(5f)
        polyLineOptions.addAll(latlngList)

        greyPolyLine = googleMap.addPolyline(polyLineOptions)
        val blackPolyLineOptions = PolylineOptions()
        polyLineOptions.run {
            color(Color.GRAY)
            width(5f)
        }
        blackPolyLine = googleMap.addPolyline(blackPolyLineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latlngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latlngList[latlngList.size-1])
        destinationMarker?.setAnchor(0.5f, 0.5f)

        val polyLineAnimator = AnimationUtils.polyLineAnimator()
        polyLineAnimator.addUpdateListener {
            val percentValue = (it.animatedValue as Int)
            val index = (greyPolyLine?.points?.size)!! * (percentValue/100.0f).toInt()
            blackPolyLine?.points = greyPolyLine?.points!!.subList(0, index)
        }
        polyLineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        if(movingCabMarker == null) {
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        if(previousLatLngFromServer == null) {
            currentLatLngFromServer = latLng
            previousLatLngFromServer = currentLatLngFromServer
            movingCabMarker?.position = currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f, 0.5f)
            animationCamera(currentLatLngFromServer)
        }else {
            previousLatLngFromServer = currentLatLngFromServer
            currentLatLngFromServer = latLng
            val valueAnimator = AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener { va ->
                if(currentLatLngFromServer != null && previousLatLngFromServer != null) {
                    val multiplier = va.animatedFraction
                    val nextLocation = LatLng(
                        multiplier * currentLatLngFromServer!!.latitude + ( 1 - multiplier) * previousLatLngFromServer!!.latitude,
                        multiplier * currentLatLngFromServer!!.longitude + ( 1 - multiplier) * previousLatLngFromServer!!.longitude
                    )
                    movingCabMarker?. position = nextLocation

                    val rotation = MapUtils.getRotation(previousLatLngFromServer!!, nextLocation)
                    if(!rotation.isNaN()) {
                        movingCabMarker?.rotation = rotation
                    }
                    movingCabMarker?.setAnchor(0.5f, 0.5f)
                    animationCamera(nextLocation)
                }

            }
            valueAnimator.start()
        }

    }

    override fun informCabIsArriving() {
        statusTextView.text = getString(R.string.your_cab_is_arriving)
    }

    override fun informCabArrived() {
        statusTextView.text = getString(R.string.your_cab_has_arrived)
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        statusTextView.text = getString(R.string.you_are_on_a_trip)
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        statusTextView.text = getString(R.string.trip_end)
        nextRideButton.visibility = View.VISIBLE
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
        val error = getString(R.string.route_not_available_choose_different_locations)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }
}