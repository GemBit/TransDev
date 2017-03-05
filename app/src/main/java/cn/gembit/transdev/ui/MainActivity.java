package cn.gembit.transdev.ui;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.SwitchCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
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
import cn.gembit.transdev.util.AliveKeeper;
import cn.gembit.transdev.util.ClientAction;
import cn.gembit.transdev.util.ConnectionBroadcast;
import cn.gembit.transdev.util.ServerWrapper;
import cn.gembit.transdev.util.TaskService;
import cn.gembit.transdev.widgets.BottomDialogBuilder;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final static String[] ENCODINGS;
    private final static Comparator<String> ENCODING_COMPARATOR;
    private final static int DEFAULT_ENCODING_INDEX;


    private final static int SERVER_ON_NOTIFICATION_ID = View.generateViewId();
    private final static int PERMISSIONS_REQUEST_CODE = 1;

    static {
        ENCODINGS = Charset.availableCharsets().keySet().toArray(new String[0]);
        ENCODING_COMPARATOR = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        };
        DEFAULT_ENCODING_INDEX = Arrays.binarySearch(ENCODINGS, "UTF-8", ENCODING_COMPARATOR);
    }

    private ExplorerPagerAdapter mAdapter;
    private ViewPager mPager;
    private DrawerLayout mDrawer;

    private LocalBookmark mLocalBookmark;
    private ClientBookmark mClientBookmark;
    private ServerBookmark mServerBookmark;

    private Menu mMenu;
    private MenuItem mTaskManage;
    private MenuItem mQuickConnect;
    private MenuItem mAddition;
    private MenuItem mAddLocal;
    private MenuItem mAddClient;
    private MenuItem mAddServer;
    private MenuItem mServerStatus;

    private LinkedList<MenuItem> mSessionMenuItems = new LinkedList<>();
    private LinkedList<ExplorerFragment> mSessionFragments = new LinkedList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PackageInfo info;
        try {
            info = getPackageManager()
                    .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            BottomDialogBuilder.make(this, "权限自检失败，程序退出").setOnDismissListener(
                    new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    }
            ).show();
            return;
        }

        if (info.requestedPermissions != null) {
            ArrayList<String> deniedPermissions = new ArrayList<>();
            for (String permission : info.requestedPermissions) {
                if (ContextCompat.checkSelfPermission(this, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.size() > 0) {
                ActivityCompat.requestPermissions(this, deniedPermissions.toArray(new String[0]),
                        PERMISSIONS_REQUEST_CODE);
                return;
            }
        }


        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            BottomDialogBuilder.make(this, "未安装储存卡！程序退出")
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    })
                    .show();
        }

        init();
        checkIntent(getIntent());
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (!closeDrawer()) {
            int index = mPager.getCurrentItem();
            if (index >= 0 && index < mSessionFragments.size()) {
                mSessionFragments.get(index).onBackPress();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean permitted = false;
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0) {
                permitted = true;
                for (int grantResult : grantResults) {
                    permitted &= grantResult == PackageManager.PERMISSION_GRANTED;
                }
            }
        }

        if (!permitted) {
            BottomDialogBuilder.make(this, "未获得足够权限，程序退出").setOnDismissListener(
                    new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            MainActivity.this.finish();
                        }
                    }).show();
        } else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        }
    }

    public void init() {
        mAdapter = new ExplorerPagerAdapter(getSupportFragmentManager());
        mDrawer = (DrawerLayout) findViewById(R.id.drawerLayout);
        mPager = (ViewPager) findViewById(R.id.viewPager);
        NavigationView navigation = ((NavigationView) findViewById(R.id.navView));

        mPager.setAdapter(mAdapter);

        mLocalBookmark = new LocalBookmark();
        mClientBookmark = new ClientBookmark();
        mServerBookmark = new ServerBookmark();

        mMenu = navigation.getMenu();
        mTaskManage = mMenu.findItem(R.id.taskManage);
        mQuickConnect = mMenu.findItem(R.id.quickConnect);
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

        SwitchCompat switchCompat = new SwitchCompat(this);
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
                                boolean stated = mServerBookmark.mServer.getPort() != -1;
                                buttonView.setChecked(stated);
                                buttonView.setEnabled(true);
                                NotificationManager manager = ((NotificationManager)
                                        getSystemService(Context.NOTIFICATION_SERVICE));
                                if (stated) {
                                    AliveKeeper.keep(MainActivity.this, MainActivity.class.getName());

                                    String msg = "访问：ftp://" + mServerBookmark.mServer.getIP() +
                                            ":" + mServerBookmark.mServer.getPort();

                                    Intent intent =
                                            new Intent(MainActivity.this, MainActivity.class)
                                            .putExtra(ServerWrapper.class.getName(), true);
                                    PendingIntent pendingIntent = PendingIntent.getActivity(
                                            MainActivity.this,
                                            0,
                                            intent,
                                            PendingIntent.FLAG_UPDATE_CURRENT);
                                    manager.notify(SERVER_ON_NOTIFICATION_ID,
                                            new NotificationCompat.Builder(MainActivity.this)
                                                    .setSmallIcon(R.drawable.ic_launcher)
                                                    .setOngoing(true)
                                                    .setAutoCancel(false)
                                                    .setContentTitle("文件共享FTP服务器已启动")
                                                    .setContentText(msg)
                                                    .setContentIntent(pendingIntent)
                                                    .build());
                                } else {
                                    AliveKeeper.release(MainActivity.class.getName());
                                    manager.cancel(SERVER_ON_NOTIFICATION_ID);
                                }
                            }
                        });
                    }
                }).start();
            }
        });
        mServerStatus.setActionView(switchCompat);

        navigation.setNavigationItemSelectedListener(this);

        mDrawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                int count = TaskService.getUnfinishedTaskCount();
                mTaskManage.setTitle("任务管理" + (count > 0 ? " (" + count + ")" : ""));
            }
        });

        mPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
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

    private void checkIntent(Intent intent) {
        if (intent.getBooleanExtra(ServerWrapper.class.getName(), false)) {
            new AlertDialog.Builder(this)
                    .setCancelable(false)
                    .setMessage("message")
                    .setTitle("title")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ServerWrapper.getSingleton().stop();
                        }
                    })
                    .show();
        }
    }

    private boolean closeDrawer() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item == mTaskManage) {
            startActivity(new Intent(this, TaskActivity.class));
            closeDrawer();

        } else if (item == mQuickConnect) {
            quickConnect();
            closeDrawer();

        } else if (item == mAddition) {
            startActivity(new Intent(this, AdditionActivity.class));
            closeDrawer();

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
            new AlertDialog.Builder(this)
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
                        BottomDialogBuilder.make(this,
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

            closeDrawer();
        }
        return true;
    }

    private void quickConnect() {
        final View view = View.inflate(this, R.layout.dialog_quick_connect, null);

        final TextView tvSend = (TextView) view.findViewById(R.id.tvSend);
        final AppCompatSpinner spnSend = (AppCompatSpinner) view.findViewById(R.id.spnSend);
        final SwitchCompat swtSend = (SwitchCompat) view.findViewById(R.id.swtSend);

        final TextView tvReceive = (TextView) view.findViewById(R.id.tvReceive);
        final SwitchCompat swtReceive = (SwitchCompat) view.findViewById(R.id.swtReceive);


        final AlertDialog quickConnectDialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(view)
                .setNegativeButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (swtSend.isChecked()) {
                            swtSend.performClick();
                        }
                        if (swtReceive.isChecked()) {
                            swtReceive.performClick();
                        }
                    }
                })
                .show();

        if (mServerBookmark.mServer.getPort() == -1) {
            tvSend.setText("开启文件共享后方可启用广播");
            spnSend.setEnabled(false);
            swtSend.setEnabled(false);

        } else {
            tvSend.setText("广播未开启");
            spnSend.setAdapter(new ArrayAdapter<>(this,
                    R.layout.support_simple_spinner_dropdown_item,
                    mServerBookmark.mBookmark.keySet().toArray(new String[0])));

            swtSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    spnSend.setEnabled(!isChecked);

                    if (!isChecked) {
                        ConnectionBroadcast.stopSending();
                        return;
                    }

                    String username = (String) spnSend.getSelectedItem();
                    ServerWrapper.UserMeta userMeta = mServerBookmark.mBookmark.get(username);
                    if (userMeta == null) {
                        tvSend.setText("请先选择账户");
                        swtSend.performClick();
                        return;
                    }

                    ConnectionBroadcast.sendBroadcast(userMeta.username, userMeta.password
                            , new ConnectionBroadcast.OnSendingStatusChangedCallback() {
                                @Override
                                public void onSendingStatusChanged(boolean sending, String brief) {
                                    swtSend.setChecked(sending);
                                    tvSend.setText(brief);
                                }
                            });
                }
            });
        }


        tvReceive.setText("未开启搜寻");

        swtReceive.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    ConnectionBroadcast.stopReceiving();
                    return;
                } else {
                    tvReceive.setText("正在搜寻…");
                }

                ConnectionBroadcast.receiveBroadcast(new ConnectionBroadcast.OnReceivedCallback() {
                    @Override
                    public void onReceived(
                            final ClientAction.Argument.Connect argument, String brief) {
                        tvReceive.setText(brief);

                        if (argument == null) {
                            swtReceive.setChecked(false);
                            return;
                        }

                        swtReceive.setChecked(false);
                        new AlertDialog.Builder(MainActivity.this)
                                .setCancelable(false)
                                .setTitle("发现设备")
                                .setMessage(argument.alias + "\n地址：" + argument.address)
                                .setNegativeButton("取消", null)
                                .setPositiveButton("连接", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        addSession(
                                                ClientExplorerFragment.newInstance(
                                                        "远程文件：" + argument.alias, argument),
                                                R.drawable.ic_client,
                                                argument.alias);
                                        quickConnectDialog.dismiss();
                                    }
                                })
                                .show();
                    }
                });
            }
        });
    }

    private void addSession(ExplorerFragment fragment, int iconResId, String menuItemTitle) {
        final MenuItem item;

        ImageView imageView = new ImageView(this);
        imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_close));

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
        ImageView imageView = new ImageView(this);
        imageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_config));

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

        final View view = View.inflate(this, R.layout.dialog_local_bookmark, null);
        final EditText edtName = (EditText) view.findViewById(R.id.edtName);
        final EditText edtRoot = (EditText) view.findViewById(R.id.edtRoot);

        final AlertDialog dialog = new AlertDialog.Builder(this)
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
                            Toast.makeText(MainActivity.this, "名称不可为空", Toast.LENGTH_SHORT).show();
                        } else if (root.isEmpty()) {
                            Toast.makeText(MainActivity.this, "路径不可为空", Toast.LENGTH_SHORT).show();

                        } else {

                            if (item == null && mLocalBookmark.create(name, new FilePath(root))) {
                                Toast.makeText(MainActivity.this, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupLocal, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    mLocalBookmark.modify(oldName, name, new FilePath(root))) {
                                Toast.makeText(MainActivity.this, "编辑成功", Toast.LENGTH_SHORT).show();
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                Toast.makeText(MainActivity.this, "名称已使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }

    private void addOrModifyClientBookmark(final MenuItem item) {
        final String oldName = item == null ? null : item.getTitle().toString();

        final View view = View.inflate(this, R.layout.dialog_client_bookmark, null);
        final AppCompatSpinner spnEncoding = (AppCompatSpinner) view.findViewById(R.id.spnEncoding);
        final EditText edtAddress = (EditText) view.findViewById(R.id.edtAddress);
        final EditText edtPort = (EditText) view.findViewById(R.id.edtPort);
        final EditText edtUsername = (EditText) view.findViewById(R.id.edtUsername);
        final EditText edtPassword = (EditText) view.findViewById(R.id.edtPassword);
        final EditText edtName = (EditText) view.findViewById(R.id.edtName);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(view)
                .setNeutralButton("删除", null)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", null)
                .show();

        spnEncoding.setAdapter(new ArrayAdapter<>(
                this, R.layout.support_simple_spinner_dropdown_item, ENCODINGS));

        if (item != null) {
            ClientAction.Argument.Connect connectArg = mClientBookmark.mBookmark.get(oldName);

            int index = Arrays.binarySearch(ENCODINGS, connectArg.encoding, ENCODING_COMPARATOR);
            if (index >= 0) {
                spnEncoding.setSelection(index);
            }
            edtAddress.setText(connectArg.address);
            edtPort.setText(String.valueOf(connectArg.port));
            edtUsername.setText(connectArg.username);
            edtPassword.setText(connectArg.password);
            edtName.setText(connectArg.alias);


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
                            Toast.makeText(MainActivity.this, "地址不可为空", Toast.LENGTH_SHORT).show();
                        } else if (port == -1) {
                            Toast.makeText(MainActivity.this, "端口必须为数字", Toast.LENGTH_SHORT).show();
                        } else {

                            ClientAction.Argument.Connect newConnectArg =
                                    new ClientAction.Argument.Connect();
                            newConnectArg.encoding = encoding;
                            newConnectArg.address = address;
                            newConnectArg.port = port;
                            newConnectArg.username = username;
                            newConnectArg.password = password;
                            newConnectArg.alias = name;

                            if (item == null && mClientBookmark.create(newConnectArg)) {
                                Toast.makeText(MainActivity.this, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupClient, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    mClientBookmark.modify(oldName, newConnectArg)) {
                                Toast.makeText(MainActivity.this, "编辑成功", Toast.LENGTH_SHORT).show();
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                Toast.makeText(MainActivity.this, "名称已使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }


    private void addOrModifyServerBookmark(final MenuItem item) {
        final String oldName = item == null ? null : item.getTitle().toString();

        final View view = View.inflate(this, R.layout.dialog_server_bookmark, null);
        final EditText edtUsername = (EditText) view.findViewById(R.id.edtUsername);
        final EditText edtPassword = (EditText) view.findViewById(R.id.edtPassword);
        final CheckBox chkWritable = (CheckBox) view.findViewById(R.id.chkWritable);
        final CheckBox chkConfined = (CheckBox) view.findViewById(R.id.chkConfined);

        final AlertDialog dialog = new AlertDialog.Builder(this)
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
                            Toast.makeText(MainActivity.this, "用户名不可为空", Toast.LENGTH_SHORT).show();
                        } else if (username.equals("anonymous") && !password.isEmpty()) {
                            edtPassword.setText("");
                            Toast.makeText(MainActivity.this, "匿名账户无需密码", Toast.LENGTH_SHORT).show();

                        } else {

                            ServerWrapper.UserMeta newMeta = new ServerWrapper.UserMeta(
                                    username, password, writable, confined);

                            if (item == null && mServerBookmark.create(newMeta)) {
                                Toast.makeText(MainActivity.this, "添加成功", Toast.LENGTH_SHORT).show();
                                addBookmarkMenuItem(R.id.groupServer, username);
                                dialog.dismiss();

                            } else if (item != null && mServerBookmark.modify(oldName, newMeta)) {
                                Toast.makeText(MainActivity.this,
                                        "编辑成功，需要重启服务器生效", Toast.LENGTH_SHORT).show();
                                item.setTitle(username);
                                dialog.dismiss();


                            } else {
                                Toast.makeText(MainActivity.this, "用户名已使用", Toast.LENGTH_SHORT).show();
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
                connectArg.alias = name;
            }
        }

        private boolean create(ClientAction.Argument.Connect connectArg) {
            if (mBookmark.containsKey(connectArg.alias)) {
                return false;
            }
            mBookmark.put(connectArg.alias, connectArg);
            MyApp.getContext().getSharedPreferences(BOOKMARK_FILE, Context.MODE_PRIVATE).edit()
                    .putString(connectArg.alias + ".address", connectArg.address)
                    .putInt(connectArg.alias + ".port", connectArg.port)
                    .putString(connectArg.alias + ".username", connectArg.username)
                    .putString(connectArg.alias + ".password", connectArg.password)
                    .putString(connectArg.alias + ".encoding", connectArg.encoding)
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

        private boolean modify(String oldName, ClientAction.Argument.Connect newConnectArg) {
            if (!newConnectArg.alias.equals(oldName) &&
                    mBookmark.containsKey(newConnectArg.alias)) {
                return false;
            } else {
                remove(oldName);
                create(newConnectArg);
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
