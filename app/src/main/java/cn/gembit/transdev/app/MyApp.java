package cn.gembit.transdev.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import cn.gembit.transdev.activities.AdditionActivity;
import cn.gembit.transdev.activities.BaseActivity;


public class MyApp extends Application implements Thread.UncaughtExceptionHandler {

    private static MyApp sMyApp;

    public static SharedPreferences getSharedPreferences(String name) {
        return sMyApp.getApplicationContext().getSharedPreferences(name, MODE_PRIVATE);
    }

    public static Context getAppContext() {
        return sMyApp.getApplicationContext();
    }

    private static String encode(String message) {
        final byte[] charset = new byte[64];
        for (byte i = 'A'; i <= 'Z'; i++) {
            charset[i - 'A'] = i;
        }
        for (byte i = 'a'; i <= 'z'; i++) {
            charset[i - 'a' + 26] = i;
        }
        for (byte i = '0'; i <= '9'; i++) {
            charset[i - '0' + 52] = i;
        }
        charset[62] = '-';
        charset[63] = '_';

        byte[] encoding;
        try {
            byte[] arr = message.getBytes("UTF-8");
            if (arr.length % 3 != 0) {
                encoding = new byte[arr.length / 3 * 3 + 3];
                System.arraycopy(arr, 0, encoding, 0, arr.length);
            } else {
                encoding = arr;
            }
        } catch (UnsupportedEncodingException e) {
            encoding = new byte[0];
        }

        byte[] encoded = new byte[encoding.length / 3 * 4];
        for (int i = 0; i < encoding.length; i += 3) {
            int p = (((int) encoding[i]) & 0xff);
            p = p << 8 | (((int) encoding[i + 1]) & 0xff);
            p = p << 8 | (((int) encoding[i + 2]) & 0xff);
            encoded[i / 3 * 4] = charset[(p >> 18) & 63];
            encoded[i / 3 * 4 + 1] = charset[(p >> 12) & 63];
            encoded[i / 3 * 4 + 2] = charset[(p >> 6) & 63];
            encoded[i / 3 * 4 + 3] = charset[p & 63];
        }

        try {
            return new String(encoded, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
        sMyApp = this;
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    @Override
    public void uncaughtException(final Thread t, final Throwable cause) {
        new Handler(Looper.getMainLooper()).postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                StringBuilder msg = new StringBuilder();

                msg.append("Android\n");
                msg.append(Build.VERSION.SDK_INT).append("\n");

                msg.append(Build.BRAND).append('(').append(Build.MODEL).append(")\n");

                try {
                    PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                    msg.append(info.versionCode).append("\n");
                } catch (PackageManager.NameNotFoundException e) {
                    msg.append(-1).append("\n");
                }


                Throwable e = cause;
                do {
                    msg.append("cause:").append(e.getClass().getName()).append(":")
                            .append(e.getMessage()).append("\n");
                    for (StackTraceElement ele : e.getStackTrace()) {
                        msg.append("\t")
                                .append(ele.getClassName())
                                .append("#")
                                .append(ele.getMethodName())
                                .append("(")
                                .append(ele.getFileName())
                                .append(":")
                                .append(ele.getLineNumber())
                                .append(")\n");
                    }
                    e = e.getCause();
                } while (e != null);

                AppConfig.saveBugReport("msg=" + encode(msg.toString()));

                BaseActivity.exit(true);
            }
        });

        if (Looper.myLooper() == Looper.getMainLooper()) {
            Looper.loop();
        }
    }
}
