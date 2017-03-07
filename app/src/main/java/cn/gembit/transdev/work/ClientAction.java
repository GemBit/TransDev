package cn.gembit.transdev.work;

import android.os.Handler;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;

public abstract class ClientAction {

    public final static FTPFileFilter ONLY_CHILD_FILTER = new FTPFileFilter() {
        @Override
        public boolean accept(FTPFile ftpFile) {
            return ftpFile != null &&
                    !ftpFile.getName().equals(".") &&
                    !ftpFile.getName().equals("..");
        }
    };

    public void start(boolean async) {

        final Argument argument = onCreateArgument();
        if (argument == null) {
            return;
        }

        if (async) {
            final Handler handler = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final Result result = execute(argument);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            onResultOut(result);
                        }
                    });
                }
            }).start();
        } else {
            onResultOut(execute(argument));
        }
    }

    private Result execute(Argument argument) {
        Result result = null;
        if (argument instanceof Argument.Connect) {
            result = connect((Argument.Connect) argument);

        } else if (argument instanceof Argument.List) {
            result = list((Argument.List) argument);

        } else if (argument instanceof Argument.NewFile) {
            result = newFile((Argument.NewFile) argument);

        } else if (argument instanceof Argument.Delete) {
            result = delete((Argument.Delete) argument);

        } else if (argument instanceof Argument.Rename) {
            result = rename((Argument.Rename) argument);

        } else if (argument instanceof Argument.Move) {
            result = move((Argument.Move) argument);

        } else if (argument instanceof Argument.Disconnect) {
            result = disconnect((Argument.Disconnect) argument);
        }
        return result;
    }

    private Result.Connect connect(Argument.Connect argument) {

        Result.Connect result = new Result.Connect();
        result.ftpClient = new FTPClient();
        result.error = true;

        try {
            result.ftpClient.setConnectTimeout(10000);
            result.ftpClient.setControlEncoding(argument.encoding);
            result.ftpClient.connect(argument.address, argument.port);
            if (FTPReply.isPositiveCompletion(result.ftpClient.getReplyCode())) {
                if (result.ftpClient.login(argument.username, argument.password)) {
                    result.ftpClient.enterLocalPassiveMode();
                    result.ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                    result.ftpClient.setListHiddenFiles(true);
                    String workingDir = result.ftpClient.printWorkingDirectory();
                    result.initialPath = new FilePath(workingDir);
                    result.error = workingDir == null;
                }
            }
        } catch (IOException e) {
            if (result.ftpClient.isConnected()) {
                try {
                    result.ftpClient.disconnect();
                } catch (IOException ex) {
                    result.error = true;
                }
            }
        }
        return result;
    }

    private Result.List list(Argument.List argument) {
        Result.List result = new Result.List();
        FTPFile[] files = null;
        try {
            if (argument.ftpClient != null && argument.dir != null) {
                files = argument.ftpClient.listFiles((argument.dir.pathString), ONLY_CHILD_FILTER);
            }
        } catch (IOException e) {
            files = null;
        }

        if (files != null) {
            result.connectionBroken = false;
            result.allListed = new ArrayList<>(files.length);
            for (FTPFile file : files) {
                result.allListed.add(new FileMeta(
                        file.isDirectory(),
                        file.getName(),
                        file.getSize(),
                        file.getTimestamp().getTimeInMillis()));
            }
        } else {
            try {
                result.connectionBroken =
                        argument.ftpClient == null || !argument.ftpClient.sendNoOp();
            } catch (IOException e) {
                result.connectionBroken = true;
            }
        }
        return result;
    }

    private Result.NewFile newFile(Argument.NewFile argument) {
        Result.NewFile result = new Result.NewFile();
        try {
            if (argument.isDir) {
                result.error = !argument.ftpClient.makeDirectory(argument.path.pathString);
            } else {
                result.error = !argument.ftpClient.storeFile(argument.path.pathString,
                        new ByteArrayInputStream(new byte[0]));
            }
        } catch (IOException e) {
            result.error = true;
        }
        return result;
    }

    private Result.Delete delete(Argument.Delete argument) {
        Result.Delete result = new Result.Delete();
        result.allDeleted = new ArrayList<>(argument.allDeleted.size());

        for (FileMeta meta : argument.allDeleted) {
            if (deleteFile(argument.ftpClient, argument.dir.getChild(meta.name), meta.type == FileType.DIR)) {
                result.allDeleted.add(meta);
            }
        }
        return result;
    }

    private boolean deleteFile(FTPClient ftpClient, FilePath path, boolean isDir) {
        try {
            if (isDir) {
                boolean result = true;
                FTPFile[] ftpFiles = ftpClient.listFiles(path.pathString, ONLY_CHILD_FILTER);
                for (FTPFile ftpFile : ftpFiles) {
                    result &= deleteFile(ftpClient, path.getChild(ftpFile.getName()), ftpFile.isDirectory());
                }
                return result && ftpClient.removeDirectory(path.pathString);
            } else {
                return ftpClient.deleteFile(path.pathString);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private Result.Rename rename(Argument.Rename argument) {
        Result.Rename result = new Result.Rename();
        try {
            result.error = !argument.ftpClient.rename(
                    argument.oldPath.pathString, argument.newPath.pathString);
        } catch (IOException e) {
            result.error = true;
        }
        return result;
    }

    private Result.Move move(Argument.Move argument) {
        Result.Move result = new Result.Move();
        result.allMoved = new ArrayList<>(argument.allToMove.size());

        Argument.List listArg = new Argument.List();
        listArg.ftpClient = argument.ftpClient;
        listArg.dir = argument.newDir;
        Result.List listResult = list(listArg);

        if (listResult.allListed == null) {
            result.failed = argument.allToMove.size();
            return result;
        }

        ArrayList<String> existedNames = new ArrayList<>(listResult.allListed.size());
        for (FileMeta meta : listResult.allListed) {
            existedNames.add(meta.name);
        }

        for (FileMeta meta : argument.allToMove) {
            GlobalClipboard.NameDuplicateHandler.reset(meta.name);
            String newName = meta.name;
            while (existedNames.contains(newName)) {
                newName = GlobalClipboard.NameDuplicateHandler.getNewName();
            }
            try {
                if (argument.ftpClient.rename(
                        argument.oldDir.getChild(meta.name).pathString,
                        argument.newDir.getChild(newName).pathString)) {
                    result.allMoved.add(meta.getRenamed(newName));
                    if (GlobalClipboard.NameDuplicateHandler.hasGottenName()) {
                        result.renamed++;
                    }
                } else {
                    result.failed++;
                }
            } catch (IOException e) {
                result.failed++;
            }
        }
        return result;
    }

    private Result.Disconnect disconnect(Argument.Disconnect argument) {
        Result.Disconnect result = new Result.Disconnect();
        if (argument.ftpClient != null) {
            try {
                argument.ftpClient.logout();
                argument.ftpClient.disconnect();
                result.error = false;
            } catch (IOException e) {
                result.error = true;
            }
        }
        return result;
    }

    protected abstract Argument onCreateArgument();

    protected abstract void onResultOut(Result result);


    public interface Argument {
        class Connect implements Argument {
            public String address;
            public int port;
            public String username;
            public String password;
            public String encoding;
            public String alias;
        }

        class List implements Argument {
            public FTPClient ftpClient;
            public FilePath dir;
        }

        class NewFile implements Argument {
            public FTPClient ftpClient;
            public FilePath path;
            public boolean isDir;
        }

        class Delete implements Argument {
            public FTPClient ftpClient;
            public FilePath dir;
            public Collection<FileMeta> allDeleted;
        }

        class Rename implements Argument {
            public FTPClient ftpClient;
            public FilePath oldPath;
            public FilePath newPath;
        }

        class Move implements Argument {
            public FTPClient ftpClient;
            public Collection<FileMeta> allToMove;
            public FilePath oldDir;
            public FilePath newDir;
        }

        class Disconnect implements Argument {
            public FTPClient ftpClient;
        }
    }


    public interface Result {
        class Connect implements Result {
            public boolean error;
            public FilePath initialPath;
            public FTPClient ftpClient;
        }

        class List implements Result {
            public Collection<FileMeta> allListed;
            public boolean connectionBroken;
        }

        class NewFile implements Result {
            public boolean error;
        }

        class Delete implements Result {
            public Collection<FileMeta> allDeleted;
        }

        class Rename implements Result {
            public boolean error;
        }

        class Move implements Result {
            public Collection<FileMeta> allMoved;
            public int failed;
            public int renamed;
        }

        class Disconnect implements Result {
            public boolean error;
        }
    }
}
