package cn.gembit.transdev.app;


import android.content.SharedPreferences;

import cn.gembit.transdev.R;

public class AppConfig {

    private static SharedPreferences getPreferences() {
        return MyApp.getSharedPreferences("AppConfig");
    }

    public static void saveFileListPicSize(int size) {
        getPreferences().edit().putInt("FileListPicSize", size).apply();
    }

    public static int readFileListPicSize(int defaultVal) {
        return getPreferences().getInt("FileListPicSize", defaultVal);
    }


    public static void saveThemeID(int themeId) {
        getPreferences().edit().putInt("ThemeID", themeId).apply();
    }

    public static int readThemeID() {
        return getPreferences().getInt("ThemeID", R.style.AppTheme_Dark);
    }


    public static void saveFileIconBgId(int fileIconBgResID) {
        getPreferences().edit().putInt("FileIconBgId", fileIconBgResID).apply();
    }

    public static int readFileIconBgId() {
        return getPreferences().getInt("FileIconBgId", R.drawable.bg_file_icon_square);
    }
}
