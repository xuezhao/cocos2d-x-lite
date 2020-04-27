/****************************************************************************
Copyright (c) 2010-2012 cocos2d-x.org
Copyright (c) 2013-2016 Chukong Technologies Inc.
Copyright (c) 2017-2018 Xiamen Yaji Software Co., Ltd.

http://www.cocos2d-x.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
 ****************************************************************************/
package org.cocos2dx.lib;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.Vibrator;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;


public class Cocos2dxHelper {
    // ===========================================================
    // Constants
    // ===========================================================
    private static final String TAG = Cocos2dxHelper.class.getSimpleName();

    // ===========================================================
    // Fields
    // ===========================================================

    private static Activity sActivity;
    private static Vibrator sVibrateService;
    private static BatteryReceiver sBatteryReceiver = new BatteryReceiver();

    public static final int NETWORK_TYPE_NONE = 0;
    public static final int NETWORK_TYPE_LAN  = 1;
    public static final int NETWORK_TYPE_WWAN = 2;

    // The absolute path to the OBB if it exists.
    private static String sObbFilePath = "";
    
    // The OBB file
    private static ZipResourceFile sOBBFile = null;

    /**
     * Battery receiver to getting battery level.
     */
    static class BatteryReceiver extends BroadcastReceiver {
        public float sBatteryLevel = 0.0f;

        @Override
        public void onReceive(Context context, Intent intent) {
            setBatteryLevelByIntent(intent);
        }

