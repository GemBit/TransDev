package cn.gembit.transdev.activities;


import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

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

        OPTIONS[1] = new OptionGroup("应用",
                new OptionItem("使用说明", "基本使用概述和若干操作细节"),
                new OptionItem("检查更新", "当前版本：1.0"));

        OPTIONS[2] = new OptionGroup("个性化",
                new OptionItem("界面主题", null),
                new OptionItem("文件图标", null));
    }

    private AdditionOptionAdapter mAdapter;

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
                drawable.setColorFilter(BaseActivity.getColor(this, R.attr.titleTextColor),
                        PorterDuff.Mode.SRC_ATOP);
            }
        }

        OPTIONS[2].mItems[0].mSubtitle = ThemeOption.getNameById(AppConfig.readThemeID());
        OPTIONS[2].mItems[1].mSubtitle = FileIconOption.getNameById(AppConfig.readFileIconBgId());

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

        if (groupPosition == 0) {

            if (childPosition == 0) {

            } else if (childPosition == 1) {
                new AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setPositiveButton("确定", null)
                        .setTitle(OPTIONS[groupPosition].mItems[childPosition].mTitle)
                        .setMessage("本应用中的大部分图片资源来自于网络搜索，经过图片编辑处理" +
                                "而嵌入使用。若其不慎侵犯了您的著作权利，请联系作者及时修改：" +
                                "1943825697@qq.com")
                .show();
            }

        } else if (groupPosition == 1) {

            if (childPosition == 0) {

            } else if (childPosition == 1) {

            }

        } else if (groupPosition == 2) {

            if (childPosition == 0) {
                ThemeOption.makeChoiceDialog(this);
            } else if (childPosition == 1) {
                FileIconOption.makeChoiceDialog(this);
            }

        }

        return true;
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
        private static String[] sNames = new String[]{"深色", "浅色"};

        private static String getNameById(int themeId) {
            for (int i = 0; i < sIds.length; i++) {
                if (sIds[i] == themeId) {
                    return sNames[i];
                }
            }
            return "";
        }

        private static void makeChoiceDialog(final AdditionActivity activity) {
            new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setTitle("请选择应用主题")
                    .setItems(ThemeOption.sNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int newId = ThemeOption.sIds[which];
                            int oldId = AppConfig.readThemeID();

                            if (newId != oldId) {
                                AppConfig.saveThemeID(newId);
                                OPTIONS[2].mItems[0].mSubtitle = getNameById(newId);
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

        private static void makeChoiceDialog(final AdditionActivity activity) {
            new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setTitle("请选择文件图标形状")
                    .setItems(FileIconOption.sNames, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int newId = FileIconOption.sIds[which];
                            int oldId = AppConfig.readFileIconBgId();

                            if (newId != oldId) {
                                AppConfig.saveFileIconBgId(newId);
                                OPTIONS[2].mItems[1].mSubtitle = getNameById(newId);
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
