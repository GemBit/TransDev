package cn.gembit.transdev.activities;


import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import cn.gembit.transdev.R;
import cn.gembit.transdev.app.AppConfig;

public class AdditionActivity extends BaseActivity
        implements ExpandableListView.OnChildClickListener {

    private final static OptionGroup[] OPTIONS;

    static {
        OPTIONS = new OptionGroup[3];

        OPTIONS[0] = new OptionGroup("版权有关",
                new OptionItem("开源许可", "软件所使用的第三方开源库"),
                new OptionItem("图片素材", "软件所使用各种图标资源等"));

        OPTIONS[1] = new OptionGroup("个性化",
                new OptionItem("界面主题", null),
                new OptionItem("文件图标", null));

        OPTIONS[2] = new OptionGroup("应用",
                new OptionItem("使用说明", "基本使用概述和若干操作细节"),
                new OptionItem("分享应用", "分享发送本软件的安装包"),
                new OptionItem("检查更新", null),
                new OptionItem("退出应用", "结束全部后台并退出应用"));
    }

    private AdditionOptionAdapter mAdapter;

    private int mCurVersionCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_addition);

        Toolbar toolbar = (Toolbar) findViewById(R.id.title);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            Drawable drawable = toolbar.getNavigationIcon();
            if (drawable != null) {
                drawable.setColorFilter(BaseActivity.getAttrColor(this, R.attr.titleTextColor),
                        PorterDuff.Mode.SRC_ATOP);
            }
        }

        String curVersionName;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            mCurVersionCode = info.versionCode;
            curVersionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            mCurVersionCode = -1;
            curVersionName = "N/A";
        }

        OPTIONS[1].mItems[0].mSubtitle = ThemeOption.getNameById(AppConfig.readThemeID());
        OPTIONS[1].mItems[1].mSubtitle = FileIconOption.getNameById(AppConfig.readFileIconBgId());

        OPTIONS[2].mItems[2].mSubtitle = "当前版本：" + curVersionName;

        ExpandableListView listView = (ExpandableListView) findViewById(R.id.additionList);
        listView.setAdapter(mAdapter = new AdditionOptionAdapter());
        listView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(
                    ExpandableListView parent, View v, int groupPosition, long id) {
                return true;
            }
        });

        for (int i = 0; i < OPTIONS.length; i++) {
            listView.expandGroup(i);
        }

        listView.setOnChildClickListener(this);
    }

    @Override
    public boolean onChildClick(
            ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
        OptionItem item = OPTIONS[groupPosition].mItems[childPosition];

        if (groupPosition == 0) {

            if (childPosition == 0) {
                dialogForLibs(item);

            } else if (childPosition == 1) {
                dialogForIcons(item);
            }

        } else if (groupPosition == 1) {

            if (childPosition == 0) {
                ThemeOption.makeChoiceDialog(this, item);
            } else if (childPosition == 1) {
                FileIconOption.makeChoiceDialog(this, item);
            }

        } else if (groupPosition == 2) {

            if (childPosition == 0) {
                dialogForTips(item);

            } else if (childPosition == 1) {
                shareAPK(item);

            } else if (childPosition == 2) {
                new OptionCheckUpdate().dialogForUpdate(item);

            } else if (childPosition == 3) {
                dialogForExit(item);
            }
        }

        return true;
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    private void dialogForLibs(OptionItem item) {
        int textColor = BaseActivity.getAttrColor(this, android.R.attr.textColor);
        int accentColor = BaseActivity.getAttrColor(this, R.attr.colorAccent);
        WebView webView = new WebView(this);
        webView.setBackgroundColor(0x00ffffff);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.loadUrl("file:///android_res/raw/licenses.html?textColor=" +
                textColor + "&accentColor=" + accentColor);

        new AlertDialog.Builder(this)
                .setTitle(item.mTitle)
                .setView(webView)
                .setNegativeButton("确定", null)
                .show();

    }

    private void dialogForIcons(OptionItem item) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setPositiveButton("确定", null)
                .setTitle(item.mTitle)
                .setMessage("本应用中的大部分图片资源来自于网络搜索，经过图片编辑处理" +
                        "而嵌入使用。若其不慎侵犯了您的著作权利，请联系作者及时修改：" +
                        "1943825697@qq.com")
                .show();
    }

    private void dialogForTips(OptionItem item) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setPositiveButton("确定", null)
                .setTitle(item.mTitle)
                .setMessage("大体上,软件的功能可以分为三个部分：\n" +
                        "1、作为文件浏览器，可以对本地文件进行查增删改等操作\n" +
                        "2、作为FTP客户端，可以对远程文件进行各种操作\n" +
                        "3、作为FTP服务器，将本地文件的部分或全部文件进行局域网共享\n\n" +
                        "在主界面的侧拉栏，可对本地文件添加路径收藏，保存远程主机，管理FTP共享账户，" +
                        "还可以跳转到或者删除已打开窗口\n\n" +
                        "对于共享账户，限制其可访问文件意味着：此账号只能访问特定文件（默认为空），" +
                        "可以点击账号，然后将本地文件“复制”到对应窗口，为之添加可访问文件或文件夹\n\n" +
                        "通过【一键速连】功能，可以快速连接同一局域网下正在进行广播的其他设备\n\n\n" +
                        "使用细节：\n\n" +
                        "文件的各种编辑复制等操作都是通过屏幕下方的浮动按钮完成\n\n" +
                        "直接点击文件图标，等于对其进行长按\n\n" +
                        "在单个文件被选中后，再次点击可以有更多打开方式\n\n" +
                        "在路径栏长按可以复制对应路径到剪切板\n\n" +
                        "已复制文件，长按粘贴按钮，可以取消之")
                .show();
    }

    private void shareAPK(OptionItem item) {
        String filePath = getApplicationContext().getApplicationInfo().publicSourceDir;
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("*/*")
                .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(filePath)));
        startActivity(Intent.createChooser(intent, item.mTitle));
    }

    private void dialogForExit(OptionItem item) {
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(item.mTitle)
                .setMessage("确定退出？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BaseActivity.exit();
                    }
                })
                .show();
    }

    private static class OptionGroup {
        private String mGroupTitle;
        private OptionItem[] mItems;

        private OptionGroup(String groupTitle, OptionItem... items) {
            mGroupTitle = groupTitle;
            mItems = items;
        }
    }

    private static class OptionItem {
        private String mTitle;
        private String mSubtitle;

        private OptionItem(String title, String subtitle) {
            mTitle = title;
            mSubtitle = subtitle;
        }
    }

    private static class ThemeOption {
        private static int[] sIds = new int[]{R.style.AppTheme_Dark, R.style.AppTheme_Light};
        private static String[] sNames = new String[]{"深暗", "浅亮"};

        private static String getNameById(int themeId) {
            for (int i = 0; i < sIds.length; i++) {
                if (sIds[i] == themeId) {
                    return sNames[i];
                }
            }
            return "";
        }

        private static void makeChoiceDialog(
                final AdditionActivity activity, final OptionItem item) {
            new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setTitle(item.mTitle)
                    .setItems(ThemeOption.sNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int newId = ThemeOption.sIds[which];
                            int oldId = AppConfig.readThemeID();

                            if (newId != oldId) {
                                AppConfig.saveThemeID(newId);
                                item.mSubtitle = getNameById(newId);
                                activity.mAdapter.notifyDataSetChanged();
                                Toast.makeText(activity, "已更换主题，重启应用生效",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).show();
        }
    }

    private static class FileIconOption {
        private static int[] sIds = new int[]
                {R.drawable.bg_file_icon_square, R.drawable.bg_file_icon_circle};
        private static String[] sNames = new String[]{"方形", "圆形"};

        private static String getNameById(int themeId) {
            for (int i = 0; i < sIds.length; i++) {
                if (sIds[i] == themeId) {
                    return sNames[i];
                }
            }
            return "";
        }

        private static void makeChoiceDialog(
                final AdditionActivity activity, final OptionItem item) {
            new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setTitle(item.mTitle)
                    .setItems(FileIconOption.sNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int newId = FileIconOption.sIds[which];
                            int oldId = AppConfig.readFileIconBgId();

                            if (newId != oldId) {
                                AppConfig.saveFileIconBgId(newId);
                                item.mSubtitle = getNameById(newId);
                                activity.mAdapter.notifyDataSetChanged();
                                Toast.makeText(activity, "已更换图标形状，重启应用生效",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }).show();
        }
    }

    private static class ViewHolder {
        private TextView mTitleView;
        private TextView mSubtitleView;
    }

    private class OptionCheckUpdate {
        private boolean mCancelled = false;

        private int mVersionLag;
        private String mDownloadLink;
        private String mDescription;

        private void dialogForUpdate(final OptionItem item) {
            final AlertDialog dialog = new AlertDialog.Builder(AdditionActivity.this)
                    .setCancelable(false)
                    .setTitle(item.mTitle)
                    .setView(new ProgressBar(AdditionActivity.this))
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mCancelled = true;
                        }
                    })
                    .show();

            new Thread(new Runnable() {
                @Override
                public void run() {
                    HttpsURLConnection connection = null;
                    BufferedReader reader = null;

                    try {
                        connection = (HttpsURLConnection) new URL(
                                "https://TransDev.GemBit.cn/Update.php?version=" + mCurVersionCode)
                                .openConnection();
                        connection.setRequestMethod("GET");
                        connection.setUseCaches(false);
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);
                        reader = new BufferedReader(
                                new InputStreamReader(connection.getInputStream(), "UTF-8"));

                        mVersionLag = Integer.parseInt(reader.readLine());
                        if (mVersionLag > 0) {
                            mDownloadLink = reader.readLine();

                            StringBuilder sb = new StringBuilder();
                            while ((mDescription = reader.readLine()) != null) {
                                sb.append(mDescription).append('\n');
                            }
                            mDescription = sb.toString();
                        }

                    } catch (Exception e) {
                        mVersionLag = -1;

                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                mVersionLag = -1;
                            }
                        }
                        if (connection != null) {
                            connection.disconnect();
                        }

                        if (!mCancelled) {
                            AdditionActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dialog.dismiss();
                                    checkUpdateResult();
                                }
                            });
                        }
                    }
                }
            }).start();
        }

        private void checkUpdateResult() {
            AlertDialog.Builder builder = new AlertDialog.Builder(AdditionActivity.this)
                    .setCancelable(false)
                    .setMessage(mDescription)
                    .setNegativeButton("取消", null);

            if (mVersionLag == -1) {
                builder.setTitle("检查更新失败");
            } else {
                builder.setTitle("检查更新成功");
                if (mVersionLag > 0) {
                    String msg = "你落后了最新版" + mVersionLag + "个版本。\n\n";
                    msg += "更新记录：\n\n" + mDescription;
                    builder.setMessage(msg)
                            .setPositiveButton("下载", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    AdditionActivity.this.startActivity(new Intent()
                                            .setAction("android.intent.action.VIEW")
                                            .setData(Uri.parse(mDownloadLink)));
                                }
                            });
                } else {
                    builder.setMessage("已是最新版本");
                }
            }

            builder.show();
        }
    }

    private class AdditionOptionAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return OPTIONS.length;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return OPTIONS[groupPosition].mItems.length;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return OPTIONS[groupPosition];
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return OPTIONS[groupPosition].mItems[childPosition];
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getGroupView(
                int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {

            TextView textView;
            if (convertView == null) {
                textView = (TextView) LayoutInflater.from(AdditionActivity.this)
                        .inflate(R.layout.item_addition_list_parent, parent, false);
            } else {
                textView = (TextView) convertView;
            }

            textView.setText(OPTIONS[groupPosition].mGroupTitle);
            return textView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent) {
            ViewHolder holder;

            if (convertView == null) {
                convertView = LayoutInflater.from(AdditionActivity.this)
                        .inflate(R.layout.item_addition_list_child, parent, false);
                holder = new ViewHolder();
                holder.mTitleView = (TextView) convertView.findViewById(R.id.title);
                holder.mSubtitleView = (TextView) convertView.findViewById(R.id.subTitle);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.mTitleView.setText(OPTIONS[groupPosition].mItems[childPosition].mTitle);
            holder.mSubtitleView.setText(OPTIONS[groupPosition].mItems[childPosition].mSubtitle);
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }
}
