package com.example.wearosmap;

import android.Manifest;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Looper;
import android.os.Vibrator;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.wear.widget.SwipeDismissFrameLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;

public class MapsActivity extends WearableActivity implements OnMapReadyCallback, SensorEventListener {

    /**
     * Map is initialized when it's fully loaded and ready to be used.
     *
     * @see #onMapReady(com.google.android.gms.maps.GoogleMap)
     */
    private GoogleMap mMap;
    private LatLng careTakerLocation = new LatLng(0, 0);
    private LatLng currLocationLatLng = new LatLng(0, 0);
    private float requiredRotation;

    private FusedLocationProviderClient locationClient;
    private RequestQueue requestQueue;
    private SoundPool soundPool;
    private int[] sm;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor mangetometer;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private float[] orientations = new float[3];
    private float[] mR = new float[9];
    private boolean accelerometerSet = false;
    private boolean magnetometerSet = false;

    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        // Enables always on.
        setAmbientEnabled();

        setContentView(R.layout.activity_maps);

        final SwipeDismissFrameLayout swipeDismissRootFrameLayout =
                (SwipeDismissFrameLayout) findViewById(R.id.swipe_dismiss_root_container);
        final FrameLayout mapFrameLayout = (FrameLayout) findViewById(R.id.map_container);

        // Enables the Swipe-To-Dismiss Gesture via the root layout (SwipeDismissFrameLayout).
        // Swipe-To-Dismiss is a standard pattern in Wear for closing an app and needs to be
        // manually enabled for any Google Maps Activity. For more information, review our docs:
        // https://developer.android.com/training/wearables/ui/exit.html
        swipeDismissRootFrameLayout.addCallback(new SwipeDismissFrameLayout.Callback() {
            @Override
            public void onDismissed(SwipeDismissFrameLayout layout) {
                // Hides view before exit to avoid stutter.
                layout.setVisibility(View.GONE);
                finish();
            }
        });

