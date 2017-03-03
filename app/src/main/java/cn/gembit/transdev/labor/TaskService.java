package cn.gembit.transdev.labor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import cn.gembit.transdev.R;
import cn.gembit.transdev.ui.TaskActivity;
import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;

public class TaskService extends Service {

    private final static int ONGOING_NOTIFICATION_ID = 1;
    private final static int RESULT_NOTIFICATION_ID = 2;

    private final static List<Task> TASKS = new LinkedList<>();
    private static int sUnfinishedTaskCount = 0;

    private static TaskService sService;

    public static void createTask(Context context, Task task) {
        synchronized (TASKS) {
            Intent intent = new Intent(context, TaskService.class);
            intent.putExtra("index", TASKS.size());
            TASKS.add(task);
            context.startService(intent);
            sUnfinishedTaskCount++;
        }
        Toast.makeText(context, "已添加传输任务", Toast.LENGTH_SHORT).show();
    }


    public static List<Task> getTasks() {
        List<Task> mTasks = new ArrayList<>(TASKS);
        Collections.reverse(mTasks);
        return mTasks;
    }

    private static Notification makeNotification() {
        boolean finished = sUnfinishedTaskCount == 0;

        return new NotificationCompat.Builder(sService)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(!finished)
                .setAutoCancel(finished)
                .setContentTitle(finished ? "传输完成" : "正在进行后台传输")
                .setContentText(finished ? "点击查看" : sUnfinishedTaskCount + "个任务进行中")
                .setContentIntent(PendingIntent.getActivity(
                        sService, 0, new Intent(sService, TaskActivity.class), 0))
                .build();
    }

    private static void analyzeLocalSource(Task task, int from) {
        int to = task.mSrcPathQueue.size();
        int dirCount = task.dirTotal;

        for (; from < to && task.status != Task.STATUS.interrupted; from++) {
            if (task.mSrcTypeQueue.get(from)) {
                FilePath path = task.mSrcPathQueue.get(from);
                File[] children = new File(path.pathString).listFiles();
                if (children == null) {
                    continue;
                }
                for (File child : children) {
                    task.mSrcPathQueue.add(path.getChild(child.getName()));
                    if (child.isDirectory()) {
                        task.mSrcTypeQueue.add(true);
                        task.dirTotal++;
                    } else {
                        task.mSrcTypeQueue.add(false);
                        task.fileTotal++;
                        task.sizeTotal += child.length();
                    }
                }
            }
        }

        if (task.dirTotal > dirCount) {
            analyzeLocalSource(task, to);
        }
    }

    private static void analyzeClientSource(Task task, int from) {
        int to = task.mSrcPathQueue.size();
        int dirCount = task.dirTotal;
        FTPClient client = ((GlobalClipboard.Source.Client) task.source).client;

        for (; from < to && task.status != Task.STATUS.interrupted; from++) {
            if (task.mSrcTypeQueue.get(from)) {
                FilePath path = task.mSrcPathQueue.get(from);

                FTPFile[] children;
                try {
                    children = client.listFiles(path.pathString, ClientAction.ONLY_CHILD_FILTER);
                } catch (Exception e) {
                    task.analysisError++;
                    continue;
                }
                if (children == null) {
                    continue;
                }
                for (FTPFile child : children) {
                    task.mSrcPathQueue.add(path.getChild(child.getName()));
                    if (child.isDirectory()) {
                        task.mSrcTypeQueue.add(true);
                        task.dirTotal++;
                    } else {
                        task.mSrcTypeQueue.add(false);
                        task.fileTotal++;
                        task.sizeTotal += child.getSize();
                    }
                }
            }
        }

        if (task.dirTotal > dirCount) {
            analyzeClientSource(task, to);
        }
    }

    private static void analyzeLocalDestination(Task task) {
        String[] names = new File(task.destination.dir.pathString).list();
        if (names != null) {
            task.mDestExisted.addAll(Arrays.asList(names));
        }
    }

    private static void analyzeClientDestination(Task task) {
        FTPFile[] children;
        try {
            children = ((GlobalClipboard.Destination.Client) task.destination).client
                    .listFiles(task.destination.dir.pathString, ClientAction.ONLY_CHILD_FILTER);
        } catch (Exception e) {
            task.analysisError++;
            children = null;
        }
        if (children != null) {
            for (FTPFile child : children) {
                task.mDestExisted.add(child.getName());
            }
        }
    }

