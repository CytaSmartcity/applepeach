package com.peach.apple.applepeach;

import android.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.VisionDetectionState;
import dji.common.gimbal.GimbalState;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseComponent;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.thirdparty.v3.eventbus.EventBus;


public class MainActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private static final String TAG = "MainActivity";
    public static FirebaseAuth mAuth;
    private static FirebaseUser currentUser;
    private StorageReference storageRef;
    ////////////////////////
    // DJI VARIABLES
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private List<String> missingPermission = new ArrayList<>();
    private static BaseProduct mProduct;
    private Handler mHandler;
    private static final int REQUEST_PERMISSION_CODE = 12345;
    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            android.Manifest.permission.VIBRATE,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_WIFI_STATE,
            android.Manifest.permission.WAKE_LOCK,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_PHONE_STATE,
    };
    ///////////////////


    private boolean isImageReady = false;


    // DJI Stuff
    private VisionDetectionState visionDetectionState = null;
    private GimbalState gimbalState = null;
    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;
    protected DJICodecManager mCodecManager = null; // Codec for video live view
    TextureView mVideoSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //ButterKnife.bind(this);
        // Event bus to receive msgs
        //EventBus.getDefault().register(this);

        //Dji registration
        startDJIregistration();
        mVideoSurface = findViewById(R.id.video_feed);
        mVideoSurface.setVisibility(View.VISIBLE);



        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null)
            signIn();

        storageRef = FirebaseStorage.getInstance().getReference();

        // Write a message to the database
        //FirebaseDatabase database = FirebaseDatabase.getInstance();
        //DatabaseReference myRef = database.getReference("message");
        //myRef.setValue("Hello, World!");
        //uploadFile("drawable/", "drone.png", "testing");

        loopForImage();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //EventBus.getDefault().unregister(this);
    }

    //"path/to/images/rivers.jpg"


    private void signUp() {
        mAuth.createUserWithEmailAndPassword("abcd@gmail.com", "123456")
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("ApplePeach", "createUserWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w("ApplePeach", "createUserWithEmail:failure", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();

                        }

                        // ...
                    }
                });
    }

    private void signIn() {
        mAuth.signInWithEmailAndPassword("abcd@gmail.com", "123456")
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d("ApplePeach", "signInWithEmail:success");
                            Toast.makeText(MainActivity.this, " signed in",
                                    Toast.LENGTH_SHORT).show();

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w("ApplePeach", "signInWithEmail:failure", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            //updateUI(null);
                        }

                        // ...
                    }
                });

    }

    //shw msgs on the screen!
    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // DJI RELATED TO registration and product connection!

    /**
     * DJI
     * Result of runtime permission request
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

    public void startDJIregistration() {
        //DJI REGISTRATION
        showToast("empika2");
        Toast.makeText(MainActivity.this, "empika!",
                Toast.LENGTH_SHORT).show();

        // When the compile and target version is higher than 22, please request the following permission at runtime to ensure the SDK works well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkAndRequestPermissions();
        }
        //Initialize DJI SDK Manager
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * DIJ
     * Checks if there is any missing permissions, and
     * requests runtime permission if needed.
     */
    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            showToast("Need to grant the permissions!");
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }
    }

    /**
     * DJI
     * startSDKRegistration
     */
    public void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("Registering, please wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                showToast("Register Success");
                                // Sent message that product has been registered
                                //EventBus.getDefault().post(new ButtonEvent("DJI",true));
                                DJISDKManager.getInstance().startConnectionToProduct();
                            } else {
                                showToast("Register sdk fails, please check the bundle id and network connection!");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                            mProduct = newProduct;
                            if (mProduct != null) {
                                mProduct.setBaseProductListener(mDJIBaseProductListener);
                                // Set the mProduct to the application so that all activities can obtain it
                                ((RCApplication) getApplication()).setBaseProduct(mProduct);
                                // if there is a change broadcast a message
                                //EventBus.getDefault().post(new MessageEvent("dji_product"));
                                enableVideoCallbacks(); //set and enable the video surface of layout and start listening for video

                            }
                            notifyStatusChange();
                        }
                    });
                }
            });
        }
    }

    private BaseProduct.BaseProductListener mDJIBaseProductListener = new BaseProduct.BaseProductListener() {
        @Override
        public void onComponentChange(BaseProduct.ComponentKey key, BaseComponent oldComponent, BaseComponent newComponent) {
            if (newComponent != null) {
                newComponent.setComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }

        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };
    private BaseComponent.ComponentListener mDJIComponentListener = new BaseComponent.ComponentListener() {
        @Override
        public void onConnectivityChange(boolean isConnected) {
            notifyStatusChange();
        }
    };

    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };


    private void enableVideoCallbacks() {

        if (mVideoSurface != null) {
            mVideoSurface.setSurfaceTextureListener(this);
            Log.i(TAG, "VideoSurface listener is set");
        } else {
            Log.i(TAG, "VideoSurface is Null");
        }
        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new VideoFeeder.VideoDataCallback() {
            @Override
            public void onReceive(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    ;
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                } else {
                    //sendError("mCodecManager is Null");
                    Log.d(TAG, "mCodecManager is Null");
                }
            }
        };

        //TODO aircraft returned is unknown so force starting it instead
        VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