        // Adjusts margins to account for the system window insets when they become available.
        swipeDismissRootFrameLayout.setOnApplyWindowInsetsListener(
                new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                        insets = swipeDismissRootFrameLayout.onApplyWindowInsets(insets);

                        FrameLayout.LayoutParams params =
                                (FrameLayout.LayoutParams) mapFrameLayout.getLayoutParams();

                        // Sets Wearable insets to FrameLayout container holding map as margins
                        params.setMargins(
                                insets.getSystemWindowInsetLeft(),
                                insets.getSystemWindowInsetTop(),
                                insets.getSystemWindowInsetRight(),
                                insets.getSystemWindowInsetBottom());
                        mapFrameLayout.setLayoutParams(params);

                        return insets;
                    }
                });

        // Obtain the MapFragment and set the async listener to be notified when the map is ready.
        MapFragment mapFragment =
                (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        this.requestQueue = Volley.newRequestQueue(this);

        loadSounds();

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.mangetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, mangetometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (event.getRepeatCount() == 0) {
            if (keyCode == KeyEvent.KEYCODE_STEM_1) {
                Log.i("MF KEY", "Keycode 1: " + event.toString());

                if((requiredRotation > 0 && requiredRotation < 30) || (requiredRotation > 330)) {
                    Log.i("ROTATION", "Move Forward");
                    //vibrateMoveForward();
                    //playTurnMoveForwardSound();
                }
                else if(requiredRotation > 180) {
                    Log.i("ROTATION", "Turn left");
                    //vibrateLeft();
                    //playTurnLeftSound();
                } else {
                    Log.i("ROTATION", "Turn right");
                    //vibrateRight();
                    //playTurnRightSound();
                }

                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }


    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        long[] vibrationPattern = {0, 500, 500, 500};
        final int indexInPatternToRepeat = -1;
        vibrator.vibrate(vibrationPattern, indexInPatternToRepeat);
    }

    private void loadSounds() {
        soundPool = new SoundPool.Builder().build();
        sm = new int[3];

        soundPool.setOnLoadCompleteListener(
                new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                        Log.i("SOUND", "Status: " + status);
                    }
                }
        );

        sm[0] = soundPool.load(this, R.raw.turnleft, 1);
        sm[1] = soundPool.load(this, R.raw.turnright, 1);
        sm[2] = soundPool.load(this, R.raw.moveforward, 1);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i("APP", "--> onMapReady");

        // Map is ready to be used.
        mMap = googleMap;

        // Inform user how to close app (Swipe-To-Close).
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(getApplicationContext(), R.string.intro_text, duration);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                },
                1
        );

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        LocationRequest lr = new LocationRequest();
        lr.setInterval(1000);
        lr.setFastestInterval(500);
        lr.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mMap.moveCamera(CameraUpdateFactory.zoomTo(10));

        locationClient.getLastLocation()
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.i("APP", "Error getting location: " + e.getMessage());
                    }
                })
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        Log.i("APP", "Got Location! " + location);
                        if(location != null) {
                            mMap.moveCamera(CameraUpdateFactory.newLatLng(
                                    new LatLng(
                                            location.getLatitude(),
                                            location.getLongitude()
                                    )
                            ));
                        }
                    }
                });

        Log.i("APP", "moved zoom camera");

        locationClient.requestLocationUpdates(
                lr,
                new LocationCallback(){
                    @Override
                    public void onLocationResult(LocationResult locationResult) {
                        Log.i("APP", "location call back");

                        if(locationResult == null) {
                            Log.i("APP", "Location not found");
                            return;
                        }

                        Location currLocation = locationResult.getLastLocation();
                        if(currLocation != null) {
                            currLocationLatLng = new LatLng(currLocation.getLatitude(), currLocation.getLongitude());

                            getCaretakerLocation();

                            mMap.clear();
                            mMap.addMarker(
                                    new MarkerOptions()
                                            .position(currLocationLatLng)
                                            .title("Current Location")
                            );
                            mMap.addMarker(
                                    new MarkerOptions()
                                            .position(careTakerLocation)
                                            .title("Caretaker Location")
                                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            );

                            postVipLocation(currLocation.getLatitude(),currLocation.getLongitude(),0);
                        }
                    }
                }, Looper.getMainLooper()
        );

        Log.i("APP", "end");
    }

    private void getCaretakerLocation()
    {
        final String url = "https://edd-dunk-server.appspot.com/caretaker";

        JsonObjectRequest getRequest = new JsonObjectRequest(Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONObject>()
                {
                    @Override
                    public void onResponse(JSONObject response)
                    {
                        Log.i("GET Response", response.toString());
                        try {
                            double latitude = response.getDouble("latitude");
                            double longitude = response.getDouble("longitude");
                            double bearing = response.getDouble("bearing");

                            careTakerLocation = new LatLng(latitude, longitude);

                            Log.i("GET Response", latitude + "");
                            Log.i("GET Response", longitude + "");
                            Log.i("GET Response", bearing + "");
                        } catch (Exception e) {
                            Log.i("Error", e.getMessage());
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i("GET Error.Response", error.toString());
                    }
                }
        );

        requestQueue.add(getRequest);
    }

    private void postVipLocation(double lat, double lon, double bear) {
        final String url = "https://edd-dunk-server.appspot.com/vip";
        JSONObject jsonBody = new JSONObject();

        try {
            jsonBody.put("latitude", lat);
            jsonBody.put("longitude", lon);
            jsonBody.put("bearing", bear);
        } catch (JSONException e) {
            Log.i("Error", e.getMessage());
        }

        Log.i("APP", "creating request");
        JsonObjectRequest postRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                jsonBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // display response
                        Log.i("GET Response", response.toString());
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i("POST Error.Response", error.toString());
                    }
                }
        );

        Log.i("APP", "posting location");

        requestQueue.add(postRequest);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor == accelerometer) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
            accelerometerSet = true;
        } else if(event.sensor == mangetometer) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
            magnetometerSet = true;
        }

        if(!accelerometerSet || !magnetometerSet) {
            return;
        }

        SensorManager.getRotationMatrix(mR, null, lastAccelerometer, lastMagnetometer);
        SensorManager.getOrientation(mR, orientations);

        float azimuthInRadians = orientations[0];
        float azimuthInDegrees = (float) (Math.toDegrees(azimuthInRadians) + 360) % 360;

        Location currentLocation = new Location("");
        currentLocation.setLatitude(currLocationLatLng.latitude);
        currentLocation.setLongitude(currLocationLatLng.longitude);
        currentLocation.setBearing(azimuthInDegrees);

        Log.i("ROTATION", "Current Rotation: " + azimuthInDegrees);

        Location targetLocation = new Location("");
        targetLocation.setLatitude(careTakerLocation.latitude);
        targetLocation.setLongitude(careTakerLocation.longitude);

        Log.i("ROTATION", "Bearing to: " + ((currentLocation.bearingTo(targetLocation) + 360) % 360));

        this.requiredRotation = (360 + ((currentLocation.bearingTo(targetLocation) + 360) % 360) - currentLocation.getBearing()) % 360;

        Log.i("ROTATION", "Required Rotation: " + requiredRotation);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing
    }
}
