package cn.gembit.transdev.ui;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FileOpener;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;
import cn.gembit.transdev.util.GlobalClipboard;
import cn.gembit.transdev.util.ServerWrapper;
import cn.gembit.transdev.widgets.BottomDialogBuilder;
import cn.gembit.transdev.widgets.InputDialog;

public class ServerExplorerFragment extends LocalExplorerFragment {

    private ServerWrapper mServer;

    private String mTitle;
    private String mUsername;

    public static ServerExplorerFragment newInstance(String title, String username) {
        ServerExplorerFragment fragment = new ServerExplorerFragment();
        fragment.mTitle = title;
        fragment.mUsername = username;
        return fragment;
    }

    public String getUsername() {
        return mUsername;
    }

    @Override
    protected void startUp() {
        mServer = ServerWrapper.getSingleton();
        setTitle(mTitle);
        setRootDir(mServer.getPhysicalRoot());
        changeDir(getRootDir());
    }

    @Override
    protected void changeDir(FilePath newPath) {
        if (newPath == null) {
            return;
        }
        if (!mServer.isInsideUserFileSystem(mUsername, newPath)) {
            notifyDirChanged(getRootDir(), mServer.listUserRoot(mUsername));
        } else {
            super.changeDir(newPath);
        }
    }

    @Override
    protected void enter(FileMeta meta) {
        if (getCurDir().equals(getRootDir())) {
            FilePath path = mServer.getPhysicalFilePath(mUsername, meta.name);
            File file = new File(path.pathString);
            if (!file.exists()) {
                BottomDialogBuilder.make(getContext(), "文件（夹）不存在").show();
            } else if (file.isDirectory()) {
                changeDir(path);
            } else {
                FileOpener.open(file, getContext());
            }
        } else {
            super.enter(meta);
        }
    }

    @Override
    protected void moreAction(FileMeta meta) {
        if (getCurDir().equals(getRootDir())) {
            FilePath path = mServer.getPhysicalFilePath(mUsername, meta.name);
            File file = new File(path.pathString);
            if (file.isFile()) {
                FileOpener.openAs(file, getContext());
            }
        } else {
            super.moreAction(meta);
        }
    }

    @Override
    protected void newFile(boolean isDir) {
        if (getCurDir().equals(getRootDir())) {
            BottomDialogBuilder.make(getContext(),
                    "此为共享文件列表，不能进行新建操作，请将本地文件复制于此进行共享").show();
        } else {
            super.newFile(isDir);
        }
    }

    @Override
    protected void delete(final Collection<FileMeta> allSelected) {
        if (getCurDir().equals(getRootDir())) {
            new AlertDialog.Builder(getContext())
                    .setCancelable(false)
                    .setMessage("删除这" + allSelected.size() + "项共享吗？（不会删除原文件）")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            List<FileMeta> allDeleted = new ArrayList<>(allSelected.size());
                            for (FileMeta meta : allSelected) {
                                if (mServer.removeMapRecord(mUsername, meta.name)
                                        == ServerWrapper.RECORD_SUCCESS) {
                                    allDeleted.add(meta);
                                }
                            }
                            removeItemView(allDeleted);

                            int failed = allSelected.size() - allDeleted.size();
                            if (failed > 0) {
                                BottomDialogBuilder.make(
                                        getContext(), "有" + failed + "项删除失败").show();
                            }
                        }
                    })
                    .show();
        } else {
            super.delete(allSelected);
        }
    }

    @Override
    protected void rename(final FileMeta oldMeta) {
        if (getCurDir().equals(getRootDir())) {
            int extLen = FileType.getNameExtension(oldMeta.name).length();
            new InputDialog(getContext(), "更换共享名称不会改变其本身名称", oldMeta.name, 0,
                    extLen == 0 ? oldMeta.name.length() : oldMeta.name.length() - extLen - 1) {
                @Override
                public String checkOut(String input) {
                    if (input.equals(oldMeta.name)) {
                        return "名称未变";
                    }
                    switch (mServer.renameMapRecord(mUsername, oldMeta.name, input)) {
                        case ServerWrapper.RECORD_NAME_ILLEGAL:
                            return "名称含有非法字符";
                        case ServerWrapper.RECORD_NAME_CONFLICT:
                            return "名称和已有项冲突";
                        case ServerWrapper.RECORD_SUCCESS:
                            modifyItemView(oldMeta, oldMeta.getRenamed(input));
                            return null;
                        default:
                            return "重命名失败";
                    }
                }
            }.show();
        } else {
            super.rename(oldMeta);
        }
    }

    @Override
    protected void copyOrCut(Collection<FileMeta> allSelected, boolean isToCopy) {
        if (getCurDir().equals(getRootDir())) {
            BottomDialogBuilder.make(getContext(), "不能对共享文件（夹）列表进行复制或者剪切").show();
        } else {
            super.copyOrCut(allSelected, isToCopy);
        }
    }

    @Override
    protected void paste() {
        if (getCurDir().equals(getRootDir())) {
            GlobalClipboard.Source source = GlobalClipboard.getOut();

            if (source instanceof GlobalClipboard.Source.Local) {
                GlobalClipboard.Source.Local theSource = (GlobalClipboard.Source.Local) source;
                if (!theSource.isToCopy) {
                    Toast.makeText(getContext(),
                            "对于添加文件共享，剪切操作不会移除原文件", Toast.LENGTH_SHORT).show();
                }

                int failed = 0;
                int renamed = 0;
                for (FileMeta fileMeta : theSource.fileMetaCollection) {
                    String mappedName = fileMeta.name;
                    GlobalClipboard.NameDuplicateHandler.reset(mappedName);

                    do {
                        int result = mServer.createMapRecord(mUsername,
                                theSource.dir.getChild(fileMeta.name), mappedName);
                        if (result == ServerWrapper.RECORD_SUCCESS) {
                            addItemView(fileMeta.getRenamed(mappedName));
                            if (GlobalClipboard.NameDuplicateHandler.hasGottenName()) {
                                renamed++;
                            }
                            break;
                        }
                        if (result == ServerWrapper.RECORD_NAME_CONFLICT) {
                            mappedName = GlobalClipboard.NameDuplicateHandler.getNewName();
                            continue;
                        }
                        failed++;
                        break;
                    } while (true);
                }

                String output = "";
                if (failed > 0) {
                    output += "有" + failed + "项添加共享失败，" +
                            "请检查其是不是重复添加，或者已添加了它的父目录、子目录";
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
                BottomDialogBuilder.make(getContext(), "只能共享本地文件").show();
            }
        } else {
            super.paste();
        }
    }
}
