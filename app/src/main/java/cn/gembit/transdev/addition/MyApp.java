package cn.gembit.transdev.addition;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.StrictMode;
import android.util.TypedValue;

import java.io.UnsupportedEncodingException;


public class MyApp extends Application implements Thread.UncaughtExceptionHandler {

    private static MyApp sMyApp;
    private static TypedValue typedValue = new TypedValue();

    public static SharedPreferences getSharedPreferences(String name) {
        return sMyApp.getApplicationContext().getSharedPreferences(name, MODE_PRIVATE);
    }

    public synchronized static int getColor(Context context, int attr) {
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;

//        TypedValue typedValue = new TypedValue();
//
//        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[]{attr});
//        int color = a.getColor(0, 0);
//        a.recycle();
//
//        return color;
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

        doLaunchWork();
    }

    private void doLaunchWork() {
        SharedPreferences sp = Config.AppConfig.getSharedPreferences();
        if (sp.getBoolean(Config.AppConfig.FIRST_LAUNCH, true)) {

            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean(Config.AppConfig.FIRST_LAUNCH, false);
            editor.apply();

        }
    }

    @Override
    public void uncaughtException(final Thread t, final Throwable cause) {
        cause.printStackTrace();
        StringBuilder msg = new StringBuilder();

        msg.append("Device=").append(Build.BRAND)
                .append('(').append(Build.MODEL).append(")\n");

        String versionName = Build.VERSION.RELEASE;
        int versionCode = Build.VERSION.SDK_INT;
        msg.append("OS=").append("Android ").append(versionName)
                .append('(').append(versionCode).append(")\n");

        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "unknown";
            versionCode = -1;
        }
        msg.append("AppVersion=").append(versionName)
                .append('(').append(versionCode).append(")\n");

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

        String url = "http://transdev.gembit.cn/BugReport.php";
        url += "?msg=" + MyApp.encode(msg.substring(0, Math.min(msg.length(), 2000)));

        Intent chooser = Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                "应用崩溃，是否发送报告？");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sMyApp.getApplicationContext().startActivity(chooser);

        Process.killProcess(Process.myPid());
    }
}