    private static void generateDestinationPath(Task task) {
        HashMap<FilePath, FilePath> duplicatedPaths = new HashMap<>();
        FilePath cachedDuplicatedPath = null;
        FilePath cachedSolutionPath = null;

        for (FileMeta meta : task.source.fileMetaCollection) {
            String name = meta.name;
            if (task.mDestExisted.contains(name)) {
                GlobalClipboard.NameDuplicateHandler.reset(name);
                do {
                    name = GlobalClipboard.NameDuplicateHandler.getNewName();
                } while (task.mDestExisted.contains(name));
                duplicatedPaths.put(
                        task.destination.dir.getChild(meta.name),
                        task.destination.dir.getChild(name));
                task.mDestExisted.add(name);
            }
        }

        for (FilePath srcPath : task.mSrcPathQueue) {

            FilePath destPath = srcPath.move(task.source.dir, task.destination.dir);
            if (cachedDuplicatedPath != null && cachedDuplicatedPath.cover(destPath)) {
                destPath = destPath.move(cachedDuplicatedPath, cachedSolutionPath);
            } else {
                for (FilePath duplicatedPath : duplicatedPaths.keySet()) {
                    if (duplicatedPath.cover(destPath)) {
                        cachedDuplicatedPath = duplicatedPath;
                        cachedSolutionPath = duplicatedPaths.get(duplicatedPath);
                        destPath = destPath.move(cachedDuplicatedPath, cachedSolutionPath);
                        break;
                    }
                }
            }
            task.mDestPathQueue.add(destPath);
        }
    }

    private static void localToLocal(Task task) {
        for (int i = 0; i < task.mSrcPathQueue.size() &&
                task.status != Task.STATUS.interrupted; i++) {

            File destFile = new File(task.mDestPathQueue.get(i).pathString);
            try {
                if (task.mSrcTypeQueue.get(i)) {
                    if (destFile.mkdirs()) {
                        task.dirDone++;
                    } else {
                        task.transferError++;
                    }
                } else {
                    task.currentFile = task.mSrcPathQueue.get(i);
                    InputStream is = new FileInputStream(task.currentFile.pathString);
                    OutputStream os = new FileOutputStream(destFile);
                    if (copyStream(task, is, os)) {
                        task.fileDone++;
                    } else {
                        task.transferError++;
                    }
                }
            } catch (Exception e) {
                task.transferError++;
            }
        }
    }

    private static void localToClient(Task task) {
        FTPClient client = ((GlobalClipboard.Destination.Client) task.destination).client;

        for (int i = 0; i < task.mSrcPathQueue.size() &&
                task.status != Task.STATUS.interrupted; i++) {

            String destPath = task.mDestPathQueue.get(i).pathString;
            try {
                if (task.mSrcTypeQueue.get(i)) {
                    if (client.makeDirectory(destPath)) {
                        task.dirDone++;
                    } else {
                        task.transferError++;
                    }
                } else {
                    task.currentFile = task.mSrcPathQueue.get(i);
                    InputStream is = new FileInputStream(task.currentFile.pathString);
                    OutputStream os = client.storeFileStream(destPath);
                    if (os != null && copyStream(task, is, os, client)) {
                        task.fileDone++;
                    } else {
                        task.transferError++;
                    }
                }
            } catch (Exception e) {
                task.transferError++;
            }
        }
    }

    private static void clientToLocal(Task task) {
        FTPClient client = ((GlobalClipboard.Source.Client) task.source).client;

        for (int i = 0; i < task.mSrcPathQueue.size() &&
                task.status != Task.STATUS.interrupted; i++) {

            File destFile = new File(task.mDestPathQueue.get(i).pathString);
            try {
                if (task.mSrcTypeQueue.get(i)) {
                    if (destFile.mkdirs()) {
                        task.dirDone++;
                    } else {
                        task.transferError++;
                    }
                } else {
                    task.currentFile = task.mSrcPathQueue.get(i);
                    InputStream is = client.retrieveFileStream(task.currentFile.pathString);
                    OutputStream os = new FileOutputStream(destFile);
                    if (is != null && copyStream(task, is, os, client)) {
                        task.fileDone++;
                    } else {
                        task.transferError++;
                    }
                }
            } catch (Exception e) {
                task.transferError++;
            }
        }
    }

