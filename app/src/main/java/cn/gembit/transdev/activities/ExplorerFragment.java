package cn.gembit.transdev.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cn.gembit.transdev.R;
import cn.gembit.transdev.app.AppConfig;
import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.file.FileType;
import cn.gembit.transdev.widgets.AutoFitRecyclerView;
import cn.gembit.transdev.widgets.FloatingActionButtonMenu;
import cn.gembit.transdev.widgets.WaitView;
import cn.gembit.transdev.work.GlobalClipboard;

public abstract class ExplorerFragment extends Fragment {

    private static int sPictureSize;
    private static int sItemNormalBackground;
    private static int sItemSelectedBackground;
    private static boolean sIsFirstAttach = true;

    private Animation mRefreshAnimation;

    private FilePath mRootDir;
    private FilePath mCurDir;

    private View mRootView;
    private AppBarLayout mPathBarLayout;

    private String mTitle;

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
            } else {
                mFabMenu.expand(false);
                if (v.getId() == mNewFileFAB.getId()) {
                    newFile(false);

                } else if (v.getId() == mNewDirFAB.getId()) {
                    newFile(true);

                } else if (v.getId() == mDeleteFAB.getId()) {
                    delete(mAdapter.mSelectedItems.keySet());

                } else if (v.getId() == mCopyFAB.getId()) {
                    copyOrCut(clearSelection(), true);

                } else if (v.getId() == mCutFAB.getId()) {
                    copyOrCut(clearSelection(), false);

                } else if (v.getId() == mRenameFAB.getId()) {
                    rename(mAdapter.mSelectedItems.keySet().iterator().next());
                }
            }
