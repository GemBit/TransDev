package cn.gembit.transdev.ui;


import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.TextView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.addition.MyApp;

public class AdditionActivity extends AppCompatActivity {

    private final static Group[] DATA;

    static {
        DATA = new Group[2];

        DATA[0] = new Group("版权有关",
                new Item("开源许可", "软件所用的第三方开源库"),
                new Item("图片素材", "软件使用各种图标"));

        DATA[1] = new Group("应用",
                new Item("使用说明", "若干使用技巧"),
                new Item("检查更新", "当前版本1.0"),
                new Item("更换主题", "深色"));
    }

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
                drawable.setColorFilter(MyApp.getColor(this, R.attr.titleTextColor),
                        PorterDuff.Mode.DST_ATOP);
            }
        }

        ExpandableListView listView = (ExpandableListView) findViewById(R.id.additionList);
        listView.setAdapter(new AdditionOptionAdapter());
        listView.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(
                    ExpandableListView parent, View v, int groupPosition, long id) {
                return true;
            }
        });
        for (int i = 0; i < DATA.length; i++) {
            listView.expandGroup(i);
        }

        listView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {

                if (groupPosition == 0) {
                    if (childPosition == 0) {

                    } else if (childPosition == 1) {

                    }
                } else if (groupPosition == 1) {
                    if (childPosition == 0) {

                    } else if (childPosition == 1) {

                    } else if (childPosition == 2) {

                    }
                }
                return true;
            }
        });
    }

    private static class Group {
        private final String mGroupTitle;
        private final Item[] mItems;

        private Group(String groupTitle, Item... items) {
            mGroupTitle = groupTitle;
            mItems = items;
        }
    }

    private static class Item {
        private String mTitle;
        private String mSubtitle;

        private Item(String title, String subtitle) {
            mTitle = title;
            mSubtitle = subtitle;
        }
    }

    private static class ViewHolder {
        private TextView mTitleView;
        private TextView mSubtitleView;
    }

    private class AdditionOptionAdapter extends BaseExpandableListAdapter {

        @Override
        public int getGroupCount() {
            return DATA.length;
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return DATA[groupPosition].mItems.length;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return DATA[groupPosition];
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            return DATA[groupPosition].mItems[childPosition];
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

            textView.setText(DATA[groupPosition].mGroupTitle);
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

            holder.mTitleView.setText(DATA[groupPosition].mItems[childPosition].mTitle);
            holder.mSubtitleView.setText(DATA[groupPosition].mItems[childPosition].mSubtitle);
            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

    }
}
