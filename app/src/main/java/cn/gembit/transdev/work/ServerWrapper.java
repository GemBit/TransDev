package cn.gembit.transdev.work;

import android.content.SharedPreferences;
import android.os.Environment;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerConfigurationException;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FileSystemFactory;
import org.apache.ftpserver.ftplet.FileSystemView;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.User;
import org.apache.ftpserver.listener.Listener;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cn.gembit.transdev.app.MyApp;
import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;

@SuppressWarnings("WeakerAccess")
public class ServerWrapper {

    public final static int RECORD_SUCCESS = 0;
    public final static int RECORD_USER_ILLEGAL = 1;
    public final static int RECORD_NAME_ILLEGAL = 2;
    public final static int RECORD_NAME_CONFLICT = 3;
    public final static int RECORD_PATH_CONFLICT = 4;
    public final static int RECORD_NOT_FOUND = 5;

    private final static int PREFERRED_PORT = 2222;

    private final static Map<String, UserMeta> USER_META_MAP = new HashMap<>();

    private static ServerWrapper sSingleton;
    private static FilePath sPhysicalRoot;
    private static FilePath sFtpRoot;

    private FtpServer mFtpServer;

    private String mIP;
    private int mPort = -1;

    private ServerWrapper() {
    }

    public static ServerWrapper getSingleton() {
        if (sSingleton == null) {
            synchronized (ServerWrapper.class) {
                if (sSingleton == null) {
                    sSingleton = new ServerWrapper();
                    sPhysicalRoot = new FilePath(Environment.getExternalStorageDirectory().getPath());
                    sFtpRoot = new FilePath("/");
                    ConfigSaver.restoreAllUsers();
                }
            }
        }
        return sSingleton;
    }

    public FilePath getPhysicalRoot() {
        return sPhysicalRoot;
    }

