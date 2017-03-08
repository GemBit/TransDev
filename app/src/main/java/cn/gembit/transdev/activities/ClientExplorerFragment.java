package cn.gembit.transdev.activities;

import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.widget.Toast;

import org.apache.commons.net.ftp.FTPClient;

import java.util.Collection;

import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;
import cn.gembit.transdev.work.ClientAction;
import cn.gembit.transdev.work.GlobalClipboard;
import cn.gembit.transdev.work.TaskService;
import cn.gembit.transdev.widgets.BottomDialogBuilder;
import cn.gembit.transdev.widgets.InputDialog;

public class ClientExplorerFragment extends ExplorerFragment {

    private FTPClient mClient;

    private ClientAction.Argument.Connect mConnectArg;

    public static ClientExplorerFragment newInstance(
            String title, ClientAction.Argument.Connect connectArg) {
        ClientExplorerFragment fragment = new ClientExplorerFragment();
        fragment.setTitle(title);
        fragment.mConnectArg = connectArg;
        return fragment;
    }

    @Override
    protected void startUp() {
        lockFragment(true);

        new ClientAction() {
            @Override
            protected Argument onCreateArgument() {
                return mConnectArg;
            }

            @Override
            protected void onResultOut(Result result) {
                if (getContext() == null) {
                    return;
                }
                lockFragment(false);
                Result.Connect theResult = (Result.Connect) result;
                if (!theResult.error) {
                    mClient = theResult.ftpClient;
                    setRootDir(null);
                    FilePath curDir = getCurDir();
                    changeDir(curDir == null ? theResult.initialPath : curDir);
                } else {
                    BottomDialogBuilder.make(getContext(), "连接失败").show();
                }
            }
        }.start(true);
    }

    @Override
    public void endUp() {
        if (mClient != null) {
            new ClientAction() {
                @Override
                protected Argument onCreateArgument() {
                    Argument.Disconnect argument = new Argument.Disconnect();
                    argument.client = mClient;
                    return argument;
                }

                @Override
                protected void onResultOut(Result result) {
                }
            }.start(true);
        }
    }

