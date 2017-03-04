package cn.gembit.transdev.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;

import cn.gembit.transdev.R;
import cn.gembit.transdev.widgets.BottomDialogBuilder;


public class MainActivity extends AppCompatActivity {

    private final static int PERMISSIONS_REQUEST_CODE = 1;

    private SessionManager mManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PackageInfo info;
        try {
            info = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            BottomDialogBuilder.make(this, "权限自检失败，程序退出").setOnDismissListener(
                    new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    }
            ).show();
            return;
        }

        if (info.requestedPermissions != null) {
            ArrayList<String> deniedPermissions = new ArrayList<>();
            for (String permission : info.requestedPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.size() > 0) {
                ActivityCompat.requestPermissions(this, deniedPermissions.toArray(new String[0]),
                        PERMISSIONS_REQUEST_CODE);
                return;
            }
        }


        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            BottomDialogBuilder.make(this, "未安装储存卡！程序退出")
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    })
                    .show();
        }


        mManager = new SessionManager(
                getSupportFragmentManager(),
                (DrawerLayout) findViewById(R.id.drawerLayout),
                (ViewPager) findViewById(R.id.viewPager),
                ((NavigationView) findViewById(R.id.navView)));
    }

    @Override
    public void onBackPressed() {
        mManager.back();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean permitted = false;
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                permitted = true;
                for (int grantResult : grantResults) {
                    permitted &= grantResult == PackageManager.PERMISSION_GRANTED;
                }
            }
        }

        if (!permitted) {
            BottomDialogBuilder.make(this, "未获得足够权限，程序退出").setOnDismissListener(
                    new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    }).show();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }
}
