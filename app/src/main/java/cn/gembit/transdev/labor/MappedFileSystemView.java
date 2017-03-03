package cn.gembit.transdev.labor;

import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import cn.gembit.transdev.file.FilePath;

public class MappedFileSystemView implements FileSystemView {

    private FileSystemView mOriginalView;

    private List<FilePath> mOriginalPaths = new LinkedList<>();
    private List<FilePath> mMappedPaths = new LinkedList<>();

    public MappedFileSystemView(FileSystemView view,
                                List<FilePath> original, List<FilePath> mapped) {
        mOriginalView = view;
        mOriginalPaths = original;
        mMappedPaths = mapped;
    }

    private String mapAbsolutePath(String pathName) {
        FilePath path = new FilePath(pathName);
        for (int i = 0; i < mOriginalPaths.size(); i++) {
            FilePath original = mOriginalPaths.get(i);
            if (original.cover(path)) {
                return path.move(original, mMappedPaths.get(i)).pathString;
            }
        }
        return pathName;
    }

    private String restorePath(String pathName) throws FtpException {

        FilePath path;
        try {
            if (pathName.startsWith("/")) {
                path = new FilePath(pathName);
            } else {
                path = new FilePath(getWorkingDirectory().getAbsolutePath()).getRelative(pathName);
            }
        } catch (Exception e) {
            throw new FtpException();
        }

        if (path.pathString.equals("/")) {
            return path.pathString;
        }

        for (int i = 0; i < mMappedPaths.size(); i++) {
            FilePath mapped = mMappedPaths.get(i);
            if (mapped.cover(path)) {
                return path.move(mapped, mOriginalPaths.get(i)).pathString;
            }
        }
        throw new FtpException();
    }

    @Override
    public FtpFile getHomeDirectory() throws FtpException {
        return new MappedFtpFile(mOriginalView.getHomeDirectory());
    }

    @Override
    public FtpFile getWorkingDirectory() throws FtpException {
        return new MappedFtpFile(mOriginalView.getWorkingDirectory());
    }

    @Override
    public boolean changeWorkingDirectory(String dir) throws FtpException {
        return mOriginalView.changeWorkingDirectory(restorePath(dir));
    }

    @Override
    public FtpFile getFile(String fileName) throws FtpException {
        return new MappedFtpFile(mOriginalView.getFile(restorePath(fileName)));
    }

    @Override
    public boolean isRandomAccessible() throws FtpException {
        return mOriginalView.isRandomAccessible();
    }

    @Override
    public void dispose() {
        mOriginalView.dispose();
    }


    private class MappedFtpFile implements FtpFile {

        private FtpFile mOriginalFile;

        private MappedFtpFile(FtpFile file) {
            mOriginalFile = file;
        }

        @Override
        public String getAbsolutePath() {
            return mapAbsolutePath(mOriginalFile.getAbsolutePath());
        }

        @Override
        public String getName() {
            int index = mOriginalPaths.indexOf(new FilePath(mOriginalFile.getAbsolutePath()));
            if (index >= 0) {
                String mappedPathName = mMappedPaths.get(index).pathString;
                return mappedPathName.substring(1);
            } else {
                return mOriginalFile.getName();
            }
        }

        @Override
        public boolean isHidden() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return mOriginalFile.isDirectory();
        }

        @Override
        public boolean isFile() {
            return mOriginalFile.isFile();
        }

        @Override
        public boolean doesExist() {
            return mOriginalFile.doesExist();
        }

        @Override
        public boolean isReadable() {
            return mOriginalFile.isReadable();
        }

        @Override
        public boolean isWritable() {
            return mOriginalFile.isWritable();
        }

        @Override
        public boolean isRemovable() {
            return mOriginalFile.isRemovable();
        }

        @Override
        public String getOwnerName() {
            return mOriginalFile.getOwnerName();
        }

        @Override
        public String getGroupName() {
            return mOriginalFile.getGroupName();
        }

        @Override
        public int getLinkCount() {
            return mOriginalFile.getLinkCount();
        }

        @Override
        public long getLastModified() {
            return mOriginalFile.getLastModified();
        }

        @Override
        public boolean setLastModified(long l) {
            return mOriginalFile.setLastModified(l);
        }

        @Override
        public long getSize() {
            return mOriginalFile.getSize();
        }

        @Override
        public Object getPhysicalFile() {
            return mOriginalFile.getPhysicalFile();
        }

        @Override
        public boolean mkdir() {
            return mOriginalFile.mkdir();
        }

        @Override
        public boolean delete() {
            return mOriginalFile.delete();
        }

        @Override
        public boolean move(FtpFile ftpFile) {
            return mOriginalFile.move(((MappedFtpFile) ftpFile).mOriginalFile);
        }

        @Override
        public List<? extends FtpFile> listFiles() {
            List<MappedFtpFile> list = new LinkedList<>();
            if (!mOriginalFile.getAbsolutePath().equals("/")) {
                for (FtpFile ftpFile : mOriginalFile.listFiles()) {
                    list.add(new MappedFtpFile(ftpFile));
                }
            } else {
                for (FilePath path : mOriginalPaths) {
                    FtpFile file;
                    try {
                        file = mOriginalView.getFile(path.pathString);
                    } catch (FtpException e) {
                        file = null;
                    }
                    if (file != null && file.doesExist()) {
                        list.add(new MappedFtpFile(file));
                    }
                }
            }
            return list;
        }

        @Override
        public OutputStream createOutputStream(long l) throws IOException {
            return mOriginalFile.createOutputStream(l);
        }

        @Override
        public InputStream createInputStream(long l) throws IOException {
            return mOriginalFile.createInputStream(l);
        }
    }
}