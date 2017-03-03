package cn.gembit.transdev.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.gembit.transdev.R;
import cn.gembit.transdev.addition.Config;
import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;
import cn.gembit.transdev.labor.GlobalClipboard;
import cn.gembit.transdev.widgets.AutoFitRecyclerView;
import cn.gembit.transdev.widgets.FloatingActionButtonMenu;
import cn.gembit.transdev.widgets.PathBar;
import cn.gembit.transdev.widgets.WaitView;

public abstract class ExplorerFragment extends Fragment {

    final static Animation REFRESH_ANIMATION;

    private static int sPictureSize;
    private static int sItemNormalBackground;
    private static int sItemSelectedBackground;
    private static boolean sIsFirstAttach = true;

    static {
        REFRESH_ANIMATION = new ScaleAnimation(1f, 1f, 1.05f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0);
        REFRESH_ANIMATION.setDuration(200);
        REFRESH_ANIMATION.setInterpolator(new AccelerateInterpolator());
    }

    private FilePath mRootDir;
    private FilePath mCurDir;

    private View mRootView;
    private AppBarLayout mPathBarLayout;

    private TextView mTitleView;
    private PathBar mPathBar;
    private AutoFitRecyclerView mRecyclerView;
    private FloatingActionButtonMenu mFabMenu;
    private SwipeRefreshLayout mRefreshLayout;
    private WaitView mWaitView;
    private FileListAdapter mAdapter;

    private boolean mLocked = false;

    private GlobalClipboard.ClipboardActionCallback mClipboardCallback;

    private FloatingActionButtonMenu.SubItem mSelectInverseFAB;
    private FloatingActionButtonMenu.SubItem mNewFileFAB;
    private FloatingActionButtonMenu.SubItem mNewDirFAB;
    private FloatingActionButtonMenu.SubItem mDeleteFAB;
    private FloatingActionButtonMenu.SubItem mCopyFAB;
    private FloatingActionButtonMenu.SubItem mCutFAB;
    private FloatingActionButtonMenu.SubItem mRenameFAB;

