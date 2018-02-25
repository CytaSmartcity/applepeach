package com.peach.apple.applepeach;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.secneo.sdk.Helper;


import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;

import static android.content.ContentValues.TAG;

/**
 * Created by xxhong on 16-11-21.
 */

public class RCApplication extends Application {
    private String ip,port;
    private static BaseProduct mProduct;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    //added for dji import classes
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(RCApplication.this);
    }

    //dji specific
    public BaseProduct getBaseProduct() {
        return mProduct;
    }
    public Aircraft getAircraft() {return  (Aircraft) mProduct;}
    public void setBaseProduct(BaseProduct mProduct) {
        this.mProduct = mProduct;
    }



    //show messages on screen
    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }




}
