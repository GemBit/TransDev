package cn.gembit.transdev.activities;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;

import java.util.LinkedList;
import java.util.List;

import cn.gembit.transdev.R;
import cn.gembit.transdev.app.AppConfig;

public class BaseActivity extends AppCompatActivity {

    private static List<BaseActivity> sActivities = new LinkedList<>();

    private static TypedValue typedValue = new TypedValue();

    public static void exit() {
        for (int i = sActivities.size() - 1; i >= 0; i--) {
            sActivities.get(i).finish();
        }
        Process.killProcess(Process.myPid());
    }

    public synchronized static int getColor(Context context, int attr) {
        context.getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        int theme = AppConfig.readThemeID();
        setTheme(theme);

        super.onCreate(savedInstanceState);

        sActivities.add(this);
        System.out.println(sActivities.size());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sActivities.remove(this);
        System.out.println(sActivities.size());
    }
}