    private View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == mSelectInverseFAB.getId()) {
                selectInverse();
                return;

            } else if (v.getId() == mNewFileFAB.getId()) {
                newFile(false);

            } else if (v.getId() == mNewDirFAB.getId()) {
                newFile(true);

            } else if (v.getId() == mDeleteFAB.getId()) {
                delete(mAdapter.mSelectedItems.keySet());

            } else if (v.getId() == mCopyFAB.getId()) {
                copyOrCut(new ArrayList<>(mAdapter.mSelectedItems.keySet()), true);
                clearSelection();

            } else if (v.getId() == mCutFAB.getId()) {
                copyOrCut(new ArrayList<>(mAdapter.mSelectedItems.keySet()), false);
                clearSelection();

            } else if (v.getId() == mRenameFAB.getId()) {
                rename(mAdapter.mSelectedItems.keySet().iterator().next());
            }
            mFabMenu.expand(false);
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (sIsFirstAttach) {
            sIsFirstAttach = false;

            SharedPreferences sp = Config.UIConfig.getSharedPreferences();
            sPictureSize = sp.getInt(Config.UIConfig.FILE_LIST_PIC_SIZE,
                    context.getResources().getDimensionPixelSize(R.dimen.fileListPictureSize));

            sItemNormalBackground = ContextCompat.getColor(getContext(), R.color.colorBackground);
            sItemSelectedBackground = ContextCompat.getColor(getContext(), R.color.colorPrimary);
            sItemSelectedBackground = (sItemSelectedBackground & 0x00FFFFFF) | 0x55000000;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (mRootView != null) {
            return mRootView;
        }

        View rootView = inflater.inflate(R.layout.fragment_explorer, container, false);
        rootView.setEnabled(false);

        mTitleView = (TextView) rootView.findViewById(R.id.fragmentTitle);
        mPathBarLayout = (AppBarLayout) rootView.findViewById(R.id.pathBarLayout);
        mPathBar = (PathBar) rootView.findViewById(R.id.pathBar);
        mRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);
        mRecyclerView = (AutoFitRecyclerView) rootView.findViewById(R.id.fileList);
        mFabMenu = (FloatingActionButtonMenu) rootView.findViewById(R.id.fabMenu);
        mWaitView = (WaitView) rootView.findViewById(R.id.waitView);

        mFabMenu.setContent(
                R.drawable.ic_add, R.drawable.ic_paste,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        paste();
                    }
                },
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return GlobalClipboard.getOut() != null;
                    }
                },
                mSelectInverseFAB = mFabMenu.new SubItem(
                        getContext(), "反选", R.drawable.ic_select_inverse, mListener),
                mNewFileFAB = mFabMenu.new SubItem(
                        getContext(), "新建文件", R.drawable.ic_new_file, mListener),
                mNewDirFAB = mFabMenu.new SubItem(
                        getContext(), "新建文件夹", R.drawable.ic_new_dir, mListener),
                mDeleteFAB = mFabMenu.new SubItem(
                        getContext(), "删除", R.drawable.ic_delete, mListener),
                mCopyFAB = mFabMenu.new SubItem(
                        getContext(), "复制", R.drawable.ic_copy, mListener),
                mCutFAB = mFabMenu.new SubItem(
                        getContext(), "剪切", R.drawable.ic_cut, mListener),
                mRenameFAB = mFabMenu.new SubItem(
                        getContext(), "重命名", R.drawable.ic_rename, mListener));

        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                changeDir(mCurDir);
                mRefreshLayout.setRefreshing(false);
            }
        });
        GlobalClipboard.registerCallback(
                mClipboardCallback = new GlobalClipboard.ClipboardActionCallback() {
                    @Override
                    public void onClipboardAction(boolean putIn) {
                        mFabMenu.setToBeDrawerMode(!putIn);
                    }
                });

        mPathBar.bindFragment(this);

        mAdapter = new FileListAdapter();
        notifySelectionCountChanged();

        mRecyclerView.addOnScrollListener(new ExplorerFragment.ScrollToHideFabListener());
        mRecyclerView.setAdapter(mAdapter);

        startUp();
        return rootView;
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mRootView = getView();
    }

    public boolean onBackPress() {

        if (mLocked) {
            return true;
        }

        if (mFabMenu.expand(false)) {
            return true;
        }

        if (mAdapter.mSelectedItems.size() > 0) {
            clearSelection();
            return true;
        }

        if (mCurDir != null) {
            FilePath parent = mCurDir.getParent();
            if (mRootDir != null ? mRootDir.cover(parent) : !mCurDir.equals(parent)) {
                changeDir(parent);
                return true;
            }
        }
        return false;
    }

    protected abstract void startUp();

    public abstract void changeDir(FilePath newPath);

    protected abstract void enter(FileMeta meta);

    protected abstract void moreAction(FileMeta meta);

    protected abstract void newFile(boolean isDir);

    protected abstract void delete(Collection<FileMeta> allSelected);

    protected abstract void rename(FileMeta oldMeta);

    protected abstract void copyOrCut(Collection<FileMeta> allSelected, boolean isToCopy);

    protected abstract void paste();

    protected Drawable getIcon(FileMeta meta) {
        return ContextCompat.getDrawable(getContext(), FileType.getIcon(meta.type));
    }

    protected void endUp() {
        GlobalClipboard.unregisterCallback(mClipboardCallback);
    }


    public FilePath getRootDir() {
        return mRootDir;
    }

    protected void setRootDir(FilePath rootDir) {
        this.mRootDir = rootDir;
    }

    protected FilePath getCurDir() {
        return mCurDir;
    }

    protected void setTitle(String title) {
        mTitleView.setText(title);
    }

    protected void lockFragment(boolean toLock) {
        mLocked = toLock;
        mWaitView.setVisibility(mLocked ? View.VISIBLE : View.INVISIBLE);
        mRefreshLayout.setEnabled(!mLocked);
        mFabMenu.setEnabled(!mLocked);
        mPathBar.setEnabled(!mLocked);
        if (mLocked) {
            mFabMenu.expand(false);
        }
    }

    protected void addItemView(FileMeta toAdd) {
        int size = mAdapter.mMetaList.size();
        mAdapter.mMetaList.add(toAdd);
        mAdapter.notifyItemInserted(size);
        mRecyclerView.scrollToPosition(size);
        mPathBarLayout.setExpanded(false, true);
    }

    protected void removeItemView(Collection<FileMeta> allDeleted) {
        ArrayList<Integer> allDeletedIndex = new ArrayList<>(allDeleted.size());
        for (FileMeta deleted : allDeleted) {
            allDeletedIndex.add(mAdapter.mSelectedItems.get(deleted));
            mAdapter.mSelectedItems.remove(deleted);
        }
        notifySelectionCountChanged();
        Collections.sort(allDeletedIndex, Collections.<Integer>reverseOrder());
        for (int where : allDeletedIndex) {
            mAdapter.mMetaList.remove(where);
            mAdapter.notifyItemRemoved(where);
        }
    }

    protected void modifyItemView(FileMeta oldMeta, FileMeta newMeta) {
        int where = mAdapter.mSelectedItems.get(oldMeta);
        mAdapter.mSelectedItems.remove(oldMeta);
        notifySelectionCountChanged();
        mAdapter.mMetaList.set(where, newMeta);
        mAdapter.notifyItemChanged(where);
    }

    protected void notifyDirChanged(FilePath newPath, Collection<FileMeta> allMeta) {
        mCurDir = newPath;
        mPathBar.notifyPathChanged(mCurDir);

        mAdapter.mSelectedItems.clear();
        notifySelectionCountChanged();
        mAdapter.mMetaList.clear();
        mAdapter.mMetaList.addAll(allMeta);
        Collections.sort(mAdapter.mMetaList);
        mAdapter.notifyDataSetChanged();

        mRecyclerView.startAnimation(REFRESH_ANIMATION);
        mRecyclerView.scrollToPosition(0);

        mFabMenu.expand(false);
        mFabMenu.setVisibility(View.VISIBLE);
    }

    private void notifySelectionCountChanged() {
        int count = mAdapter.mSelectedItems.size();
        mSelectInverseFAB.setValidity(true);
        mNewFileFAB.setValidity(count == 0);
        mNewDirFAB.setValidity(count == 0);
        mDeleteFAB.setValidity(count > 0);
        mCopyFAB.setValidity(count > 0);
        mCutFAB.setValidity(count > 0);
        mRenameFAB.setValidity(count == 1);
    }

    private void clearSelection() {
        Iterator<Map.Entry<FileMeta, Integer>> it = mAdapter.mSelectedItems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FileMeta, Integer> entry = it.next();
            int position = entry.getValue();
            it.remove();
            mAdapter.notifyItemChanged(position);
        }
    }

    private void selectInverse() {
        int size = mAdapter.mMetaList.size();
        if (size > 0) {
            HashMap<FileMeta, Integer> tmp = new HashMap<>();
            for (int i = 0; i < size; i++) {
                FileMeta meta = mAdapter.mMetaList.get(i);
                if (!mAdapter.mSelectedItems.containsKey(meta)) {
                    tmp.put(meta, i);
                }
            }
            mAdapter.mSelectedItems = tmp;
            mAdapter.notifyItemRangeChanged(0, size);
            notifySelectionCountChanged();
        }
    }

    private void switchSelectionStatus(int position) {
        FileMeta meta = mAdapter.mMetaList.get(position);
        if (mAdapter.mSelectedItems.containsKey(meta)) {
            mAdapter.mSelectedItems.remove(meta);
        } else {
            mAdapter.mSelectedItems.put(meta, position);
        }

        notifySelectionCountChanged();
        mAdapter.notifyItemChanged(position);
    }

    private class FileItemView extends RelativeLayout
            implements View.OnClickListener, View.OnLongClickListener {

        private final ImageView mTypeView;
        private final TextView mNameView;
        private final TextView mSizeView;
        private final TextView mTimeView;

        private RecyclerView.ViewHolder mHolder;

        private FileItemView(Context context) {
            super(context);
            inflate(getContext(), R.layout.item_file_list, this);
            mTypeView = (ImageView) findViewById(R.id.type);
            mNameView = (TextView) findViewById(R.id.name);
            mSizeView = (TextView) findViewById(R.id.size);
            mTimeView = (TextView) findViewById(R.id.time);

            setOnClickListener(this);
            setOnLongClickListener(this);

            resize(true);
        }

        private void resize(boolean forced) {
            ViewGroup.LayoutParams params = mTypeView.getLayoutParams();
            if (forced || sPictureSize != params.width) {
                params.width = params.height = sPictureSize;
                mTypeView.setLayoutParams(params);
                mNameView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sPictureSize * 0.42f);
                mSizeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sPictureSize * 0.24f);
                mTimeView.setTextSize(TypedValue.COMPLEX_UNIT_PX, sPictureSize * 0.24f);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            resize(false);
        }

        @Override
        public void onClick(View v) {
            if (!mLocked) {
                int position = ((FileItemView) v).mHolder.getAdapterPosition();
                int selectedCount = mAdapter.mSelectedItems.size();
                FileMeta meta = mAdapter.mMetaList.get(position);
                if (selectedCount == 0) {
                    enter(meta);
                } else if (selectedCount == 1 && mAdapter.mSelectedItems.containsKey(meta)) {
                    moreAction(meta);
                    switchSelectionStatus(position);
                } else {
                    switchSelectionStatus(position);
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if (!mLocked) {
                switchSelectionStatus(((FileItemView) v).mHolder.getAdapterPosition());
            }
            return true;
        }
    }

    private class FileListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<FileMeta> mMetaList = new ArrayList<>();
        private Map<FileMeta, Integer> mSelectedItems = new HashMap<>();

        @Override
        public int getItemCount() {
            return mMetaList.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(
                    new FileItemView(getContext())) {
            };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            FileItemView itemView = (FileItemView) holder.itemView;
            itemView.mHolder = holder;

            FileMeta meta = mMetaList.get(position);

            Bitmap bitmap = ((BitmapDrawable) getIcon(meta)).getBitmap();
            RoundedBitmapDrawable rid = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
            rid.setCornerRadius(bitmap.getWidth() * 0.15f);
            itemView.mTypeView.setImageDrawable(rid);

            itemView.mNameView.setText(meta.name);
            itemView.mSizeView.setText(meta.size);
            itemView.mTimeView.setText(meta.time);
            itemView.setBackgroundColor(mSelectedItems.containsKey(meta) ?
                    ExplorerFragment.sItemSelectedBackground :
                    ExplorerFragment.sItemNormalBackground);
        }
    }

    private class ScrollToHideFabListener extends RecyclerView.OnScrollListener {

        private final static int DURATION = 1000;
        private final static int MOVE_SCALE = 3;

        private boolean mMoving;
        private int mHeight;
        private int mOffset;

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING && !mMoving) {
                mMoving = true;
                mFabMenu.clearAnimation();
                mHeight = mFabMenu.heightFromScreenBottom();
                mOffset = 0;
                if (mFabMenu.getVisibility() != View.VISIBLE) {
                    mFabMenu.setVisibility(View.VISIBLE);
                    mFabMenu.offsetTopAndBottom(mHeight);
                    mOffset += mHeight;
                }
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE && mMoving) {
                mMoving = false;
                mFabMenu.offsetTopAndBottom(-mOffset);
                if (mOffset >= mHeight) {
                    mFabMenu.setVisibility(View.INVISIBLE);
                } else if (mOffset > 0) {
                    TranslateAnimation animation = new TranslateAnimation(0f, 0f, mOffset, 0f);
                    animation.setInterpolator(new OvershootInterpolator());
                    animation.setDuration(DURATION);
                    mFabMenu.startAnimation(animation);
                }
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            dy /= MOVE_SCALE;
            if (mMoving) {
                if (dy > 0 && mOffset < mHeight || dy < 0 && mOffset > 0) {
                    if (mOffset + dy > 0) {
                        mFabMenu.offsetTopAndBottom(dy);
                        mOffset += dy;
                    } else {
                        mFabMenu.offsetTopAndBottom(-mOffset);
                        mOffset = 0;
                    }
                }
            }
        }
    }
}