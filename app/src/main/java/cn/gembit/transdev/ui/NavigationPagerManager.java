package cn.gembit.transdev.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import cn.gembit.transdev.R;
import cn.gembit.transdev.addition.MyApp;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.labor.ClientAction;
import cn.gembit.transdev.labor.ServerWrapper;
import cn.gembit.transdev.widgets.BottomDialogBuilder;

public class NavigationPagerManager implements NavigationView.OnNavigationItemSelectedListener {

    private final static String[] ENCODINGS;
    private final static Comparator<String> ENCODING_COMPAATOR;
    private final static int DEFAULT_ENCODING_INDEX;

    static {
        ENCODINGS = Charset.availableCharsets().keySet().toArray(new String[0]);
        ENCODING_COMPAATOR = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        };
        DEFAULT_ENCODING_INDEX = Arrays.binarySearch(ENCODINGS, "UTF-8", ENCODING_COMPAATOR);
    }

    private Context mContext;

    private ExplorerPagerAdapter mAdapter;
    private ViewPager mPager;
    private DrawerLayout mDrawer;

    private LocalBookmark mLocalBookmark;
    private ClientBookmark mClientBookmark;
    private ServerBookmark mServerBookmark;

    private Menu mMenu;
    private MenuItem mTransTask;
    private MenuItem mAddition;
    private MenuItem mAddLocal;
    private MenuItem mAddClient;
    private MenuItem mAddServer;
    private MenuItem mServerStatus;


    private LinkedList<MenuItem> mSessionMenuItems = new LinkedList<>();
    private LinkedList<ExplorerFragment> mSessionFragments = new LinkedList<>();


    public NavigationPagerManager(FragmentManager fm, DrawerLayout drawer,
                                  ViewPager pager, NavigationView navigation) {
        mAdapter = new ExplorerPagerAdapter(fm);
        mDrawer = drawer;
        mPager = pager;

        mPager.setAdapter(mAdapter);

        mContext = pager.getContext();

        mLocalBookmark = new LocalBookmark();
        mClientBookmark = new ClientBookmark();
        mServerBookmark = new ServerBookmark();

        mMenu = navigation.getMenu();
        mTransTask = mMenu.findItem(R.id.transTask);
        mAddition = mMenu.findItem(R.id.addition);
        mAddLocal = mMenu.findItem(R.id.addLocal);
        mAddClient = mMenu.findItem(R.id.addClient);
        mAddServer = mMenu.findItem(R.id.addServer);
        mServerStatus = mMenu.findItem(R.id.serverStatus);


        for (String key : mLocalBookmark.mBookmark.keySet()) {
            addBookmarkMenuItem(R.id.groupLocal, key);
        }
        for (String key : mClientBookmark.mBookmark.keySet()) {
            addBookmarkMenuItem(R.id.groupClient, key);
        }
        for (String key : mServerBookmark.mBookmark.keySet()) {
            addBookmarkMenuItem(R.id.groupServer, key);
        }

        SwitchCompat switchCompat = new SwitchCompat(mContext);
        switchCompat.setChecked(false);
        switchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                buttonView.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (isChecked) {
                            mServerBookmark.mServer.start();
                        } else {
                            mServerBookmark.mServer.stop();
                        }
                        buttonView.post(new Runnable() {
                            @Override
                            public void run() {
                                buttonView.setChecked(mServerBookmark.mServer.getPort() != -1);
                                buttonView.setEnabled(true);
                            }
                        });
                    }
                }).start();
            }
        });
        mServerStatus.setActionView(switchCompat);

        navigation.setNavigationItemSelectedListener(this);

        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mSessionMenuItems.get(position).setChecked(true);
            }
        });

        addSession(
                LocalExplorerFragment.newInstance("本地文件：默认存储器",
                        new FilePath(Environment.getExternalStorageDirectory().getPath())),
                R.drawable.ic_local,
                "默认存储器");
    }

    public void back() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            int index = mPager.getCurrentItem();
            if (index >= 0 && index < mSessionFragments.size()) {
                mSessionFragments.get(index).onBackPress();
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item == mTransTask) {
            mContext.startActivity(new Intent(mContext, TaskActivity.class));
        } else if (item == mAddition) {

        } else if (item == mAddLocal) {
            addOrModifyLocalBookmark(null);
        } else if (item == mAddClient) {
            addOrModifyClientBookmark(null);
        } else if (item == mAddServer) {
            addOrModifyServerBookmark(null);
        } else if (item == mServerStatus) {
            String msg;
            int port = mServerBookmark.mServer.getPort();
            if (port == -1) {
                msg = "未开启";
            } else {
                msg = "IP地址：" + mServerBookmark.mServer.getIP();
                msg += "\n端口" + port;
            }
            new AlertDialog.Builder(mContext)
                    .setCancelable(false)
                    .setTitle("本机共享状态")
                    .setMessage(msg)
                    .setPositiveButton("确定", null)
                    .show();

        } else {
            String name = item.getTitle().toString();

            switch (item.getGroupId()) {
                case R.id.groupLocal:
                    addSession(
                            LocalExplorerFragment.newInstance("本地文件：" + name,
                                    mLocalBookmark.mBookmark.get(name)),
                            R.drawable.ic_local,
                            name);
                    break;

                case R.id.groupClient:
                    addSession(
                            ClientExplorerFragment.newInstance("远程文件：" + name,
                                    mClientBookmark.mBookmark.get(name)),
                            R.drawable.ic_client,
                            name);
                    break;

                case R.id.groupServer:
                    if (!mServerBookmark.mBookmark.get(name).confined) {
                        BottomDialogBuilder.make(mContext,
                                "此账号具有全局访问权限，不可对其选择共享的文件").show();
                        break;
                    }
                    addSession(
                            ServerExplorerFragment.newInstance("已共享文件：" + name, name),
                            R.drawable.ic_server,
                            name);
                    break;

                case R.id.groupSession:
                    mPager.setCurrentItem(mSessionMenuItems.indexOf(item));
                    break;

                default:
                    break;
            }

            if (mDrawer.isDrawerOpen(GravityCompat.START)) {
                mDrawer.closeDrawer(GravityCompat.START);
            }
        }
        return true;
    }

    private void addSession(ExplorerFragment fragment, int iconResId, String menuItemTitle) {
        final MenuItem item;

        ImageView imageView = new ImageView(mContext);
        imageView.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_close));

        item = mMenu.add(
                R.id.groupSession, View.generateViewId(), mAddition.getOrder() + 1, menuItemTitle)
                .setIcon(iconResId)
                .setActionView(imageView)
                .setCheckable(true)
                .setChecked(true);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSession(mSessionMenuItems.indexOf(item));
            }
        });

        mSessionMenuItems.add(item);
        mSessionFragments.add(fragment);
        mAdapter.notifyDataSetChanged();
        mPager.setCurrentItem(mSessionFragments.size() - 1);
    }

    private void closeSession(int index) {
        if (index >= 0 && index < mSessionFragments.size()) {
            mMenu.removeItem(mSessionMenuItems.remove(index).getItemId());
            mSessionFragments.remove(index).endUp();
            mAdapter.notifyDataSetChanged();
            if (mSessionMenuItems.size() > 0) {
                mSessionMenuItems.get(mPager.getCurrentItem()).setChecked(true);
            }
        }
    }

    private void addBookmarkMenuItem(int grpId, String name) {
        final MenuItem item;
        ImageView imageView = new ImageView(mContext);
        imageView.setImageDrawable(ContextCompat.getDrawable(mContext, R.drawable.ic_config));

        switch (grpId) {
            case R.id.groupLocal:
                item = mMenu.add(
                        grpId, View.generateViewId(), mAddLocal.getOrder() - 1, name)
                        .setIcon(R.drawable.ic_local)
                        .setActionView(imageView);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addOrModifyLocalBookmark(item);
                    }
                });
                break;

            case R.id.groupClient:
                item = mMenu.add(
                        grpId, View.generateViewId(), mAddClient.getOrder() - 1, name)
                        .setIcon(R.drawable.ic_client)
                        .setActionView(imageView);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addOrModifyClientBookmark(item);
                    }
                });
                break;

            case R.id.groupServer:
                item = mMenu.add(
                        grpId, View.generateViewId(), mAddServer.getOrder() - 1, name)
                        .setIcon(R.drawable.ic_server)
                        .setActionView(imageView);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addOrModifyServerBookmark(item);
                    }
                });
                break;

            default:
                break;
        }
    }

    private void addOrModifyLocalBookmark(final MenuItem item) {
        final String oldName = item == null ? null : item.getTitle().toString();

        final View view = View.inflate(mContext, R.layout.dialog_local_bookmark, null);
        final EditText edtName = (EditText) view.findViewById(R.id.edtName);
        final EditText edtRoot = (EditText) view.findViewById(R.id.edtRoot);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setCancelable(false)
                .setView(view)
                .setNeutralButton("删除", null)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", null)
                .show();

        if (oldName != null) {
            edtName.setText(oldName);
            edtRoot.setText(mLocalBookmark.mBookmark.get(oldName).pathString);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mLocalBookmark.remove(oldName);
                            mMenu.removeItem(item.getItemId());
                            dialog.dismiss();
                        }
                    });
        } else {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.GONE);
        }

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String name = edtName.getText().toString();
                        String root = edtRoot.getText().toString();
                        if (name.isEmpty()) {
                            Toast.makeText(mContext, "名称不可为空", Toast.LENGTH_SHORT).show();
                        } else if (root.isEmpty()) {
                            Toast.makeText(mContext, "路径不可为空", Toast.LENGTH_SHORT).show();

                        } else {

                            if (item == null && mLocalBookmark.create(name, new FilePath(root))) {
                                Toast.makeText(mContext, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupLocal, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    mLocalBookmark.modify(oldName, name, new FilePath(root))) {
                                Toast.makeText(mContext, "编辑成功", Toast.LENGTH_SHORT).show();
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                Toast.makeText(mContext, "名称已使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void addOrModifyClientBookmark(final MenuItem item) {
        final String oldName = item == null ? null : item.getTitle().toString();

        final View view = View.inflate(mContext, R.layout.dialog_client_bookmark, null);
        final AppCompatSpinner spnEncoding = (AppCompatSpinner) view.findViewById(R.id.spnEncoding);
        final EditText edtAddress = (EditText) view.findViewById(R.id.edtAddress);
        final EditText edtPort = (EditText) view.findViewById(R.id.edtPort);
        final EditText edtUsername = (EditText) view.findViewById(R.id.edtUsername);
        final EditText edtPassword = (EditText) view.findViewById(R.id.edtPassword);
        final EditText edtName = (EditText) view.findViewById(R.id.edtName);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setCancelable(false)
                .setView(view)
                .setNeutralButton("删除", null)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", null)
                .show();

        spnEncoding.setAdapter(new ArrayAdapter<>(
                mContext, R.layout.support_simple_spinner_dropdown_item, ENCODINGS));

        if (item != null) {
            ClientAction.Argument.Connect connectArg = mClientBookmark.mBookmark.get(oldName);

            int index = Arrays.binarySearch(ENCODINGS, connectArg.encoding, ENCODING_COMPAATOR);
            if (index >= 0) {
                spnEncoding.setSelection(index);
            }
            edtAddress.setText(connectArg.address);
            edtPort.setText(String.valueOf(connectArg.port));
            edtUsername.setText(connectArg.username);
            edtPassword.setText(connectArg.password);
            edtName.setText(oldName);


            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mClientBookmark.remove(oldName);
                            mMenu.removeItem(item.getItemId());
                            dialog.dismiss();
                        }
                    });
        } else {
            spnEncoding.setSelection(DEFAULT_ENCODING_INDEX);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.GONE);
        }

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String encoding = (String) spnEncoding.getSelectedItem();
                        String address = edtAddress.getText().toString();
                        int port;
                        try {
                            port = Integer.parseInt(edtPort.getText().toString());
                        } catch (NumberFormatException e) {
                            port = -1;
                        }
                        String username = edtUsername.getText().toString();
                        String password = edtPassword.getText().toString();
                        String name = edtName.getText().toString();
                        if (name.isEmpty()) {
                            name = address;
                            edtName.setText(name);
                        }

                        if (address.isEmpty()) {
                            Toast.makeText(mContext, "地址不可为空", Toast.LENGTH_SHORT).show();
                        } else if (port == -1) {
                            Toast.makeText(mContext, "端口必须为数字", Toast.LENGTH_SHORT).show();
                        } else if (username.isEmpty()) {
                            Toast.makeText(mContext, "用户名不可为空", Toast.LENGTH_SHORT).show();
                        } else {

                            ClientAction.Argument.Connect newConnectArg =
                                    new ClientAction.Argument.Connect();
                            newConnectArg.encoding = encoding;
                            newConnectArg.address = address;
                            newConnectArg.port = port;
                            newConnectArg.username = username;
                            newConnectArg.password = password;

                            if (item == null && mClientBookmark.create(name, newConnectArg)) {
                                Toast.makeText(mContext, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupClient, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    mClientBookmark.modify(oldName, name, newConnectArg)) {
                                Toast.makeText(mContext, "编辑成功", Toast.LENGTH_SHORT).show();
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                Toast.makeText(mContext, "名称已使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }


    private void addOrModifyServerBookmark(final MenuItem item) {
        final String oldName = item == null ? null : item.getTitle().toString();

        final View view = View.inflate(mContext, R.layout.dialog_server_bookmark, null);
        final EditText edtUsername = (EditText) view.findViewById(R.id.edtUsername);
        final EditText edtPassword = (EditText) view.findViewById(R.id.edtPassword);
        final CheckBox chkWritable = (CheckBox) view.findViewById(R.id.chkWritable);
        final CheckBox chkConfined = (CheckBox) view.findViewById(R.id.chkConfined);

        final AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setCancelable(false)
                .setView(view)
                .setNeutralButton("删除", null)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", null)
                .show();

        if (item != null) {
            ServerWrapper.UserMeta meta = mServerBookmark.mBookmark.get(oldName);

            edtUsername.setText(meta.username);
            edtPassword.setText(meta.password);
            chkWritable.setChecked(meta.writable);
            chkConfined.setChecked(meta.confined);

            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mServerBookmark.remove(oldName);
                            mMenu.removeItem(item.getItemId());
                            dialog.dismiss();
                        }
                    });
        } else {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.GONE);
        }

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String username = edtUsername.getText().toString();
                        String password = edtPassword.getText().toString();
                        boolean writable = chkWritable.isChecked();
                        boolean confined = chkConfined.isChecked();

                        if (username.isEmpty()) {
                            Toast.makeText(mContext, "用户名不可为空", Toast.LENGTH_SHORT).show();
                        } else {

                            ServerWrapper.UserMeta newMeta = new ServerWrapper.UserMeta(
                                    username, password, writable, confined);

                            if (item == null && mServerBookmark.create(newMeta)) {
                                Toast.makeText(mContext, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupServer, username);
                                dialog.dismiss();

                            } else if (item != null && mServerBookmark.modify(oldName, newMeta)) {
                                Toast.makeText(mContext,
                                        "编辑成功，需要重启共享生效", Toast.LENGTH_SHORT).show();
                                item.setTitle(username);
                                dialog.dismiss();


                            } else {
                                Toast.makeText(mContext, "用户名已使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private class LocalBookmark {
        private final static String BOOKMARK_FILE = "LocalBookmark";
        private final Map<String, FilePath> mBookmark = new HashMap<>();

        private LocalBookmark() {
            SharedPreferences preferences = MyApp.getContext()
                    .getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE);
            Map<String, ?> saved = preferences.getAll();

            final String hasWritten = "hasWritten";
            if (saved.get(hasWritten) == null) {
                create("默认存储器",
                        new FilePath(Environment.getExternalStorageDirectory().getPath()));
                preferences.edit().putBoolean(hasWritten, true).apply();
            }

            for (String key : saved.keySet()) {
                if (!key.equals(hasWritten)) {
                    mBookmark.put(key, new FilePath((String) saved.get(key)));
                }
            }
        }

        private boolean create(String name, FilePath root) {
            if (mBookmark.containsKey(name)) {
                return false;
            }
            mBookmark.put(name, root);
            MyApp.getContext().getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).edit()
                    .putString(name, root.pathString)
                    .apply();
            return true;
        }

        private void remove(String name) {
            mBookmark.remove(name);
            MyApp.getContext().getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).edit()
                    .remove(name)
                    .apply();
        }

        private boolean modify(String oldName, String newName, FilePath newRoot) {
            if (!newName.equals(oldName) && mBookmark.containsKey(newName)) {
                return false;
            } else {
                remove(oldName);
                create(newName, newRoot);
                return true;
            }
        }
    }

    private class ClientBookmark {
        private final static String BOOKMARK_FILE = "ClientBookmark";
        private final Map<String, ClientAction.Argument.Connect> mBookmark = new HashMap<>();

        private ClientBookmark() {
            Map<String, ?> saved = MyApp.getContext()
                    .getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).getAll();
            ArrayList<String> names = new ArrayList<>();
            for (String key : saved.keySet()) {
                if (key.endsWith(".address")) {
                    names.add(key.substring(0, key.length() - ".address".length()));
                }
            }
            for (String name : names) {
                ClientAction.Argument.Connect connectArg = new ClientAction.Argument.Connect();
                mBookmark.put(name, connectArg);
                connectArg.address = (String) saved.get(name + ".address");
                connectArg.port = (Integer) saved.get(name + ".port");
                connectArg.username = (String) saved.get(name + ".username");
                connectArg.password = (String) saved.get(name + ".password");
                connectArg.encoding = (String) saved.get(name + ".encoding");
            }
        }

        private boolean create(String name, ClientAction.Argument.Connect connectArg) {
            if (mBookmark.containsKey(name)) {
                return false;
            }
            mBookmark.put(name, connectArg);
            MyApp.getContext().getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).edit()
                    .putString(name + ".address", connectArg.address)
                    .putInt(name + ".port", connectArg.port)
                    .putString(name + ".username", connectArg.username)
                    .putString(name + ".password", connectArg.password)
                    .putString(name + ".encoding", connectArg.encoding)
                    .apply();
            return true;
        }

        private void remove(String name) {
            mBookmark.remove(name);
            MyApp.getContext().getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).edit()
                    .remove(name + ".address")
                    .remove(name + ".port")
                    .remove(name + ".username")
                    .remove(name + ".password")
                    .remove(name + ".encoding")
                    .apply();
        }

        private boolean modify(String oldName, String newName,
                               ClientAction.Argument.Connect newConnectArg) {
            if (!newName.equals(oldName) && mBookmark.containsKey(newName)) {
                return false;
            } else {
                remove(oldName);
                create(newName, newConnectArg);
                return true;
            }
        }
    }

    private class ServerBookmark {
        private final Map<String, ServerWrapper.UserMeta> mBookmark;

        private final ServerWrapper mServer;

        private ServerBookmark() {
            mServer = ServerWrapper.getSingleton();
            mBookmark = mServer.getAllUsers();
        }

        private boolean create(ServerWrapper.UserMeta userMeta) {
            return mServer.createUser(userMeta);
        }

        private void remove(String name) {
            closeCorrespondingSession(name);
            mServer.removeUser(name);
        }

        private boolean modify(String oldUsername, ServerWrapper.UserMeta newMeta) {
            closeCorrespondingSession(oldUsername);
            return mServer.modifyUser(oldUsername, newMeta);
        }

        private void closeCorrespondingSession(String username) {
            for (int i = 0; i < mSessionFragments.size(); i++) {
                ExplorerFragment fragment = mSessionFragments.get(i);
                if (fragment instanceof ServerExplorerFragment &&
                        (((ServerExplorerFragment) fragment).getUsername().equals(username))) {
                    closeSession(i--);
                }
            }
        }
    }

    private class ExplorerPagerAdapter extends FragmentStatePagerAdapter {
        private ExplorerPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public ExplorerFragment getItem(int position) {
            return mSessionFragments.get(position);
        }

        @Override
        public int getCount() {
            return mSessionFragments.size();
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
