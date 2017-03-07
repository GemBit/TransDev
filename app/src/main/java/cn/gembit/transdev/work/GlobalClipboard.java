package cn.gembit.transdev.work;

import org.apache.commons.net.ftp.FTPClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;

public class GlobalClipboard {

    private final static List<ClipboardActionCallback> CALLBACK_LIST = new ArrayList<>();

    private static Source sSource;

    public static void registerCallback(ClipboardActionCallback callback) {
        CALLBACK_LIST.add(callback);
        callback.onClipboardAction(sSource != null);
    }

    public static void unregisterCallback(ClipboardActionCallback callback) {
        CALLBACK_LIST.remove(callback);
    }

    public static void putIn(Source source) {
        sSource = source;
        for (ClipboardActionCallback callback : CALLBACK_LIST) {
            callback.onClipboardAction(true);
        }
    }

    public static Source getOut() {
        if (sSource != null) {
            for (ClipboardActionCallback callback : CALLBACK_LIST) {
                callback.onClipboardAction(false);
            }
        }
        Source source = sSource;
        sSource = null;
        return source;
    }

    public interface ClipboardActionCallback {
        void onClipboardAction(boolean putIn);
    }

    public static abstract class Source {
        public boolean isToCopy;
        public FilePath dir;
        public Collection<FileMeta> fileMetaCollection;

        public static class Local extends Source {
        }

        public static class Client extends Source {
            public FTPClient client;
            public ClientAction.Argument.Connect connectArg;
        }
    }

    public static abstract class Destination {
        public FilePath dir;

        public static class Local extends Destination {
        }

        public static class Client extends Destination {
            public FTPClient client;
            public ClientAction.Argument.Connect connectArg;
        }
    }

    public static class NameDuplicateHandler {

        private final static String TAG = "-";

        private static int sCount;

        private static String mBase;
        private static String mExtension;

        public static void reset(String name) {
            mExtension = FileType.getNameExtension(name);
            mBase = mExtension.length() == 0 ? name :
                    name.substring(0, name.length() - mExtension.length() - 1);
            sCount = 0;
        }

        public static String getNewName() {
            if (mExtension.length() == 0) {
                return mBase + TAG + ++sCount;
            } else {
                return mBase + TAG + ++sCount + "." + mExtension;
            }
        }

        public static boolean hasGottenName() {
            return sCount > 0;
        }
    }

}
