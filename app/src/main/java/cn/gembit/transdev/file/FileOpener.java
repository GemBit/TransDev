package cn.gembit.transdev.file;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;

import cn.gembit.transdev.widgets.BottomDialogBuilder;

public class FileOpener {

    public final static String[] GENERIC_TYPES =
            {"文本", "图片", "音频", "视频", "其他"};
    public final static String[] GENERIC_MIME =
            {"text/*", "image/*", "audio/*", "video/*", "*/*"};

    public static void open(File file, Context context) {
        String extension = FileType.getNameExtension(file.getName());
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        if (mime == null) {
            openAs(file, context);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), mime);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            openAs(file, context);
        }
    }

    public static void openAs(final File file, final Context context) {
        final int count = GENERIC_TYPES.length;
        final String[] types = new String[count + 1];
        System.arraycopy(GENERIC_TYPES, 0, types, 0, count);
        types[count] = "分享";
        new AlertDialog.Builder(context)
                .setTitle("打开为")
                .setCancelable(true)
                .setItems(types, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent;
                        if (which < count) {
                            intent = new Intent(Intent.ACTION_VIEW).setDataAndType(
                                    Uri.fromFile(file), GENERIC_MIME[which]);
                        } else {
                            intent = new Intent(Intent.ACTION_SEND).setType("*/*")
                                    .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                        }
                        try {
                            context.startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            BottomDialogBuilder.make(context, "找不大打开方式对应软件");
                        }
                    }
                })
                .show();
    }
}