    private static void clientToClient(Task task) {
        FTPClient srcClient = ((GlobalClipboard.Source.Client) task.source).client;
        FTPClient destClient = ((GlobalClipboard.Destination.Client) task.destination).client;

        for (int i = 0; i < task.mSrcPathQueue.size() &&
                task.status != Task.STATUS.interrupted; i++) {

            String destPath = task.mDestPathQueue.get(i).pathString;
            try {
                if (task.mSrcTypeQueue.get(i)) {
                    if (destClient.makeDirectory(destPath)) {
                        task.dirDone++;
                    } else {
                        task.transferError++;
                    }
                } else {
                    task.currentFile = task.mSrcPathQueue.get(i);
                    InputStream is = srcClient.retrieveFileStream(task.currentFile.pathString);
                    OutputStream os = destClient.storeFileStream(destPath);
                    if (is != null && os != null &&
                            copyStream(task, is, os, srcClient, destClient)) {
                        task.fileDone++;
                    } else {
                        task.transferError++;
                    }
                }
            } catch (Exception e) {
                task.transferError++;
            }
        }
    }

    private static boolean copyStream(
            Task task, InputStream is, OutputStream os, FTPClient... clients)
            throws Exception {

        boolean noError = true;
        try {
            int read;
            while (task.status != Task.STATUS.interrupted && (read = is.read(task.mBuffer)) > 0) {
                os.write(task.mBuffer, 0, read);
                task.sizeDone += read;
            }
        } catch (Exception e) {
            noError = false;
        } finally {
            noError &= task.status != Task.STATUS.interrupted;
            noError &= closeStream(is);
            noError &= closeStream(os);
            for (FTPClient client : clients) {
                try {
                    noError &= client.completePendingCommand();
                } catch (Exception e) {
                    noError = false;
                }
            }
        }

        return noError;
    }

    private static void deleteIfCut(Task task) {
        if (!task.source.isToCopy) {

            if (task.source instanceof GlobalClipboard.Source.Local) {
                for (int i = task.mSrcPathQueue.size(); i-- > 0 &&
                        task.status != Task.STATUS.interrupted; ) {
                    try {
                        task.currentFile = task.mSrcPathQueue.get(i);
                        if (!new File(task.currentFile.pathString).delete()) {
                            task.deletionError++;
                        }
                    } catch (Exception e) {
                        task.transferError++;
                    }
                }

            } else {
                FTPClient client = ((GlobalClipboard.Source.Client) task.source).client;
                for (int i = task.mSrcPathQueue.size(); i-- > 0 &&
                        task.status != Task.STATUS.interrupted; ) {
                    try {
                        task.currentFile = task.mSrcPathQueue.get(i);
                        if (task.mSrcTypeQueue.get(i) ?
                                !client.removeDirectory(task.currentFile.pathString) :
                                !client.deleteFile(task.currentFile.pathString)) {
                            task.deletionError++;
                        }
                    } catch (Exception e) {
                        task.transferError++;
                    }
                }
            }
        }
    }

    private static boolean closeStream(Closeable pipe) {
        if (pipe != null) {
            try {
                pipe.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sService = this;

        startForeground(ONGOING_NOTIFICATION_ID, makeNotification());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int index = intent.getIntExtra("index", -1);
        if (index >= 0 && index < TASKS.size()) {
            new Thread(new TaskRunnable(TASKS.get(index))).start();
        }

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(RESULT_NOTIFICATION_ID);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(ONGOING_NOTIFICATION_ID, makeNotification());

        return super.onStartCommand(intent, flags, startId);
    }

    public static class TaskRunnable implements Runnable {

        private final Task mTask;

        private TaskRunnable(Task task) {
            mTask = task;
        }

        @Override
        public void run() {
            boolean srcIsLocal = mTask.source instanceof GlobalClipboard.Source.Local;
            boolean destIsLocal = mTask.destination instanceof GlobalClipboard.Destination.Local;

            GlobalClipboard.Source.Client clientTypeSrc = !srcIsLocal ?
                    (GlobalClipboard.Source.Client) mTask.source : null;
            GlobalClipboard.Destination.Client clientTypeDest = !destIsLocal ?
                    (GlobalClipboard.Destination.Client) mTask.destination : null;

            if (mTask.status != Task.STATUS.interrupted && connect(clientTypeSrc, clientTypeDest)) {

                for (FileMeta meta : mTask.source.fileMetaCollection) {
                    mTask.mSrcPathQueue.add(mTask.source.dir.getChild(meta.name));
                    if (meta.type == FileType.DIR) {
                        mTask.mSrcTypeQueue.add(true);
                        mTask.dirTotal++;
                    } else {
                        mTask.mSrcTypeQueue.add(false);
                        mTask.fileTotal++;
                        mTask.sizeTotal += meta.numericalSize;
                    }
                }

                synchronized (mTask) {
                    if (mTask.status != Task.STATUS.interrupted) {
                        mTask.status = Task.STATUS.analyzing;
                    }
                }

                if (srcIsLocal) {
                    analyzeLocalSource(mTask, 0);
                } else {
                    analyzeClientSource(mTask, 0);
                }

                if (destIsLocal) {
                    analyzeLocalDestination(mTask);
                } else {
                    analyzeClientDestination(mTask);
                }

                generateDestinationPath(mTask);

                synchronized (mTask) {
                    if (mTask.status != Task.STATUS.interrupted) {
                        mTask.status = Task.STATUS.transferring;
                    }
                }
                if (srcIsLocal && destIsLocal) {
                    localToLocal(mTask);
                }
                if (srcIsLocal && !destIsLocal) {
                    localToClient(mTask);
                }
                if (!srcIsLocal && destIsLocal) {
                    clientToLocal(mTask);
                }
                if (!srcIsLocal && !destIsLocal) {
                    clientToClient(mTask);
                }

                synchronized (mTask) {
                    if (mTask.status != Task.STATUS.interrupted) {
                        mTask.status = Task.STATUS.deleting;
                    }
                }
                deleteIfCut(mTask);
            }

            disconnect(clientTypeSrc, clientTypeDest);
            mTask.release();
        }

        private boolean connect(final GlobalClipboard.Source.Client clientTypeSrc,
                                final GlobalClipboard.Destination.Client clientTypeDest) {

            final boolean[] error = new boolean[2];

            if (clientTypeSrc != null) {
                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        return clientTypeSrc.connectArg;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                        Result.Connect theResult = (Result.Connect) result;
                        clientTypeSrc.client = theResult.ftpClient;
                        error[0] = theResult.error;
                    }
                }.start(false);
            }

            if (clientTypeDest != null) {
                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        return clientTypeDest.connectArg;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                        Result.Connect theResult = (Result.Connect) result;
                        clientTypeDest.client = theResult.ftpClient;
                        error[1] = theResult.error;
                    }
                }.start(false);
            }