    @Override
    protected void newFile(final boolean isDir) {
        String title = isDir ? "输入新建文件夹名称" : "输入新建文件名称";
        new InputDialog(getContext(), title) {
            @Override
            public String checkOut(final String input) {
                lockFragment(true);

                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        Argument.NewFile argument = new Argument.NewFile();
                        argument.ftpClient = mClient;
                        argument.path = getCurDir().getChild(input);
                        argument.isDir = isDir;
                        return argument;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                        if (getContext() == null) {
                            return;
                        }
                        lockFragment(false);
                        if (((Result.NewFile) result).error) {
                            BottomDialogBuilder.make(getContext(),
                                    isDir ? "新建文件夹失败" : "新建文件失败").show();
                        } else {
                            addItemView(new FileMeta(isDir, input, 0, System.currentTimeMillis()));
                        }
                    }
                }.start(true);
                return null;
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
                        new ClientAction() {
                            @Override
                            protected Argument onCreateArgument() {
                                Argument.Delete argument = new Argument.Delete();
                                argument.ftpClient = mClient;
                                argument.dir = getCurDir();
                                argument.allDeleted = allSelected;
                                return argument;
                            }

                            @Override
                            protected void onResultOut(Result result) {
                                if (getContext() == null) {
                                    return;
                                }
                                lockFragment(false);
                                Result.Delete theResult = (Result.Delete) result;
                                removeItemView(theResult.allDeleted);
                                int failed = allSelected.size() - theResult.allDeleted.size();
                                if (failed > 0) {
                                    BottomDialogBuilder.make(getContext(),
                                            "有" + failed + "项删除失败").show();
                                }
                            }
                        }.start(true);
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
            public String checkOut(final String input) {
                if (input.equals(oldMeta.name)) {
                    return "名称未变";
                }
                lockFragment(true);

                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        Argument.Rename argument = new Argument.Rename();
                        argument.ftpClient = mClient;
                        argument.oldPath = getCurDir().getChild(oldMeta.name);
                        argument.newPath = getCurDir().getChild(input);
                        return argument;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                        if (getContext() == null) {
                            return;
                        }
                        lockFragment(false);
                        if (((Result.Rename) result).error) {
                            BottomDialogBuilder.make(getContext(), "重命名失败").show();
                        } else {
                            modifyItemView(oldMeta, oldMeta.getRenamed(input));
                        }
                    }
                }.start(true);
                return null;
            }
        }.show();
    }

    @Override
    protected void copyOrCut(Collection<FileMeta> allSelected, boolean isToCopy) {
        GlobalClipboard.Source.Client source = new GlobalClipboard.Source.Client();
        source.isToCopy = isToCopy;
        source.dir = getCurDir();
        source.fileMetaCollection = allSelected;
        source.connectArg = mConnectArg;
        GlobalClipboard.putIn(source);
    }

    @Override
    protected void paste() {
        final GlobalClipboard.Source source = GlobalClipboard.getOut();

        if (source instanceof GlobalClipboard.Source.Client) {
            final GlobalClipboard.Source.Client theSource = (GlobalClipboard.Source.Client) source;

            if (!source.isToCopy && (
                    theSource.connectArg == mConnectArg || (
                            theSource.connectArg.address.equals(mConnectArg.address) &&
                                    theSource.connectArg.port == mConnectArg.port &&
                                    theSource.connectArg.username.equals(mConnectArg.username) &&
                                    theSource.connectArg.password.equals(mConnectArg.password) &&
                                    theSource.connectArg.encoding.equals(mConnectArg.encoding)))) {

                if (source.dir.equals(getCurDir())) {
                    BottomDialogBuilder.make(getContext(), "不能移动到原路径").show();
                    return;
                }

                new ClientAction() {
                    @Override
                    protected Argument onCreateArgument() {
                        Argument.Move argument = new Argument.Move();
                        argument.ftpClient = mClient;
                        argument.oldDir = source.dir;
                        argument.newDir = getCurDir();
                        argument.allToMove = source.fileMetaCollection;
                        return argument;
                    }

                    @Override
                    protected void onResultOut(Result result) {
                        if (getContext() == null) {
                            return;
                        }
                        Result.Move theResult = (Result.Move) result;
                        for (FileMeta meta : theResult.allMoved) {
                            addItemView(meta);
                        }
                        String output = "";
                        if (theResult.failed > 0) {
                            output += "有" + theResult.failed + "项移动失败";
                        }
                        if (theResult.renamed > 0) {
                            if (!output.isEmpty()) {
                                output += "\n";
                            }
                            output += "有" + theResult.renamed + "项已自动重命名";
                        }
                        if (!output.isEmpty()) {
                            BottomDialogBuilder.make(getContext(), output).show();
                        }
                    }
                }.start(true);
                return;
            }

        }
        GlobalClipboard.Destination.Client dest = new GlobalClipboard.Destination.Client();
        dest.dir = getCurDir();
        dest.connectArg = mConnectArg;
        TaskService.Task task = new TaskService.Task(source, dest);
        TaskService.createTask(getContext(), task);
    }

    @Override
    protected void changeDir(final FilePath newPath) {
        if (newPath == null) {
            startUp();
            return;
        }
        lockFragment(true);

        new ClientAction() {
            @Override
            protected Argument onCreateArgument() {
                Argument.List argument = new Argument.List();
                argument.ftpClient = mClient;
                argument.dir = newPath;
                return argument;
            }

            @Override
            protected void onResultOut(Result result) {
                if (getContext() == null) {
                    return;
                }
                lockFragment(false);
                Result.List theResult = (Result.List) result;
                if (theResult.allListed != null) {
                    notifyDirChanged(newPath, theResult.allListed);
                } else {
                    if (theResult.connectionBroken) {
                        startUp();
                    } else {
                        BottomDialogBuilder.make(getContext(), "无法读取目录").show();
                    }
                }
            }
        }.start(true);
    }

    @Override
    protected void enter(FileMeta meta) {
        if (meta.type == FileType.DIR) {
            changeDir(getCurDir().getChild(meta.name));
        } else {
            Toast.makeText(getContext(), "请先将文件复制到本地", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void moreAction(FileMeta meta) {
    }
}