        public void setBatteryLevelByIntent(Intent intent) {
            if (null != intent) {
                int current = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                int total = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 1);
                float level = current * 1.0f / total;
                // clamp to 0~1
                sBatteryLevel = Math.min(Math.max(level, 0.0f), 1.0f);
            }
        }
    }

    static void registerBatteryLevelReceiver(Context context) {
        Intent intent = context.registerReceiver(sBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        sBatteryReceiver.setBatteryLevelByIntent(intent);
    }

    static void unregisterBatteryLevelReceiver(Context context) {
        context.unregisterReceiver(sBatteryReceiver);
    }

    public static int getNetworkType() {
        int status = NETWORK_TYPE_NONE;
        NetworkInfo networkInfo;
        try {
            ConnectivityManager connMgr = (ConnectivityManager) sActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
            networkInfo = connMgr.getActiveNetworkInfo();
        } catch (Exception e) {
            e.printStackTrace();
            return status;
        }
        if (networkInfo == null) {
            return status;
        }
        int nType = networkInfo.getType();
        if (nType == ConnectivityManager.TYPE_MOBILE) {
            status = NETWORK_TYPE_WWAN;
        } else if (nType == ConnectivityManager.TYPE_WIFI) {
            status = NETWORK_TYPE_LAN;
        }
        return status;
    }

    // ===========================================================
    // Constructors
    // ===========================================================

    private static boolean sInited = false;
    public static void init(final Activity activity) {
        sActivity = activity;
        if (!sInited) {
            Cocos2dxHelper.sVibrateService = (Vibrator)activity.getSystemService(Context.VIBRATOR_SERVICE);
            Cocos2dxHelper.initObbFilePath();
            Cocos2dxHelper.initializeOBBFile();

            sInited = true;
        }
    }

    public static float getBatteryLevel() { return sBatteryReceiver.sBatteryLevel; }
    public static String getObbFilePath() { return Cocos2dxHelper.sObbFilePath; }
    public static String getWritablePath() {
        return sActivity.getFilesDir().getAbsolutePath();
    }
    public static String getCurrentLanguage() {
        return Locale.getDefault().getLanguage();
    }
    public static String getCurrentLanguageCode() {
        return Locale.getDefault().toString();
    }
    public static String getDeviceModel(){
        return Build.MODEL;
    }
    public static String getSystemVersion() {
        return Build.VERSION.RELEASE;
    }

    public static void vibrate(float duration) {
        try {
            if (sVibrateService != null && sVibrateService.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    Class<?> vibrationEffectClass = Class.forName("android.os.VibrationEffect");
                    if(vibrationEffectClass != null) {
                        final int DEFAULT_AMPLITUDE = Cocos2dxReflectionHelper.<Integer>getConstantValue(vibrationEffectClass,
                                "DEFAULT_AMPLITUDE");
                        //VibrationEffect.createOneShot(long milliseconds, int amplitude)
                        final Method method = vibrationEffectClass.getMethod("createOneShot",
                                new Class[]{Long.TYPE, Integer.TYPE});
                        Class<?> type = method.getReturnType();

                        Object effect =  method.invoke(vibrationEffectClass,
                                new Object[]{(long) (duration * 1000), DEFAULT_AMPLITUDE});
                        //sVibrateService.vibrate(VibrationEffect effect);
                        Cocos2dxReflectionHelper.invokeInstanceMethod(sVibrateService,"vibrate",
                                new Class[]{type}, new Object[]{(effect)});
                    }
                } else {
                    sVibrateService.vibrate((long) (duration * 1000));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean openURL(String url) { 
        boolean ret = false;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            sActivity.startActivity(i);
            ret = true;
        } catch (Exception e) {
        }
        return ret;
    }

    public static void copyTextToClipboard(final String text){
        sActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ClipboardManager myClipboard = (ClipboardManager)sActivity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData myClip = ClipData.newPlainText("text", text);
                myClipboard.setPrimaryClip(myClip);
            }
        });
    }
    
    public static long[] getObbAssetFileDescriptor(final String path) {
        long[] array = new long[3];
        if (Cocos2dxHelper.sOBBFile != null) {
            AssetFileDescriptor descriptor = Cocos2dxHelper.sOBBFile.getAssetFileDescriptor(path);
            if (descriptor != null) {
                try {
                    ParcelFileDescriptor parcel = descriptor.getParcelFileDescriptor();
                    Method method = parcel.getClass().getMethod("getFd", new Class[] {});
                    array[0] = (Integer)method.invoke(parcel);
                    array[1] = descriptor.getStartOffset();
                    array[2] = descriptor.getLength();
                } catch (NoSuchMethodException e) {
                    Log.e(Cocos2dxHelper.TAG, "Accessing file descriptor directly from the OBB is only supported from Android 3.1 (API level 12) and above.");
                } catch (IllegalAccessException e) {
                    Log.e(Cocos2dxHelper.TAG, e.toString());
                } catch (InvocationTargetException e) {
                    Log.e(Cocos2dxHelper.TAG, e.toString());
                }
            }
        }
        return array;
    }

    public static int getDeviceRotation() {
        try {
            Display display = ((WindowManager) sActivity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            return display.getRotation();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        return Surface.ROTATION_0;
    }

    // ===========================================================
    // Private functions.
    // ===========================================================

    // Initialize asset path:
    // - absolute path to the OBB if it exists,
    // - else empty string.
    private static void initObbFilePath() {
        int versionCode = 1;
        final ApplicationInfo applicationInfo = sActivity.getApplicationInfo();
        try {
            versionCode = Cocos2dxHelper.sActivity.getPackageManager().getPackageInfo(applicationInfo.packageName, 0).versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        String pathToOBB = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/obb/" + applicationInfo.packageName + "/main." + versionCode + "." + applicationInfo.packageName + ".obb";
        File obbFile = new File(pathToOBB);
        if (obbFile.exists())
            Cocos2dxHelper.sObbFilePath = pathToOBB;
    }

    private static void initializeOBBFile() {
        int versionCode = 1;
        final ApplicationInfo applicationInfo = sActivity.getApplicationInfo();
        try {
            versionCode = sActivity.getPackageManager().getPackageInfo(applicationInfo.packageName, 0).versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        try {
            Cocos2dxHelper.sOBBFile = APKExpansionSupport.getAPKExpansionZipFile(sActivity, versionCode, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
