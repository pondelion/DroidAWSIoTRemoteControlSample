package com.example.awsiotdataloggingjavatest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;

public class MainActivity extends AppCompatActivity implements LocationListener {

    public static final String TAG = "AWSIoTRemoteControlService";
    private AWSIotMqttManager mMQTTManager;
    private Button pubBtn = null;
    private Button subBtn = null;
    private EditText pubText = null;
    private EditText topicName = null;
    private TextView subText = null;
    private ImageView cameraImageView = null;
    private SurfaceView cameraSurfaceView = null;
    private FrameLayout cameraFrameLayout = null;
    private boolean mLoggingRunning = true;
    private final int REQUEST_PERMISSION = 1000;
    private final static int RESULT_CAMERA = 1001;
    LocationManager mLocationManager;
    private Camera mCamera;
    private CameraPreview mPreview;

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            cameraImageView.setImageBitmap(bitmap);

            mCamera.startPreview();
        }

        public static final int MEDIA_TYPE_IMAGE = 1;
        public static final int MEDIA_TYPE_VIDEO = 2;

        /** Create a file Uri for saving an image or video */
        private Uri getOutputMediaFileUri(int type){
            return Uri.fromFile(getOutputMediaFile(type));
        }

        /** Create a File for saving an image or video */
        private File getOutputMediaFile(int type){
            // To be safe, you should check that the SDCard is mounted
            // using Environment.getExternalStorageState() before doing this.

//            File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
//                    Environment.DIRECTORY_PICTURES), "MyCameraApp");
            File mediaStorageDir = getFilesDir();
            // This location works best if you want the created images to be shared
            // between applications and persist after your app has been uninstalled.

            // Create the storage directory if it does not exist
            if (! mediaStorageDir.exists()){
                Log.d("Camera", "dir " + mediaStorageDir.getAbsolutePath());
                if (! mediaStorageDir.mkdirs()){
                    Log.d("MyCameraApp", "failed to create directory");
                    return null;
                }
            }

            // Create a media file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File mediaFile;
            if (type == MEDIA_TYPE_IMAGE){
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        "IMG_"+ timeStamp + ".jpg");
                Log.d("Camera", "Saved to " + mediaFile.getAbsolutePath());
            } else if(type == MEDIA_TYPE_VIDEO) {
                mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                        "VID_"+ timeStamp + ".mp4");
            } else {
                return null;
            }

            return mediaFile;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pubBtn = findViewById(R.id.button2);
        subBtn = findViewById(R.id.buttonSub);
        pubText = findViewById(R.id.editTextPubMsg);
        subText = findViewById(R.id.textViewSub);
        topicName = findViewById(R.id.editTextTopicName);
        cameraImageView = findViewById(R.id.cameraImageView);
        cameraFrameLayout = findViewById(R.id.cameraFrameLayout);
        pubText.setText("{'val1': 'asdjhds}");

        pubBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
