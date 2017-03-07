package cn.gembit.transdev.activities;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v7.app.AlertDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FileOpener;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;
import cn.gembit.transdev.work.GlobalClipboard;
import cn.gembit.transdev.work.TaskService;
import cn.gembit.transdev.widgets.BottomDialogBuilder;
import cn.gembit.transdev.widgets.InputDialog;

public class LocalExplorerFragment extends ExplorerFragment {

    private FilePath mRoot;
    private PackageManager mPackageManager;

    public static LocalExplorerFragment newInstance(String title, FilePath root) {
        LocalExplorerFragment fragment = new LocalExplorerFragment();
        fragment.setTitle(title);
        fragment.mRoot = root;
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPackageManager = getContext().getPackageManager();
    }

    @Override
    protected void startUp() {
        setRootDir(mRoot);
        changeDir(mRoot);
    }

    @Override
    protected void newFile(final boolean isDir) {
        String title = isDir ? "输入新建文件夹名称" : "输入新建文件名称";
        new InputDialog(getContext(), title) {
            @Override
            public String checkOut(String input) {
                boolean result;
                try {
                    File file = new File(getCurDir().getChild(input).pathString);
                    result = isDir ? file.mkdir() : file.createNewFile();
                } catch (IOException e) {
                    result = false;
                }
                if (result) {
                    addItemView(new FileMeta(isDir, input, 0, System.currentTimeMillis()));
                }
                return result ? null : "失败，路径错误或已存在同名项";
            }
        }.show();
    }


    @Override
    protected void delete(final Collection<FileMeta> allSelected) {
        new AlertDialog.Builder(getContext())
                .setCancelable(false)
                .setMessage("确定删除这" + allSelected.size() + "项吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        lockFragment(true);

                        new AsyncTask<Void, Void, Void>() {
                            private ArrayList<FileMeta> mDeleted = new ArrayList<>(allSelected.size());

                            @Override
                            protected Void doInBackground(Void... params) {
                                for (FileMeta meta : allSelected) {
                                    if (deleteFile(new File(getCurDir().getChild(meta.name).pathString))) {
                                        mDeleted.add(meta);
                                    }
                                }
                                return null;
                            }

                            private boolean deleteFile(File file) {
                                if (file.isFile()) {
                                    return file.delete();
                                } else if (file.isDirectory()) {
                                    boolean result = true;
                                    for (File child : file.listFiles()) {
                                        result &= deleteFile(child);
                                    }
                                    return result && file.delete();
                                } else {
                                    return false;
                                }
                            }

                            @Override
                            protected void onPostExecute(Void aVoid) {
                                if (getContext() == null) {
                                    return;
                                }
                                lockFragment(false);
                                removeItemView(mDeleted);
                                int failed = allSelected.size() - mDeleted.size();
                                if (failed > 0) {
                                    BottomDialogBuilder.make(
                                            getContext(), "有" + failed + "项删除失败").show();
                                }
                            }
                        }.execute();
                    }
                })
                .show();
    }


    @Override
    protected void rename(final FileMeta oldMeta) {
        int extLen = FileType.getNameExtension(oldMeta.name).length();
        new InputDialog(getContext(), "输入新名称", oldMeta.name, 0,
                extLen == 0 ? oldMeta.name.length() : oldMeta.name.length() - extLen - 1) {
            @Override
            public String checkOut(String input) {
                if (input.equals(oldMeta.name)) {
                    return "名称未变";
                }
                File oldFile = new File(getCurDir().getChild(oldMeta.name).pathString);
                File newFile = new File(getCurDir().getChild(input).pathString);
                if (oldFile.renameTo(newFile)) {
                    modifyItemView(oldMeta, oldMeta.getRenamed(input));
                    return null;
                } else {
                    return "失败，路径错误或已存在同名项";
                }
            }
        }.show();
    }

    @Override
    protected void copyOrCut(Collection<FileMeta> allSelected, boolean isToCopy) {
        GlobalClipboard.Source.Local source = new GlobalClipboard.Source.Local();
        source.isToCopy = isToCopy;
        source.dir = getCurDir();
        source.fileMetaCollection = allSelected;
        GlobalClipboard.putIn(source);
    }

    @Override
    protected void paste() {
        GlobalClipboard.Source source = GlobalClipboard.getOut();

        if (source instanceof GlobalClipboard.Source.Local && !source.isToCopy) {

            if (source.dir.equals(getCurDir())) {
                BottomDialogBuilder.make(getContext(), "不能移动到原路径").show();
                return;
            }

            int failed = 0;
            int renamed = 0;
            for (FileMeta fileMeta : source.fileMetaCollection) {
                File oldFile = new File(source.dir.getChild(fileMeta.name).pathString);
                String newFileName = fileMeta.name;
                GlobalClipboard.NameDuplicateHandler.reset(newFileName);

                do {
                    File newFile = new File(getCurDir().getChild(newFileName).pathString);
                    if (newFile.exists()) {
                        newFileName = GlobalClipboard.NameDuplicateHandler.getNewName();
                        continue;
                    }
                    if (oldFile.renameTo(newFile)) {
                        addItemView(fileMeta.getRenamed(newFileName));
                        if (GlobalClipboard.NameDuplicateHandler.hasGottenName()) {
                            renamed++;
                        }
                        break;
                    } else {
                        failed++;
                        break;
                    }
                } while (true);
            }

            String output = "";
            if (failed > 0) {
                output += "有" + failed + "项移动失败，请检查路径是否正确";
            }
            if (renamed > 0) {
                if (!output.isEmpty()) {
                    output += "\n";
                }
                output += "有" + renamed + "项已自动重命名";
            }
            if (!output.isEmpty()) {
                BottomDialogBuilder.make(getContext(), output).show();
            }

        } else {
            GlobalClipboard.Destination.Local dest = new GlobalClipboard.Destination.Local();
            dest.dir = getCurDir();
            TaskService.Task task = new TaskService.Task(source, dest);
            TaskService.createTask(getContext(), task);
        }
    }

    @Override
    protected void changeDir(FilePath newPath) {
        if (newPath == null) {
            return;
        }
        File[] children = new File(newPath.pathString).listFiles();
        if (children == null) {
            BottomDialogBuilder.make(getContext(), "目录不存在，或者无访问权限").show();
            return;
        }

        ArrayList<FileMeta> allMeta = new ArrayList<>(children.length);
        for (File child : children) {
            allMeta.add(new FileMeta(
                    child.isDirectory(),
                    child.getName(),
                    child.length(),
                    child.lastModified()));
        }
        notifyDirChanged(newPath, allMeta);
    }

    @Override
    protected void enter(FileMeta meta) {
        if (meta.type == FileType.DIR) {
            changeDir(getCurDir().getChild(meta.name));
        } else {
            FileOpener.open(new File(getCurDir().getChild(meta.name).pathString), getContext());
        }
    }

    @Override
    protected void moreAction(FileMeta meta) {
        if (meta.type != FileType.DIR) {
            FileOpener.openAs(new File(getCurDir().getChild(meta.name).pathString), getContext());
        }
    }

    @Override
    protected Drawable getIconDrawable(FileMeta meta) {
        if (meta.type == FileType.FILE_APK) {
            String apkFilePath = getCurDir().getChild(meta.name).pathString;
            PackageInfo info = mPackageManager.getPackageArchiveInfo(apkFilePath, 0);

            if (info != null) {
                info.applicationInfo.sourceDir = apkFilePath;
                info.applicationInfo.publicSourceDir = apkFilePath;
                return info.applicationInfo.loadIcon(mPackageManager);
            }
        }
        return super.getIconDrawable(meta);
    }
}
