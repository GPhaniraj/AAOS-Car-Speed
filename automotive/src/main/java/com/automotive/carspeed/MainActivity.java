package com.automotive.carspeed;

import android.app.AlertDialog;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.VehiclePropertyIds;
import android.car.content.pm.CarPackageManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.car.hardware.property.CarPropertyManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AutomotiveMainActivity";
    private Car mCarService;
    private int carSpeed = 0;
    private final int carSpeedAlert = 60;
    String token;
    private final String[] permissions = new String[]{Car.PERMISSION_SPEED};
    public TextView txtCarSpeed;
    public CarPropertyManager mCarPropertyManager;
    final float KM_MULTIPLIER = 3.59999987F;
    boolean isSpeedNotified = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtCarSpeed = findViewById(R.id.txtCarSpeed);
        txtCarSpeed.setText(getString(R.string.car_speed_text, carSpeed));
        //initializeFCM();
    }

    private void initializeFCM() {
        FirebaseApp.initializeApp(MainActivity.this);
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String fcmToken) {
                token = fcmToken;
                Log.i("FCM TOKEN", token);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startService();
    }

    private void startService() {
        if (checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "startService: Permission for" + permissions[0] + " is GRANTED");

            if (mCarService == null) {
                Log.d(TAG, "EstablishCarServiceConnection:  mCarService is NULL");
                carServiceInitialization();
                return;
            }

            if (!mCarService.isConnected() && !mCarService.isConnecting()) {
                mCarService.connect();
            } else {
                carServiceInitialization();
            }
        } else {
            Log.d(TAG, "startService: Permission for " + permissions[0] + " is NOT GRANTED");
            requestPermissions(permissions, 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (permissions[0].equals(Car.PERMISSION_SPEED) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onRequestPermissionsResult: Permission for" + permissions[0] + " GRANTED");
            startService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mCarService == null) {
            Log.d(TAG, "onPause:  mCarService is NULL");
            return;
        }

        if (mCarService.isConnected()) {
            Log.d(TAG, "onPause: CAR_Disconnecting Car");
            mCarService.disconnect();
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG, "onServiceConnected: CAR_Connected");
            registerCarSpeedPropertyCallback();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected: CAR_Disconnected");
        }
    };

    private void registerCarSpeedPropertyCallback() {
        Log.d(TAG, "PERF_VEHICLE_SPEED: registerCarSpeedPropertyCallback");

        mCarPropertyManager.registerCallback(new CarPropertyManager.CarPropertyEventCallback() {
            @Override
            public void onChangeEvent(CarPropertyValue carPropertyValue) {
                Log.d(TAG, "PERF_VEHICLE_SPEED: onChangeEvent(" + carPropertyValue.getValue() + ")");
                carSpeed = (int) (Math.round(Float.parseFloat(carPropertyValue.getValue().toString())) * KM_MULTIPLIER);
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        String dumpCarSpeed = getString(R.string.car_speed_text, carSpeed);
                        txtCarSpeed.setText(dumpCarSpeed);
                    }
                });
                if (carSpeed > carSpeedAlert && !isSpeedNotified) {
                    Log.d(TAG, "SEND FCM ALERT");
                    //SendPushNotification.pushNotification(token);
                    isSpeedNotified = true;
                    Toast.makeText(getApplicationContext(), getString(R.string.car_speed_text, carSpeed), Toast.LENGTH_LONG).show();
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle("Car Speed Alert");
                    alertDialog.setMessage("Dear customer, Please avoid over speed");
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    isSpeedNotified = !isSpeedNotified;
                                }
                            });
                    alertDialog.show();
                }
            }

            @Override
            public void onErrorEvent(int propId, int zone) {
                Log.d(TAG, "PERF_VEHICLE_SPEED: onErrorEvent(" + propId + ", " + zone + ")");
            }
        }, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL);

    }


    private void carServiceInitialization() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.w(TAG, "CarServiceInitialization: FEATURE_AUTOMOTIVE not available");
            return;
        }

        if (mCarService == null) {
            Log.d(TAG, "CarServiceInitialization:  mCarService NULL");
            mCarService = Car.createCar(this);
            mCarPropertyManager = (CarPropertyManager) mCarService.getCarManager(Car.PROPERTY_SERVICE);
            registerCarSpeedPropertyCallback();
        } else {
            Log.d(TAG, "CarServiceInitialization: mCarService CREATED");
        }
    }
}