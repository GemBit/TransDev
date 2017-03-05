package cn.gembit.transdev.addition;


import android.content.Context;
import android.content.SharedPreferences;

public class Config {

    private static SharedPreferences getSharedPreferences(String name) {
        return MyApp.getSharedPreferences(name);
    }

    private static SharedPreferences.Editor getSharedPreferencesEditor(String name) {
        return getSharedPreferences(name).edit();
    }

    public static class AppConfig {
        public final static String FIRST_LAUNCH = "FirstLaunch";

        public static SharedPreferences getSharedPreferences() {
            return Config.getSharedPreferences(AppConfig.class.getSimpleName());
        }

        public static SharedPreferences.Editor getSharedPreferencesEditor() {
            return Config.getSharedPreferencesEditor(AppConfig.class.getSimpleName());
        }
    }

    public static class UIConfig {
        public final static String FILE_LIST_PIC_SIZE = "FileListPicSize";

        public static SharedPreferences getSharedPreferences() {
            return Config.getSharedPreferences(UIConfig.class.getSimpleName());
        }

        public static SharedPreferences.Editor getSharedPreferencesEditor() {
            return Config.getSharedPreferencesEditor(UIConfig.class.getSimpleName());
        }
    }
}