            return !error[0] && !error[1];
        }

        private void disconnect(final GlobalClipboard.Source.Client clientTypeSrc,
                                final GlobalClipboard.Destination.Client clientTypeDest) {
            if (clientTypeSrc != null) {
                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        Argument.Disconnect argument = new Argument.Disconnect();
                        argument.ftpClient = clientTypeSrc.client;
                        return argument;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                    }
                }.start(false);
            }

            if (clientTypeDest != null) {
                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        Argument.Disconnect argument = new Argument.Disconnect();
                        argument.ftpClient = clientTypeDest.client;
                        return argument;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                    }
                }.start(false);
            }
        }
    }

    public static class Task {
        public final GlobalClipboard.Source source;
        public final GlobalClipboard.Destination destination;

        public STATUS status = STATUS.none;

        public FilePath currentFile;

        public int fileTotal = 0;
        public int dirTotal = 0;
        public long sizeTotal = 0;

        public int fileDone = 0;
        public int dirDone = 0;
        public long sizeDone = 0;

        public int analysisError = 0;
        public int transferError = 0;
        public int deletionError = 0;

        private List<FilePath> mSrcPathQueue = new ArrayList<>();
        private List<FilePath> mDestPathQueue = new ArrayList<>();
        private List<Boolean> mSrcTypeQueue = new ArrayList<>();
        private List<String> mDestExisted = new ArrayList<>();

        private byte[] mBuffer = new byte[4096];

        public Task(GlobalClipboard.Source source, GlobalClipboard.Destination destination) {
            this.source = source;
            this.destination = destination;
        }

        public synchronized void kill() {
            if (status != STATUS.finished) {
                status = STATUS.interrupted;
            }
        }

        private void release() {
            synchronized (this) {
                if (status != Task.STATUS.interrupted) {
                    status = STATUS.finished;
                }
            }
            source.fileMetaCollection = null;
            mSrcPathQueue = null;
            mDestPathQueue = null;
            mSrcTypeQueue = null;
            mDestExisted = null;
            mBuffer = null;

            synchronized (TASKS) {
                if (--sUnfinishedTaskCount == 0) {
                    ((NotificationManager) sService.getSystemService(Context.NOTIFICATION_SERVICE))
                            .notify(RESULT_NOTIFICATION_ID, makeNotification());
                    sService.stopSelf();
                } else {
                    ((NotificationManager) sService.getSystemService(Context.NOTIFICATION_SERVICE))
                            .notify(ONGOING_NOTIFICATION_ID, makeNotification());
                }
            }
        }

        public enum STATUS {
            none, analyzing, transferring, deleting, finished, interrupted
        }
    }
}
