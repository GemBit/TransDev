package cn.gembit.transdev.file;

import android.support.annotation.NonNull;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("WeakerAccess")
public class FileMeta implements Comparable<FileMeta> {

    private final static SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final static DecimalFormat SIZE_FORMAT_KB = new DecimalFormat("0.00KB");
    private final static DecimalFormat SIZE_FORMAT_MB = new DecimalFormat("0.00MB");
    private final static DecimalFormat SIZE_FORMAT_GB = new DecimalFormat("0.00GB");

    public final int type;
    public final String name;
    public final String size;
    public final String time;

    public final long numericalSize;
    public final long numericalTime;

    public FileMeta(boolean isDir, String name, long size, long time) {
        this.type = isDir ? FileType.DIR : FileType.judgeTypeByName(name);
        this.name = name;
        this.size = isDir ? "目录" : formatSize(size);
        this.numericalSize = isDir ? 0 : size;
        this.time = formatTime(time);
        this.numericalTime = time;
    }

    public static String formatSize(long size) {
        if (size < 1024) {
            return size + "B";
        } else if (size < 1024 * 1024) {
            return SIZE_FORMAT_KB.format(size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return SIZE_FORMAT_MB.format(size / (1024.0 * 1024.0));
        } else {
            return SIZE_FORMAT_GB.format(size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static String formatTime(long time) {
        return TIME_FORMAT.format(new Date(time));
    }

    @Override
    public int compareTo(@NonNull FileMeta o) {
        if (type == FileType.DIR && o.type != FileType.DIR) {
            return -1;
        } else if (type != FileType.DIR && o.type == FileType.DIR) {
            return 1;
        } else {
            int result = name.compareToIgnoreCase(o.name);
            return result != 0 ? result : name.compareTo(o.name);
        }
    }

    public FileMeta getRenamed(String newName) {
        return new FileMeta(type == FileType.DIR, newName, numericalSize, numericalTime);
    }
}