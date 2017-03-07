package cn.gembit.transdev.activities;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.NotificationCompat;
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
import cn.gembit.transdev.app.MyApp;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.app.AliveKeeper;
import cn.gembit.transdev.work.ClientAction;
import cn.gembit.transdev.work.ConnectionBroadcast;
import cn.gembit.transdev.work.ServerWrapper;
import cn.gembit.transdev.work.TaskService;
import cn.gembit.transdev.widgets.BottomDialogBuilder;

public class MainActivity extends BaseActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final static String[] ENCODINGS;
    private final static Comparator<String> ENCODING_COMPARATOR;
    private final static int DEFAULT_ENCODING_INDEX;


    private final static int SERVER_ON_NOTIFICATION_ID = 1;
    private final static int PERMISSIONS_REQUEST_CODE = 1;

    static {
        ENCODINGS = Charset.availableCharsets().keySet().toArray(new String[0]);
        ENCODING_COMPARATOR = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareToIgnoreCase(o2);
            }
        };

        DEFAULT_ENCODING_INDEX =
                Math.max(0, Arrays.binarySearch(ENCODINGS, "UTF-8", ENCODING_COMPARATOR));
    }

    private ExplorerPagerAdapter mAdapter;
    private ViewPager mPager;
    private DrawerLayout mDrawer;
    private SwitchCompat mSwtServer;

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
                            BaseActivity.exit();
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
                            BaseActivity.exit();
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
                            BaseActivity.exit();
                        }
                    }).show();
        } else {
            Context applicationContext = getApplicationContext();
            finish();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            applicationContext.startActivity(intent);
        }
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
            int port = ServerBookmark.mServer.getPort();
            if (port == -1) {
                msg = "未开启";
            } else {
                msg = "IP地址：" + ServerBookmark.mServer.getIP();
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
                                    LocalBookmark.mMap.get(name)),
                            R.drawable.ic_local,
                            name);
                    break;

                case R.id.groupClient:
                    addSession(
                            ClientExplorerFragment.newInstance("远程文件：" + name,
                                    ClientBookmark.mMap.get(name)),
                            R.drawable.ic_client,
                            name);
                    break;

                case R.id.groupServer:
                    if (!ServerBookmark.mMap.get(name).confined) {
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

    public void init() {
        mAdapter = new ExplorerPagerAdapter(getSupportFragmentManager());
        mDrawer = (DrawerLayout) findViewById(R.id.drawerLayout);
        mPager = (ViewPager) findViewById(R.id.viewPager);
        NavigationView navigation = ((NavigationView) findViewById(R.id.navView));

        mPager.setAdapter(mAdapter);

        mMenu = navigation.getMenu();
        mTaskManage = mMenu.findItem(R.id.taskManage);
        mQuickConnect = mMenu.findItem(R.id.quickConnect);
        mAddition = mMenu.findItem(R.id.addition);
        mAddLocal = mMenu.findItem(R.id.addLocal);
        mAddClient = mMenu.findItem(R.id.addClient);
        mAddServer = mMenu.findItem(R.id.addServer);
        mServerStatus = mMenu.findItem(R.id.serverStatus);

        LocalBookmark.restore(this);
        ClientBookmark.restore(this);
        ServerBookmark.restore(this);

        mSwtServer = new SwitchCompat(this);
        mSwtServer.setChecked(false);
        mSwtServer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
                buttonView.setEnabled(false);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if (isChecked) {
                            ServerBookmark.mServer.start();
                        } else {
                            ServerBookmark.mServer.stop();
                        }

                        buttonView.post(new Runnable() {
                            @Override
                            public void run() {
                                boolean stated = ServerBookmark.mServer.getPort() != -1;
                                buttonView.setChecked(stated);
                                buttonView.setEnabled(true);
                                onServerStatusChanged(stated);
                            }
                        });
                    }
                }).start();
            }
        });
        mServerStatus.setActionView(mSwtServer);

        navigation.setNavigationItemSelectedListener(this);

        mDrawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                int count = TaskService.getUnfinishedTaskCount();
                mTaskManage.setTitle("任务管理" + (count > 0 ? " (" + count + ")" : ""));
                mSwtServer.setChecked(ServerBookmark.mServer.getPort() != -1);
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                super.onDrawerStateChanged(newState);
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
                    .setMessage("文件共享FTP服务器运行中")
                    .setTitle("停止服务器?")
                    .setPositiveButton("停止", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mSwtServer.setChecked(false);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        }
    }

    private void onServerStatusChanged(boolean stated) {
        NotificationManager manager = ((NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE));

        if (stated) {
            AliveKeeper.keep(this, MainActivity.class.getName());

            String msg = "访问：ftp://" + ServerBookmark.mServer.getIP() +
                    ":" + ServerBookmark.mServer.getPort();

            manager.notify(SERVER_ON_NOTIFICATION_ID, new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setContentTitle("文件共享FTP服务器运行中")
                    .setContentText(msg)
                    .setContentIntent(PendingIntent.getActivity(
                            this,
                            0,
                            new Intent(this, MainActivity.class)
                                    .putExtra(ServerWrapper.class.getName(), true),
                            PendingIntent.FLAG_UPDATE_CURRENT))
                    .build());
        } else {
            AliveKeeper.release(MainActivity.class.getName());
            manager.cancel(SERVER_ON_NOTIFICATION_ID);
        }
    }

    private boolean closeDrawer() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        }
        return false;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
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
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        swtSend.setChecked(false);
                        swtReceive.setChecked(false);
                    }
                })
                .show();

        if (ServerBookmark.mServer.getPort() == -1) {
            tvSend.setText("开启文件共享后方可启用广播");
            spnSend.setEnabled(false);
            swtSend.setEnabled(false);

        } else {
            tvSend.setText("广播未开启");
            spnSend.setAdapter(new ArrayAdapter<>(this,
                    R.layout.support_simple_spinner_dropdown_item,
                    ServerBookmark.mMap.keySet().toArray(new String[0])));

            swtSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    spnSend.setEnabled(!isChecked);

                    if (!isChecked) {
                        ConnectionBroadcast.stopSending();
                        return;
                    }

                    String username = (String) spnSend.getSelectedItem();
                    ServerWrapper.UserMeta userMeta = ServerBookmark.mMap.get(username);
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
                                        quickConnectDialog.getButton(
                                                DialogInterface.BUTTON_POSITIVE).performClick();
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
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_close);
        drawable.setColorFilter(BaseActivity.getColor(this, android.R.attr.textColor),
                PorterDuff.Mode.SRC_ATOP);
        imageView.setImageDrawable(drawable);

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
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_config);
        drawable.setColorFilter(BaseActivity.getColor(this, android.R.attr.textColor),
                PorterDuff.Mode.SRC_ATOP);
        imageView.setImageDrawable(drawable);

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
            edtRoot.setText(LocalBookmark.mMap.get(oldName).pathString);
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            LocalBookmark.remove(oldName);
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
                            toast("名称不可为空");
                        } else if (root.isEmpty()) {
                            toast("路径不可为空");

                        } else {

                            if (item == null && LocalBookmark.create(name, new FilePath(root))) {
                                toast("添加成功");
                                addBookmarkMenuItem(R.id.groupLocal, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    LocalBookmark.modify(oldName, name, new FilePath(root))) {
                                toast("编辑成功");
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                toast("名称已使用");
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
            ClientAction.Argument.Connect connectArg = ClientBookmark.mMap.get(oldName);

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
                            ClientBookmark.remove(oldName);
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
                            toast("地址不可为空");
                        } else if (port == -1) {
                            toast("端口必须为数字");
                        } else {

                            ClientAction.Argument.Connect newConnectArg =
                                    new ClientAction.Argument.Connect();
                            newConnectArg.encoding = encoding;
                            newConnectArg.address = address;
                            newConnectArg.port = port;
                            newConnectArg.username = username;
                            newConnectArg.password = password;
                            newConnectArg.alias = name;

                            if (item == null && ClientBookmark.create(newConnectArg)) {
                                toast("添加成功");
                                addBookmarkMenuItem(R.id.groupClient, name);
                                dialog.dismiss();

                            } else if (item != null &&
                                    ClientBookmark.modify(oldName, newConnectArg)) {
                                toast("编辑成功");
                                item.setTitle(name);
                                dialog.dismiss();

                            } else {
                                toast("名称已使用");
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
            ServerWrapper.UserMeta meta = ServerBookmark.mMap.get(oldName);

            edtUsername.setText(meta.username);
            edtPassword.setText(meta.password);
            chkWritable.setChecked(meta.writable);
            chkConfined.setChecked(meta.confined);

            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ServerBookmark.remove(MainActivity.this, oldName);
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
                            toast("用户名不可为空");
                        } else if (username.equals("anonymous") && !password.isEmpty()) {
                            edtPassword.setText("");
                            toast("匿名账户无需密码");

                        } else {

                            ServerWrapper.UserMeta newMeta = new ServerWrapper.UserMeta(
                                    username, password, writable, confined);

                            if (item == null && ServerBookmark.create(newMeta)) {
                                toast("添加成功");
                                addBookmarkMenuItem(R.id.groupServer, username);
                                dialog.dismiss();

                            } else if (item != null &&
                                    ServerBookmark.modify(MainActivity.this, oldName, newMeta)) {
                                toast("编辑成功，需要重启服务器生效");
                                item.setTitle(username);
                                dialog.dismiss();


                            } else {
                                toast("用户名已使用");
                            }
                        }
                    }
                });
    }


    private static class LocalBookmark {
        private final static String BOOKMARK_FILE = "LocalBookmark";
        private static Map<String, FilePath> mMap;

        private static void restore(MainActivity activity) {
            mMap = new HashMap<>();

            SharedPreferences preferences = MyApp.getSharedPreferences(BOOKMARK_FILE);
            Map<String, ?> saved = preferences.getAll();

            final String hasWritten = "hasWritten";
            if (saved.get(hasWritten) == null) {
                create("默认存储器",
                        new FilePath(Environment.getExternalStorageDirectory().getPath()));
                preferences.edit().putBoolean(hasWritten, true).apply();
            }

            for (String key : saved.keySet()) {
                if (!key.equals(hasWritten)) {
                    mMap.put(key, new FilePath((String) saved.get(key)));
                    activity.addBookmarkMenuItem(R.id.groupLocal, key);
                }
            }
        }

        private static boolean create(String name, FilePath root) {
            if (mMap.containsKey(name)) {
                return false;
            }
            mMap.put(name, root);
            MyApp.getSharedPreferences(BOOKMARK_FILE).edit()
                    .putString(name, root.pathString)
                    .apply();
            return true;
        }

        private static void remove(String name) {
            mMap.remove(name);
            MyApp.getSharedPreferences(BOOKMARK_FILE).edit()
                    .remove(name)
                    .apply();
        }

        private static boolean modify(String oldName, String newName, FilePath newRoot) {
            if (!newName.equals(oldName) && mMap.containsKey(newName)) {
                return false;
            } else {
                remove(oldName);
                create(newName, newRoot);
                return true;
            }
        }
    }

    private static class ClientBookmark {
        private final static String BOOKMARK_FILE = "ClientBookmark";
        private static Map<String, ClientAction.Argument.Connect> mMap;

        private static void restore(MainActivity activity) {
            mMap = new HashMap<>();

            Map<String, ?> saved = MyApp.getSharedPreferences(BOOKMARK_FILE).getAll();
            ArrayList<String> names = new ArrayList<>();
            for (String key : saved.keySet()) {
                if (key.endsWith(".address")) {
                    names.add(key.substring(0, key.length() - ".address".length()));
                }
            }

            for (String name : names) {
                ClientAction.Argument.Connect connectArg = new ClientAction.Argument.Connect();
                mMap.put(name, connectArg);
                connectArg.address = (String) saved.get(name + ".address");
                connectArg.port = (Integer) saved.get(name + ".port");
                connectArg.username = (String) saved.get(name + ".username");
                connectArg.password = (String) saved.get(name + ".password");
                connectArg.encoding = (String) saved.get(name + ".encoding");
                connectArg.alias = name;

                activity.addBookmarkMenuItem(R.id.groupClient, name);
            }
        }


        private static boolean create(ClientAction.Argument.Connect connectArg) {
            if (mMap.containsKey(connectArg.alias)) {
                return false;
            }
            mMap.put(connectArg.alias, connectArg);
            MyApp.getSharedPreferences(BOOKMARK_FILE).edit()
                    .putString(connectArg.alias + ".address", connectArg.address)
                    .putInt(connectArg.alias + ".port", connectArg.port)
                    .putString(connectArg.alias + ".username", connectArg.username)
                    .putString(connectArg.alias + ".password", connectArg.password)
                    .putString(connectArg.alias + ".encoding", connectArg.encoding)
                    .apply();
            return true;
        }

        private static void remove(String name) {
            mMap.remove(name);
            MyApp.getSharedPreferences(BOOKMARK_FILE).edit()
                    .remove(name + ".address")
                    .remove(name + ".port")
                    .remove(name + ".username")
                    .remove(name + ".password")
                    .remove(name + ".encoding")
                    .apply();
        }

        private static boolean modify(String oldName, ClientAction.Argument.Connect newConnectArg) {
            if (!newConnectArg.alias.equals(oldName) &&
                    mMap.containsKey(newConnectArg.alias)) {
                return false;
            } else {
                remove(oldName);
                create(newConnectArg);
                return true;
            }
        }
    }

    private static class ServerBookmark {

        private static Map<String, ServerWrapper.UserMeta> mMap;

        private static ServerWrapper mServer;

        private static void restore(MainActivity activity) {
            mServer = ServerWrapper.getSingleton();
            mMap = mServer.getAllUsers();

            for (String key : mMap.keySet()) {
                activity.addBookmarkMenuItem(R.id.groupServer, key);
            }
        }

        private static boolean create(ServerWrapper.UserMeta userMeta) {
            return mServer.createUser(userMeta);
        }

        private static void remove(MainActivity activity, String name) {
            closeCorrespondingSession(activity, name);
            mServer.removeUser(name);
        }

        private static boolean modify(
                MainActivity activity, String oldUsername, ServerWrapper.UserMeta newMeta) {
            closeCorrespondingSession(activity, oldUsername);
            return mServer.modifyUser(oldUsername, newMeta);
        }

        private static void closeCorrespondingSession(MainActivity activity, String username) {
            for (int i = 0; i < activity.mSessionFragments.size(); i++) {
                ExplorerFragment fragment = activity.mSessionFragments.get(i);
                if (fragment instanceof ServerExplorerFragment &&
                        (((ServerExplorerFragment) fragment).getUsername().equals(username))) {
                    activity.closeSession(i--);
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
