package cn.gembit.transdev.app;

import android.content.Context;
import android.os.PowerManager;

import java.util.HashMap;
import java.util.Map;

public class AliveKeeper {

    private final static Map<String, PowerManager.WakeLock> LOCKS = new HashMap<>();

    public static synchronized void keep(Context context, String tag) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        wakeLock.acquire();
        LOCKS.put(tag, wakeLock);
    }

    public static synchronized void release(String tag) {
        PowerManager.WakeLock wakeLock = LOCKS.remove(tag);
        if (wakeLock != null) {
            wakeLock.release();
        }
    }
}
