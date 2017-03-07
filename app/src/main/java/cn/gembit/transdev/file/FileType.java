package cn.gembit.transdev.file;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.support.v4.content.ContextCompat;
import android.webkit.MimeTypeMap;

import cn.gembit.transdev.R;
import cn.gembit.transdev.activities.BaseActivity;
import cn.gembit.transdev.app.AppConfig;

public class FileType {

    public final static int DIR = 0;
    public final static int FILE_APK = 1;
    public final static int FILE_ARCHIVE = 2;
    public final static int FILE_AUDIO = 3;
    public final static int FILE_EXCEL = 4;
    public final static int FILE_IMAGE = 5;
    public final static int FILE_PDF = 6;
    public final static int FILE_PPT = 7;
    public final static int FILE_TEXT = 8;
    public final static int FILE_VIDEO = 9;
    public final static int FILE_WORD = 10;
    public final static int FILE_UNKNOWN = 11;

    private final static int TYPE_COUNT = FILE_UNKNOWN + 1;

    private static Drawable[] sIconDrawables = null;

    public static String getNameExtension(String name) {
        if (name != null) {
            int index = name.lastIndexOf('.');
            if (index > 0 && index < name.length() - 1) {
                return name.substring(index + 1).toLowerCase();
            }
        }
        return "";
    }

    public static int judgeTypeByName(String name) {
        String extension = FileType.getNameExtension(name);
        switch (extension) {
            case "":
                return FILE_UNKNOWN;

            case "apk":
                return FILE_APK;

            case "doc":
            case "docx":
                return FILE_WORD;

            case "xls":
            case "xlsx":
                return FILE_EXCEL;

            case "ppt":
            case "pptx":
                return FILE_PPT;

            case "pdf":
                return FILE_PDF;

            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
            case "bz2":
            case "tgz":
                return FILE_ARCHIVE;

            default:
                break;
        }

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mime != null) {
            if (mime.startsWith("text/")) {
                return FILE_TEXT;
            }

            if (mime.startsWith("audio/")) {
                return FILE_AUDIO;
            }

            if (mime.startsWith("image/")) {
                return FILE_IMAGE;
            }

            if (mime.startsWith("video/")) {
                return FILE_VIDEO;
            }
        }
        return FILE_UNKNOWN;
    }

    public static Drawable getIcon(Context context, int fileType) {
        if (sIconDrawables == null) {
            generateIconDrawables(context);
        }
        fileType = fileType >= 0 && fileType < TYPE_COUNT ? fileType : FILE_UNKNOWN;
        return sIconDrawables[fileType];
    }

    private static void generateIconDrawables(Context context) {
        Drawable[][] drawables = new Drawable[TYPE_COUNT][2];
        for (int i = 0; i < drawables.length; i++) {
            drawables[i][0] = ContextCompat.getDrawable(context, AppConfig.readFileIconBgId());
        }

        drawables[DIR][0].setColorFilter(BaseActivity.getColor(context, R.attr.colorPrimary),
                PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_APK][0].setColorFilter(0xff43a047, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_ARCHIVE][0].setColorFilter(0xff795548, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_AUDIO][0].setColorFilter(0xffe53935, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_EXCEL][0].setColorFilter(0xff1d7044, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_IMAGE][0].setColorFilter(0xff7cb342, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_PDF][0].setColorFilter(0xffe93e30, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_PPT][0].setColorFilter(0xffd04424, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_TEXT][0].setColorFilter(0xff076fc1, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_VIDEO][0].setColorFilter(0xff673ab7, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_WORD][0].setColorFilter(0xff2a5696, PorterDuff.Mode.SRC_ATOP);
        drawables[FILE_UNKNOWN][0].setColorFilter(0xff607d8b, PorterDuff.Mode.SRC_ATOP);

        drawables[DIR][1] = ContextCompat.getDrawable(context, R.drawable.ic_dir);
        drawables[FILE_APK][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_apk);
        drawables[FILE_ARCHIVE][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_archive);
        drawables[FILE_AUDIO][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_audio);
        drawables[FILE_EXCEL][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_excel);
        drawables[FILE_IMAGE][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_image);
        drawables[FILE_PDF][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_pdf);
        drawables[FILE_PPT][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_ppt);
        drawables[FILE_TEXT][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_text);
        drawables[FILE_VIDEO][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_video);
        drawables[FILE_WORD][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_word);
        drawables[FILE_UNKNOWN][1] = ContextCompat.getDrawable(context, R.drawable.ic_file_unknown);

        sIconDrawables = new LayerDrawable[TYPE_COUNT];
        for (int i = 0; i < drawables.length; i++) {
            sIconDrawables[i] = new LayerDrawable(drawables[i]);
        }
    }
}
