package cn.gembit.transdev.widgets;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.gembit.transdev.ui.ExplorerFragment;
import cn.gembit.transdev.file.FilePath;
import cn.gembit.transdev.R;

public class PathBar extends HorizontalScrollView
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

    public void notifyPathChanged(FilePath newPath) {
        mHighlightView.setAlpha(NORMAL_ALPHA);
        if (newPath.cover(mShowingPath)) {
            mHighlightView = mContainer.getChildAt(
                    2 * newPath.getExtraDepthList(mFragment.getRootDir()).size());

        } else {
            if (mShowingPath != null && mShowingPath.cover(newPath)) {
                appendViews(newPath.getExtraDepthList(mShowingPath));

            } else {
                mContainer.removeAllViews();
                mContainer.addView(mRoot);
                appendViews(newPath.getExtraDepthList(mFragment.getRootDir()));
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