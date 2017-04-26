package cn.gembit.transdev.activities;

import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;

import java.util.LinkedList;
import java.util.List;

import cn.gembit.transdev.app.AppConfig;
import cn.gembit.transdev.app.MyApp;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class BaseActivity extends AppCompatActivity {

    private static List<BaseActivity> sActivities = new LinkedList<>();

    private static TypedValue typedValue = new TypedValue();

    public static void exit(boolean restart) {
        ((NotificationManager) sActivities.get(sActivities.size() - 1)
                .getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        for (int i = sActivities.size() - 1; i >= 0; i--) {
            sActivities.get(i).finish();
        }
        if (restart) {
            MyApp.getAppContext().startActivity(
                    new Intent(MyApp.getAppContext(), MainActivity.class)
                            .addFlags(FLAG_ACTIVITY_NEW_TASK));
        }
        Process.killProcess(Process.myPid());
    }

    public static void conformExit(Context context) {
        new AlertDialog.Builder(context)
                .setTitle("选择操作")
                .setMessage("退出或者重启将会强制结束所有后台任务")
                .setNeutralButton("取消", null)
                .setNegativeButton("重启", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BaseActivity.exit(true);
                    }
                })
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BaseActivity.exit(false);
                    }
                })
                .show();
    }

    public synchronized static int getAttrColor(Context context, int attr) {
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        int theme = AppConfig.readThemeID();
        setTheme(theme);

        super.onCreate(savedInstanceState);

        sActivities.add(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sActivities.remove(this);
    }
}