//                publish(
//                        pubText.getText().toString(),
//                        topicName.getText().toString()
//                );
                HashMap data = new HashMap();
                data.put("val1", pubText.getText().toString());
                data.put("val2", 42);
                publishJson(
                        data,
                        topicName.getText().toString()
                );
            }
        });
        subBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                subscribe(topicName.getText().toString());
            }
        });

        this.mMQTTManager = new AWSIotMqttManager(AWSSettings.AWS_IOT_THING_NAME, AWSSettings.AWS_IOT_ENDPOINT);
        startRemoteControlService();

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,},
                    1000);
        } else {
            locationStart();
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    1000, 1, this);
        }

        // Create an instance of Camera
        mCamera = getCameraInstance();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.cameraFrameLayout);
        preview.addView(mPreview);
    }

    private void locationStart(){
        Log.d("debug","locationStart()");

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (mLocationManager != null && mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.d("debug", "location manager Enabled");
        } else {
            Intent settingsIntent =
                    new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(settingsIntent);
            Log.d("debug", "not gpsEnable, startActivity");
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,}, 1000);
            Log.d("debug", "checkSelfPermission false");
            return;
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000, 1, this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1000) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationStart();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("log","onActivityResult 1");

        if (requestCode == RESULT_CAMERA) {
            Bitmap bitmap;
            if (data.getExtras() == null) {
                Log.d("debug", "cancel ?");
                return;
            } else {
                bitmap = (Bitmap) data.getExtras().get("data");
                if (bitmap != null) {
                    int bmpWidth = bitmap.getWidth();
                    int bmpHeight = bitmap.getHeight();
                    Log.d("debug", String.format("w= %d", bmpWidth));
                    Log.d("debug", String.format("h= %d", bmpHeight));
                }
            }

            cameraImageView.setImageBitmap(bitmap);
        }
    }

    public void startRemoteControlService() {
        String keyStorePath = this.getFilesDir().getAbsolutePath();
        boolean isPresent = AWSIotKeystoreHelper.isKeystorePresent(keyStorePath, AWSSettings.KEY_STORE_NAME);
        if (!isPresent) {
            try {
                saveCertificateAndPrivateKey(keyStorePath);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }
        KeyStore keyStore = AWSIotKeystoreHelper.getIotKeystore(
                AWSSettings.CERT_ID,
                keyStorePath,
                AWSSettings.KEY_STORE_NAME,
                AWSSettings.KEY_STORE_PASSWORD
        );
        this.mMQTTManager.connect(keyStore, new AWSIotMqttClientStatusCallback() {
            @Override
            public void onStatusChanged(AWSIotMqttClientStatus status, Throwable throwable) {
                Log.d(TAG, "AWSIotMqttClientStatusCallback#onStatusChanged : " + status.toString());
                if (status == AWSIotMqttClientStatusCallback.AWSIotMqttClientStatus.Connected) {
                    subscribe(AWSSettings.AWS_IOT_TOPIC_NAME);
                }
            }
        });
    }

    private void saveCertificateAndPrivateKey(String keyStorePath) throws Exception {
        final String certFile = AWSSettings.CERT_FILE;
//        final String certStr = readFile(certFile);
        final String certStr = readAssetFile(certFile);
        Log.d(TAG, "certStr : " + certStr);
        final String privKeyFile = AWSSettings.PRIVATE_KEY_FILE;
//        final String privKeyStr = readFile(privKeyFile);
        final String privKeyStr = readAssetFile(privKeyFile);
        Log.d(TAG, "privKeyStr : " + privKeyStr);
        AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                AWSSettings.CERT_ID,
                certStr,
                privKeyStr,
                keyStorePath,
                AWSSettings.KEY_STORE_NAME,
                AWSSettings.KEY_STORE_PASSWORD
        );
    }

    private void subscribe(String topic) {
        if (this.mMQTTManager == null) {
            return;
        }
        this.mMQTTManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0, new AWSIotMqttNewMessageCallback() {
            @Override
            public void onMessageArrived(String topic, byte[] data) {
                String text = (String)subText.getText();
                final String msgStr = new String(data, Charset.forName("UTF-8"));
                Log.d(TAG, "Received message : " + msgStr);
                text += "\n" + msgStr;
                JSONObject msgJson;
                try {
                    msgJson = new JSONObject(msgStr);
                    //Log.d(TAG, "msgJson.meeesgae : " + msgJson.getString("message"));
                    if (msgJson.has("cmd")) {
                        text += "\n" + msgJson.getString("cmd");
                        if ("TAKE_PICTURE".equals(msgJson.getString("cmd"))) {
                            Log.d(TAG, "starting camera");
                            //startCamera();
                            mCamera.takePicture(null, null, mPicture);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if ("START_LOGGING".equals(msgStr)) {
                    Toast.makeText(MainActivity.this, "START_LOGGING", Toast.LENGTH_LONG);
                    mLoggingRunning = true;
                } else if ("STOP_LOGGING".equals(msgStr)) {
                    Toast.makeText(MainActivity.this, "STOP_LOGGING", Toast.LENGTH_LONG);
                    mLoggingRunning = false;
                }
                subText.setText(text);
            }
        });
    }

    private void publish(String msg, String topic) {
        if (this.mMQTTManager == null) {
            return;
        }
        this.mMQTTManager.publishString(msg, topic, AWSIotMqttQos.QOS0);
    }

    private void publishJson(HashMap data, String topic) {
        if (this.mMQTTManager == null) {
            return;
        }
        JSONObject json = new JSONObject(data);
        this.mMQTTManager.publishData(json.toString().getBytes(), topic, AWSIotMqttQos.QOS0);
    }

    private String readFile(String filepath) throws IOException {
        return Files.lines(Paths.get(filepath))
                .reduce("", (prev, line) ->
                        prev + line + System.getProperty("line.separator"));
    }

    private String readAssetFile(String fileName) throws IOException {
        StringBuilder contentsStr = new StringBuilder();
        InputStream fIn = null;
        InputStreamReader isr = null;
        BufferedReader input = null;
        try {
            fIn = getResources().getAssets().open(fileName, Context.MODE_WORLD_READABLE);
            isr = new InputStreamReader(fIn);
            input = new BufferedReader(isr);
            String line = "";
            while ((line = input.readLine()) != null) {
                contentsStr.append(line + "\n");
            }
        } catch (Exception e) {
            e.getMessage();
        } finally {
            try {
                if (isr != null)
                    isr.close();
                if (fIn != null)
                    fIn.close();
                if (input != null)
                    input.close();
            } catch (Exception e2) {
                e2.getMessage();
            }
        }
        return contentsStr.toString();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        Log.d(TAG, location.getLatitude() + ", " + location.getLongitude());
        if (mLoggingRunning) {
            HashMap data = new HashMap();
            data.put("lat", location.getLatitude());
            data.put("lng", location.getLongitude());
           publishJson(
                   data,
                   topicName.getText().toString()
           );
            Toast.makeText(MainActivity.this, "published location data", Toast.LENGTH_LONG);
        }
    }

    private void startCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, RESULT_CAMERA);
    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

}