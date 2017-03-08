package cn.gembit.transdev.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;

import cn.gembit.transdev.R;

public class AutoFitRecyclerView extends RecyclerView {

    private GridLayoutManager mLayoutManager;
    private int mColumn;

    public AutoFitRecyclerView(Context context) {
        super(context);
        init();
    }

    public AutoFitRecyclerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AutoFitRecyclerView(Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setLayoutManager(mLayoutManager = new GridLayoutManager(getContext(), mColumn = 1));
        addItemDecoration(new DividerItemDecoration(getContext()));
    }

    @Override
    public GridLayoutManager getLayoutManager() {
        return mLayoutManager;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            int column = 1;
            if (getResources().getConfiguration().orientation ==
                    Configuration.ORIENTATION_LANDSCAPE) {
                column++;
            }
            if ((getContext().getResources().getConfiguration().screenLayout &
                    Configuration.SCREENLAYOUT_SIZE_MASK) >=
                    Configuration.SCREENLAYOUT_SIZE_LARGE) {
                column++;
            }
            if (mColumn != column) {
                mLayoutManager.setSpanCount(mColumn = column);
            }
        }
        super.onLayout(changed, left, top, right, bottom);
    }


    private static class DividerItemDecoration extends RecyclerView.ItemDecoration {

        private final Rect mBounds = new Rect();
        private int mGap;
        private Drawable mDivider;

        private DividerItemDecoration(Context context) {
            final TypedArray a =
                    context.obtainStyledAttributes(new int[]{android.R.attr.listDivider});
            mDivider = a.getDrawable(0);
            a.recycle();

            mGap = context.getResources().getDimensionPixelSize(R.dimen.fileListHorizontalGap);
        }


        @Override
        public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
            canvas.save();

            canvas.clipRect(parent.getPaddingLeft(),
                    parent.getPaddingTop(),
                    parent.getWidth() - parent.getPaddingRight(),
                    parent.getHeight() - parent.getPaddingBottom());

            final int childCount = parent.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                parent.getDecoratedBoundsWithMargins(child, mBounds);
                final int bottom = mBounds.bottom + Math.round(ViewCompat.getTranslationY(child));
                final int top = bottom - mDivider.getIntrinsicHeight();
                final int left = mBounds.left + mGap;
                final int right = mBounds.right - mGap;
                mDivider.setBounds(left, top, right, bottom);
                mDivider.draw(canvas);
            }
            canvas.restore();
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            outRect.set(0, 0, 0, mDivider.getIntrinsicHeight());
        }
    }
}