//        sendError(VideoFeeder.getInstance().getPrimaryVideoFeed().getCallback().toString());
//        if (!((Aircraft)mProduct).getModel().equals(Model.UNKNOWN_AIRCRAFT)) {
//            VideoFeeder.getInstance().getPrimaryVideoFeed().setCallback(mReceivedVideoDataCallBack);
//            Log.d(TAG,"VideoFeeder callback initiated");
//        }else {
//            Log.d(TAG,"Unkown Aircraft detected, video feed didn't start");
//        }
    }

    //on surface listeners
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) { //initialise the codec to receive data
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }
        return false;
    }


    private void uploadFile(Uri uri, String filename) {
        //Uri file = Uri.fromFile(new File(localPathname + filename));
        //Uri uri = Uri.parse("android.resource://com.peach.apple.applepeach/drawable/drone");
        String root = Environment.getExternalStorageDirectory().toString();
        Uri file = Uri.fromFile(new File(root + "/req_images/ImageMTF.jpg"));

        StorageReference riversRef = storageRef.child("droneImages/" + filename);

        riversRef.putFile(file)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // Get a URL to the uploaded content
                        Uri downloadUrl = taskSnapshot.getDownloadUrl();
                        Toast.makeText(MainActivity.this, "Uploaded file!",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                        Toast.makeText(MainActivity.this, "Unsucessful file upload!",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private Uri saveBitmap(Bitmap image) {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/req_images");
        myDir.mkdirs();
        Random generator = new Random();
        int n = 10000;
        n =generator.nextInt(n);
        String fname = "ImageMTF.jpg";
        //String fname = "image.jpg";
        File file = new File(myDir, fname);
        //Log.i("saveBitmap ",""+file);
        if(file.exists())
            file.delete();
        try
        {
            FileOutputStream out = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

            Uri uri = Uri.fromFile(new File(root + "/req_images" + fname));

            showToast("saved image");

            return uri;
        } catch(Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public void loopForImage() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                Bitmap image;
                //int counter = 1;
                while(true) {
                    try {
                        if(isImageReady) {
                            showToast("empikaaa reee");
                            image = mVideoSurface.getBitmap();
                            Uri locuri = saveBitmap(image);
                            sleep(2000);
                            uploadFile(locuri,"ImageMTF.jpg");
                            sleep(1000);
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.start();
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        isImageReady = true;
        //mVideoSurface.getBitmap()
        // when texture is updated and the toggle has been activated start the thread for sending the picture
    }

}