    public Collection<FileMeta> listUserRoot(String username) {
        UserMeta userMeta = USER_META_MAP.get(username);
        int size = userMeta.mPhysicalPaths.size();
        ArrayList<FileMeta> fileMetaList = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            File file = new File(userMeta.mPhysicalPaths.get(i).pathString);
            fileMetaList.add(new FileMeta(
                    file.isDirectory(),
                    userMeta.mMappedPaths.get(i).pathString.substring(1),
                    file.length(),
                    file.lastModified()
            ));
        }
        return fileMetaList;
    }

    public boolean isInsideUserFileSystem(String username, FilePath physicalPath) {
        for (FilePath shared : USER_META_MAP.get(username).mPhysicalPaths) {
            if (shared.cover(physicalPath)) {
                return true;
            }
        }
        return false;
    }

    public FilePath getPhysicalFilePath(String username, String mappedName) {
        UserMeta userMeta = USER_META_MAP.get(username);
        for (int i = 0; i < userMeta.mMappedPaths.size(); i++) {
            if (userMeta.mMappedPaths.get(i).pathString.substring(1).equals(mappedName)) {
                return userMeta.mPhysicalPaths.get(i);
            }
        }
        return null;
    }

    public synchronized void start() {
        stop();

        if ((mIP = ConnectionBroadcast.tryOutMyIP()) == null) {
            return;
        }

        FtpServerFactory serverFactory = new FtpServerFactory();

        final FileSystemFactory originalFileSystemFactory = serverFactory.getFileSystem();

        serverFactory.setFileSystem(new FileSystemFactory() {
            @Override
            public FileSystemView createFileSystemView(User user) throws FtpException {
                UserMeta meta = USER_META_MAP.get(user.getName());
                if (meta == null) {
                    return null;
                }
                FileSystemView view = originalFileSystemFactory.createFileSystemView(user);
                if (meta.confined) {
                    return new MappedFileSystemView(view, meta.mOriginalPaths, meta.mMappedPaths);
                } else {
                    return view;
                }
            }
        });

        for (UserMeta meta : USER_META_MAP.values()) {
            BaseUser user = new BaseUser();
            user.setName(meta.username);
            if (meta.password != null && !meta.password.isEmpty()) {
                user.setPassword(meta.password);
            }
            user.setEnabled(true);
            user.setHomeDirectory(sPhysicalRoot.pathString);
            user.setMaxIdleTime(0);

            List<Authority> authorities = new LinkedList<>();
            if (meta.writable) {
                authorities.add(new WritePermission());
            }
            authorities.add(new ConcurrentLoginPermission(0, 0));
            authorities.add(new TransferRatePermission(0, 0));
            user.setAuthorities(authorities);

            try {
                serverFactory.getUserManager().save(user);
            } catch (FtpException e) {
                mPort = -1;
                return;
            }
        }

        final ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(PREFERRED_PORT);
        listenerFactory.setIdleTimeout(0);

        while (true) {
            serverFactory.setListeners(new HashMap<String, Listener>(1) {{
                put("default", listenerFactory.createListener());
            }});
            mFtpServer = serverFactory.createServer();
            try {
                mFtpServer.start();
                mPort = listenerFactory.getPort();
                return;
            } catch (FtpServerConfigurationException e1) {
                listenerFactory.setPort(listenerFactory.getPort() + 1);
            } catch (FtpException e2) {
                mPort = -1;
                return;
            }
        }
    }

    public synchronized void stop() {
        if (mFtpServer != null && !mFtpServer.isStopped()) {
            mFtpServer.stop();
            mIP = null;
            mPort = -1;
        }
    }

    public String getIP() {
        return mIP == null ? "N/A" : mIP;
    }

    public int getPort() {
        return mPort;
    }

    public boolean createUser(UserMeta meta) {
        if (USER_META_MAP.containsKey(meta.username)) {
            return false;
        }
        if (meta.confined) {
            meta.mPhysicalPaths = new LinkedList<>();
            meta.mOriginalPaths = new LinkedList<>();
            meta.mMappedPaths = new LinkedList<>();
        }
        USER_META_MAP.put(meta.username, meta);
        ConfigSaver.createUser(meta);
        return true;
    }

    public void removeUser(String username) {
        USER_META_MAP.remove(username);
        ConfigSaver.removeUser(username);
    }

    public boolean modifyUser(String oldUsername, UserMeta newMeta) {
        if (!newMeta.username.equals(oldUsername) && USER_META_MAP.containsKey(newMeta.username)) {
            return false;
        }

        UserMeta oldMeta = USER_META_MAP.get(oldUsername);
        USER_META_MAP.remove(oldMeta.username);
        USER_META_MAP.put(newMeta.username, newMeta);
        ConfigSaver.removeUser(oldMeta.username);
        ConfigSaver.createUser(newMeta);

        if (newMeta.confined) {
            if (oldMeta.confined) {
                newMeta.mPhysicalPaths = oldMeta.mPhysicalPaths;
                newMeta.mOriginalPaths = oldMeta.mOriginalPaths;
                newMeta.mMappedPaths = oldMeta.mMappedPaths;

                for (int i = 0; i < newMeta.mOriginalPaths.size(); i++) {
                    ConfigSaver.createRecord(newMeta.username, newMeta.mOriginalPaths.get(i),
                            newMeta.mMappedPaths.get(i));
                }
            } else {
                newMeta.mPhysicalPaths = new LinkedList<>();
                newMeta.mOriginalPaths = new LinkedList<>();
                newMeta.mMappedPaths = new LinkedList<>();
            }
        }
        return true;
    }

    public Map<String, UserMeta> getAllUsers() {
        return Collections.unmodifiableMap(USER_META_MAP);
    }

    public int createMapRecord(String username, FilePath physicalPath, String mappedName) {
        UserMeta meta = USER_META_MAP.get(username);
        if (meta == null || !meta.confined) {
            return RECORD_USER_ILLEGAL;
        }

        if (mappedName.indexOf('/') >= 0) {
            return RECORD_NAME_ILLEGAL;
        }

        FilePath mappedPath = new FilePath("/" + mappedName);

        if (meta.mMappedPaths.contains(mappedPath)) {
            return RECORD_NAME_CONFLICT;
        }

        FilePath originalPath = physicalPath.move(sPhysicalRoot, sFtpRoot);
        for (FilePath path : meta.mOriginalPaths) {
            if (path.cover(originalPath) || originalPath.cover(path)) {
                return RECORD_PATH_CONFLICT;
            }
        }

        meta.mPhysicalPaths.add(physicalPath);
        meta.mOriginalPaths.add(originalPath);
        meta.mMappedPaths.add(mappedPath);
        ConfigSaver.createRecord(username, originalPath, mappedPath);
        return RECORD_SUCCESS;
    }

    public int removeMapRecord(String username, String mappedName) {
        UserMeta meta = USER_META_MAP.get(username);
        if (meta == null) {
            return RECORD_USER_ILLEGAL;
        }

        FilePath mappedPath = new FilePath("/" + mappedName);
        int where = meta.mMappedPaths.indexOf(mappedPath);
        if (where < 0) {
            return RECORD_NOT_FOUND;
        }

        meta.mPhysicalPaths.remove(where);
        meta.mOriginalPaths.remove(where);
        meta.mMappedPaths.remove(where);
        ConfigSaver.removeRecord(username, mappedPath);
        return RECORD_SUCCESS;
    }

    public int renameMapRecord(String username, String oldMappedName, String newMappedName) {
        UserMeta meta = USER_META_MAP.get(username);
        if (meta == null || !meta.confined) {
            return RECORD_USER_ILLEGAL;
        }

        if (newMappedName.indexOf('/') >= 0) {
            return RECORD_NAME_ILLEGAL;
        }

        FilePath oldMappedPath = new FilePath("/" + oldMappedName);
        FilePath newMappedPath = new FilePath("/" + newMappedName);
        int where = meta.mMappedPaths.indexOf(oldMappedPath);

        if (where >= 0) {
            if (meta.mMappedPaths.contains(newMappedPath) && !oldMappedName.equals(newMappedName)) {
                return RECORD_NAME_CONFLICT;
            }
            meta.mMappedPaths.set(where, newMappedPath);
            ConfigSaver.removeRecord(username, oldMappedPath);
            ConfigSaver.createRecord(username, meta.mOriginalPaths.get(where), newMappedPath);
            return RECORD_SUCCESS;
        } else {
            return RECORD_NOT_FOUND;
        }
    }

    public static class UserMeta {
        public final String username;
        public final String password;
        public final boolean writable;
        public final boolean confined;

        private List<FilePath> mPhysicalPaths;
        private List<FilePath> mOriginalPaths;
        private List<FilePath> mMappedPaths;

        public UserMeta(String name, String password, boolean writable, boolean confined) {
            this.username = name;
            this.password = password;
            this.writable = writable;
            this.confined = confined;
        }
    }


    private static class ConfigSaver {

        private static final String USERS_CONFIG_FILE = "ServerBookmark.Users";
        private static final String MAP_CONFIG_FILE = "ServerBookmark.PathMap";

        private static void createUser(UserMeta meta) {
            String writable = meta.writable ? "1" : "0";
            String confined = meta.confined ? "1" : "0";
            String password = meta.password == null ? "" : meta.password;
            MyApp.getSharedPreferences(USERS_CONFIG_FILE).edit()
                    .putString(meta.username, writable + confined + password)
                    .apply();
        }

        private static void removeUser(String username) {
            MyApp.getSharedPreferences(USERS_CONFIG_FILE).edit()
                    .remove(username)
                    .apply();

            SharedPreferences preferences = MyApp.getSharedPreferences(MAP_CONFIG_FILE);
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (key.lastIndexOf('/') == username.length()) {
                    editor.remove(key);
                }
            }
            editor.apply();
        }

        private static void createRecord(String username, FilePath originalPath, FilePath mappedPath) {
            MyApp.getSharedPreferences(MAP_CONFIG_FILE).edit()
                    .putString(username + mappedPath.pathString, originalPath.pathString)
                    .apply();
        }

        private static void removeRecord(String username, FilePath mappedPath) {
            MyApp.getSharedPreferences(MAP_CONFIG_FILE).edit()
                    .remove(username + mappedPath.pathString)
                    .apply();
        }

        private static void restoreAllUsers() {
            Map<String, ?> users = MyApp.getSharedPreferences(USERS_CONFIG_FILE).getAll();
            Map<String, ?> map = MyApp.getSharedPreferences(MAP_CONFIG_FILE).getAll();

            for (String username : users.keySet()) {
                String info = (String) users.get(username);
                UserMeta meta = new UserMeta(
                        username,
                        info.substring(2),
                        info.charAt(0) == '1',
                        info.charAt(1) == '1');
                if (meta.confined) {
                    meta.mPhysicalPaths = new LinkedList<>();
                    meta.mOriginalPaths = new LinkedList<>();
                    meta.mMappedPaths = new LinkedList<>();
                }
                USER_META_MAP.put(username, meta);
            }
            for (String mappedName : map.keySet()) {
                int index = mappedName.lastIndexOf('/');
                UserMeta meta = USER_META_MAP.get(mappedName.substring(0, index));
                meta.mMappedPaths.add(new FilePath(mappedName.substring(index)));
                FilePath originalPath = new FilePath((String) map.get(mappedName));
                meta.mOriginalPaths.add(originalPath);
                meta.mPhysicalPaths.add(originalPath.move(sFtpRoot, sPhysicalRoot));
            }
        }
    }
}