//            mFabMenu.expand(false);
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (sIsFirstAttach) {
            sIsFirstAttach = false;

            sPictureSize = AppConfig.readFileListPicSize(
                    context.getResources().getDimensionPixelSize(R.dimen.fileListPictureSize));

            sItemNormalBackground =
                    BaseActivity.getAttrColor(getContext(), android.R.attr.colorBackground);
            sItemSelectedBackground =
                    BaseActivity.getAttrColor(getContext(), R.attr.colorPrimary);
            sItemSelectedBackground = (sItemSelectedBackground & 0x00FFFFFF) | 0x55000000;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        if (mRootView != null) {
            return mRootView;
        }


        mRefreshAnimation = new ScaleAnimation(1f, 1f, 1.05f, 1f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0);
        mRefreshAnimation.setDuration(200);
        mRefreshAnimation.setInterpolator(new AccelerateInterpolator());

        View rootView = inflater.inflate(R.layout.fragment_explorer, container, false);
        rootView.setEnabled(false);

        ((TextView) rootView.findViewById(R.id.title)).setText(mTitle);
        mPathBarLayout = (AppBarLayout) rootView.findViewById(R.id.pathBarLayout);
        mPathBar = (PathBar) rootView.findViewById(R.id.pathBar);
        mRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipeRefreshLayout);
        mRecyclerView = (AutoFitRecyclerView) rootView.findViewById(R.id.fileList);
        mFabMenu = (FloatingActionButtonMenu) rootView.findViewById(R.id.fabMenu);
        mWaitView = (WaitView) rootView.findViewById(R.id.waitView);

        mFabMenu.setContents(
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
                mRenameFAB = mFabMenu.new SubItem(
                        getContext(), "重命名", R.drawable.ic_rename, mListener),
                mCutFAB = mFabMenu.new SubItem(
                        getContext(), "剪切", R.drawable.ic_cut, mListener),
                mCopyFAB = mFabMenu.new SubItem(
                        getContext(), "复制", R.drawable.ic_copy, mListener),
                mDeleteFAB = mFabMenu.new SubItem(
                        getContext(), "删除", R.drawable.ic_delete, mListener),
                mNewDirFAB = mFabMenu.new SubItem(
                        getContext(), "新建文件夹", R.drawable.ic_new_dir, mListener),
                mNewFileFAB = mFabMenu.new SubItem(
                        getContext(), "新建文件", R.drawable.ic_new_file, mListener),
                mSelectInverseFAB = mFabMenu.new SubItem(
                        getContext(), "反选", R.drawable.ic_select_inverse, mListener));

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

        mRecyclerView.addOnScrollListener(new ScrollToHideFabListener());
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

        if (clearSelection().size() > 0) {
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

    protected abstract void changeDir(FilePath newPath);

    protected abstract void enter(FileMeta meta);

    protected abstract void moreAction(FileMeta meta);

    protected abstract void newFile(boolean isDir);

    protected abstract void delete(Collection<FileMeta> allSelected);

    protected abstract void rename(FileMeta oldMeta);

    protected abstract void copyOrCut(Collection<FileMeta> allSelected, boolean isToCopy);

    protected abstract void paste();

    protected Drawable getIconDrawable(FileMeta meta) {
        return FileType.getIcon(getContext(), meta.type);
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
        mTitle = title;
        View view = getView();
        if (view != null) {
            ((TextView) view.findViewById(R.id.title)).setText(mTitle);
        }
    }

    protected void lockFragment(boolean toLock) {
        mLocked = toLock;
        mWaitView.setVisibility(mLocked ? View.VISIBLE : View.GONE);
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
        mPathBar.notifyPathChanged();

        mAdapter.mSelectedItems.clear();
        notifySelectionCountChanged();
        mAdapter.mMetaList.clear();
        mAdapter.mMetaList.addAll(allMeta);
        Collections.sort(mAdapter.mMetaList);
        mAdapter.notifyDataSetChanged();

        mRecyclerView.startAnimation(mRefreshAnimation);
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

    private Collection<FileMeta> clearSelection() {
        List<FileMeta> selected = new ArrayList<>(mAdapter.mSelectedItems.size());

        Iterator<Map.Entry<FileMeta, Integer>> it = mAdapter.mSelectedItems.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FileMeta, Integer> entry = it.next();
            selected.add(entry.getKey());
            int position = entry.getValue();
            it.remove();
            mAdapter.notifyItemChanged(position);
        }
        notifySelectionCountChanged();

        return selected;
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

    public static class PathBar extends HorizontalScrollView
            implements View.OnClickListener, View.OnLongClickListener {

        private final static float NORMAL_ALPHA = 0.5f;
        private final static float HIGHLIGHT_ALPHA = 1f;

        private ExplorerFragment mFragment;

        private int mMediumTextSize;
        private int mGap;

        private TypedValue mTypedValue = new TypedValue();

        private LinearLayout mContainer;
        private ImageView mRoot;
        private View mHighlightView = null;

        private FilePath mShowingPath = null;

        public PathBar(Context context) {
            super(context);
            init();
        }

        public PathBar(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public PathBar(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            getContext().getTheme().resolveAttribute(
                    R.attr.selectableItemBackground, mTypedValue, true);

            mContainer = new LinearLayout(getContext());
            mContainer.setOrientation(LinearLayout.HORIZONTAL);
            addView(mContainer, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);


            mMediumTextSize = getResources().getDimensionPixelSize(R.dimen.mediumTextSize);
            int mLargeTextSize = getResources().getDimensionPixelOffset(R.dimen.largeTextSize);
            mGap = getResources().getDimensionPixelSize(R.dimen.smallGap);

            mRoot = new ImageView(getContext());
            mContainer.addView(mRoot,
                    mLargeTextSize + 2 * mGap, ViewGroup.LayoutParams.MATCH_PARENT);
            mRoot.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_home));
            mRoot.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mRoot.setBackgroundResource(mTypedValue.resourceId);
            mRoot.setOnClickListener(this);
            mRoot.setOnLongClickListener(this);
            mRoot.setPadding(mGap, 0, mGap, 0);

            mHighlightView = mRoot;
            mHighlightView.setAlpha(HIGHLIGHT_ALPHA);
        }

        public void bindFragment(ExplorerFragment fragment) {
            mFragment = fragment;
        }

        public void notifyPathChanged() {
            FilePath newPath = mFragment.mCurDir;
            mHighlightView.setAlpha(NORMAL_ALPHA);

            if (newPath.cover(mShowingPath)) {
                mHighlightView = mContainer.getChildAt(
                        2 * newPath.getExtraDepthList(mFragment.mRootDir).size());

            } else {
                if (mShowingPath != null && mShowingPath.cover(newPath)) {
                    appendViews(newPath.getExtraDepthList(mShowingPath));

                } else {
                    mContainer.removeAllViews();
                    mContainer.addView(mRoot);
                    appendViews(newPath.getExtraDepthList(mFragment.mRootDir));
                }
                mShowingPath = newPath;
            }
            mHighlightView.setAlpha(HIGHLIGHT_ALPHA);

            post(new Runnable() {
                @Override
                public void run() {
                    Rect rect = new Rect();
                    mHighlightView.getHitRect(rect);
                    requestChildRectangleOnScreen(mContainer, rect, false);
                }
            });
        }


        private void appendViews(List<String> names) {

            for (int i = 0; i < names.size(); i++) {

                ImageView separator = new ImageView(getContext());
                mContainer.addView(separator, mMediumTextSize,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                separator.setImageDrawable(ContextCompat.getDrawable(
                        getContext(), R.drawable.ic_arrow_right));
                separator.setAlpha(NORMAL_ALPHA);
                separator.setScaleType(ImageView.ScaleType.FIT_CENTER);

                TextView directory = new TextView(getContext());
                mContainer.addView(directory,
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                directory.setGravity(Gravity.CENTER);
                directory.setText(names.get(i));
                directory.setAlpha(NORMAL_ALPHA);
                directory.setTextSize(TypedValue.COMPLEX_UNIT_PX, mMediumTextSize);
                directory.setTextColor(BaseActivity.getAttrColor(getContext(), R.attr.titleTextColor));
                directory.setBackgroundResource(mTypedValue.resourceId);
                directory.setPadding(mGap, 0, mGap, 0);
                directory.setOnClickListener(this);
                directory.setOnLongClickListener(this);

                if (i == names.size() - 1) {
                    mHighlightView = directory;
                }
            }
        }

        @Override
        public void onClick(View v) {
            if (mShowingPath == null || !isEnabled()) {
                return;
            }
            mFragment.changeDir(mShowingPath.getParent(
                    (mContainer.getChildCount() - mContainer.indexOfChild(v) - 1) / 2));
        }

        @Override
        public boolean onLongClick(View v) {
            if (mShowingPath == null || !isEnabled()) {
                return true;
            }
            String pathString = mShowingPath.getParent(
                    (mContainer.getChildCount() - mContainer.indexOfChild(v) - 1) / 2).pathString;
            ((ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText(pathString, pathString));
            Toast.makeText(getContext(), "路径已复制" + pathString, Toast.LENGTH_SHORT).show();
            return true;
        }
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

            mTypeView.setOnClickListener(this);
            setOnClickListener(this);
            setOnLongClickListener(this);

            checkResize(true);
        }

        private void checkResize(boolean forced) {
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
            checkResize(false);
        }

        @Override
        public void onClick(View v) {
            if (!mLocked) {
                if (v == mTypeView) {
                    onLongClick(this);
                    return;
                }

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

            itemView.mTypeView.setImageDrawable(getIconDrawable(meta));
            itemView.mNameView.setText(meta.name);
            itemView.mSizeView.setText(meta.size);
            itemView.mTimeView.setText(meta.time);
            itemView.setBackgroundColor(mSelectedItems.containsKey(meta) ?
                    sItemSelectedBackground : sItemNormalBackground);
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