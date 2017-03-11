package cn.gembit.transdev.widgets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.Px;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.activities.BaseActivity;

import static android.content.Context.WINDOW_SERVICE;

public class FloatingActionButtonMenu extends FrameLayout {

    private final static Interpolator ANIMATION_INTERPOLATOR = new DecelerateInterpolator(2.5f);

    private final static int ANIMATION_DURATION = 350;
    private final static float ROTATE_ANGLE = -45f;

    private RotateAnimation mRotateAnimation;
    private RotateAnimation mReverseRotateAnimation;
    private AlphaAnimation mAppearAlphaAnimation;
    private AlphaAnimation mDisappearAlphaAnimation;

    private long mAnimationStartTime;

    private GridLayout mGridLayout;
    private AppCompatImageButton mBaseButton;
    private SubItem[] mSubItems;

    private boolean mExpanded;
    private boolean mAtDrawerMode;

    private Drawable mDrawerModeIcon;
    private Drawable mButtonModeIcon;

    private int mDarkBackground;
    private int mTransparentBackground;

    private ColorStateList mBackgroundTintList;

    public FloatingActionButtonMenu(Context context) {
        super(context);
        init();
    }

    public FloatingActionButtonMenu(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FloatingActionButtonMenu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mRotateAnimation = new RotateAnimation(-ROTATE_ANGLE, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mReverseRotateAnimation = new RotateAnimation(ROTATE_ANGLE, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        mRotateAnimation.setDuration(ANIMATION_DURATION);
        mRotateAnimation.setInterpolator(ANIMATION_INTERPOLATOR);
        mReverseRotateAnimation.setDuration(ANIMATION_DURATION);
        mReverseRotateAnimation.setInterpolator(ANIMATION_INTERPOLATOR);

        mAppearAlphaAnimation = new AlphaAnimation(0f, 1f);
        mDisappearAlphaAnimation = new AlphaAnimation(1f, 0f);

        mGridLayout = new GridLayout(getContext());
        LayoutParams params =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        int fabGap = getContext().getResources().getDimensionPixelSize(R.dimen.fabGap);
        mGridLayout.setPadding(fabGap, 0, fabGap, fabGap);
        addView(mGridLayout, params);

        mTransparentBackground = ContextCompat.getColor(getContext(), android.R.color.transparent);
        mDarkBackground = BaseActivity.getAttrColor(getContext(), android.R.attr.colorBackground);
        mDarkBackground = mDarkBackground & 0xffffff | 0x99000000;

        mBackgroundTintList = new ColorStateList(
                new int[][]{{android.R.attr.state_pressed},
                        {}},
                new int[]{BaseActivity.getAttrColor(getContext(), R.attr.colorAccentDark),
                        BaseActivity.getAttrColor(getContext(), R.attr.colorAccent)});
    }

    public void setContents(int drawerModeIconId, int buttonModeIconId,
                            final OnClickListener clickListener,
                            final OnLongClickListener longClickListener,
                            SubItem... subItems) {
        mSubItems = subItems == null ? new SubItem[0] : subItems;
        mExpanded = false;
        mAtDrawerMode = true;
        mDrawerModeIcon = ContextCompat.getDrawable(getContext(), drawerModeIconId);
        mButtonModeIcon = ContextCompat.getDrawable(getContext(), buttonModeIconId);

        mGridLayout.setRowCount(mSubItems.length + 1);
        mGridLayout.setColumnCount(2);

        int fabGap = getContext().getResources().getDimensionPixelSize(R.dimen.fabGap);
        int fabNormal = getContext().getResources().getDimensionPixelSize(R.dimen.fabNormal);
        int fabMini = getContext().getResources().getDimensionPixelSize(R.dimen.fabMini);

        mBaseButton = new AppCompatImageButton(getContext());
        mBaseButton.setImageDrawable(mAtDrawerMode ? mDrawerModeIcon : mButtonModeIcon);
        mBaseButton.setBackgroundResource(R.drawable.bg_circle);
        ViewCompat.setBackgroundTintList(mBaseButton, mBackgroundTintList);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                GridLayout.spec(mSubItems.length), GridLayout.spec(1));
        params.width = params.height = fabNormal;
        params.setGravity(Gravity.CENTER);
        params.setMargins(fabGap, fabGap, fabGap, fabGap);
        mGridLayout.addView(mBaseButton, params);

        mGridLayout.setLayoutDirection(View.LAYOUT_DIRECTION_INHERIT);

        boolean ltr = getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_LTR;
        int gravity = (ltr ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;

        for (int i = 0; i < mSubItems.length; i++) {
            params = new GridLayout.LayoutParams(GridLayout.spec(i), GridLayout.spec(0));
            params.width = params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.setGravity(gravity);
            mGridLayout.addView(mSubItems[i].mTitle, params);

            params = new GridLayout.LayoutParams(GridLayout.spec(i), GridLayout.spec(1));
            params.width = params.height = fabMini;
            params.setMargins(fabGap, fabGap, fabGap, fabGap);
            params.setGravity(Gravity.CENTER);
            mGridLayout.addView(mSubItems[i].mButton, params);
        }

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                expand(false);
            }
        });
        setClickable(false);

        mBaseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mAtDrawerMode) {
                    expand(!mExpanded);
                } else if (isEnabled() && clickListener != null) {
                    clickListener.onClick(mBaseButton);
                }
            }
        });

        mBaseButton.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return !mAtDrawerMode && longClickListener != null &&
                        longClickListener.onLongClick(mBaseButton);
            }
        });
    }


    public void setToBeDrawerMode(boolean toBeDrawerMode) {
        mAtDrawerMode = toBeDrawerMode;
        mBaseButton.setImageDrawable(toBeDrawerMode ? mDrawerModeIcon : mButtonModeIcon);
        if (!toBeDrawerMode) {
            expand(false);
        }
    }

    public boolean expand(boolean toExpandIt) {
        if (toExpandIt == mExpanded) {
            return false;
        } else {
            mExpanded = toExpandIt;
        }

        mBaseButton.setRotation(toExpandIt ? ROTATE_ANGLE : 0f);
        mBaseButton.startAnimation(toExpandIt ? mRotateAnimation : mReverseRotateAnimation);
        mAnimationStartTime = System.currentTimeMillis();

        setClickable(toExpandIt);
        setBackgroundColor(toExpandIt ? mDarkBackground : mTransparentBackground);

        int visibility = toExpandIt ? VISIBLE : INVISIBLE;

        for (SubItem item : mSubItems) {
            if (item.mValid) {
                int height = (int) ((mBaseButton.getY() + mBaseButton.getHeight() / 2) -
                        (item.mButton.getY() + item.mButton.getHeight() / 2));

                item.mTitle.setVisibility(visibility);
                item.mButton.setVisibility(visibility);

                AnimationSet animation = new AnimationSet(true);
                animation.setDuration(ANIMATION_DURATION);
                animation.setInterpolator(ANIMATION_INTERPOLATOR);

                if (toExpandIt) {
                    animation.addAnimation(new TranslateAnimation(0f, 0f, height, 0f));
                    animation.addAnimation(mAppearAlphaAnimation);
                } else {
                    animation.addAnimation(new TranslateAnimation(0f, 0f, 0f, height));
                    animation.addAnimation(mDisappearAlphaAnimation);
                }

                item.mButton.startAnimation(animation);
                item.mTitle.startAnimation(animation);
            }
        }
        return true;
    }

    public int heightFromScreenBottom() {
        Point size = new Point();
        ((WindowManager) getContext().getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getSize(size);

        int[] location = new int[2];
        mBaseButton.getLocationOnScreen(location);
        return size.y - location[1];
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        boolean ltr = newConfig.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR;
        int gravity = (ltr ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;

        for (SubItem item : mSubItems) {
            GridLayout.LayoutParams params =
                    (GridLayout.LayoutParams) item.mTitle.getLayoutParams();
            params.setGravity(gravity);
        }
    }


    @Override
    public void offsetTopAndBottom(@Px int offset) {
        mGridLayout.offsetTopAndBottom(offset);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (SubItem item : mSubItems) {
            item.mButton.setEnabled(enabled);
        }
        mBaseButton.setEnabled(enabled);
    }

    public class SubItem {

        private boolean mValid;

        private AppCompatImageButton mButton;
        private TextView mTitle;

        public SubItem(Context context, String text, int drawableId, OnClickListener listener) {
            mValid = true;

            int hPadding = context.getResources()
                    .getDimensionPixelSize(R.dimen.fabTitleHorizontalPadding);
            int vPadding = context.getResources()
                    .getDimensionPixelOffset(R.dimen.fabTitleVerticalPadding);

            mButton = new AppCompatImageButton(context);
            mButton.setImageDrawable(ContextCompat.getDrawable(getContext(), drawableId));
            mButton.setBackgroundResource(R.drawable.bg_circle);
            ViewCompat.setBackgroundTintList(mButton, mBackgroundTintList);
            mButton.setOnClickListener(listener);
            mButton.setVisibility(INVISIBLE);
            mButton.setId(View.generateViewId());

            mTitle = new TextView(context);
            mTitle.setTextColor(BaseActivity.getAttrColor(getContext(), R.attr.titleTextColor));
            mTitle.setBackgroundColor(BaseActivity.getAttrColor(getContext(), R.attr.colorAccent));
            mTitle.setText(text);
            mTitle.setPadding(hPadding, vPadding, hPadding, vPadding);
            mTitle.setVisibility(INVISIBLE);
        }

        public void setValidity(final boolean valid) {
            long delay;
            if (mBaseButton.getAnimation() == null) {
                delay = 0;
            } else {
                delay = mBaseButton.getAnimation().getDuration() -
                        (System.currentTimeMillis() - mAnimationStartTime);
                delay = delay > 0 ? delay : 0;
            }
            mButton.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mValid = valid) {
                        mTitle.setVisibility(mExpanded ? VISIBLE : INVISIBLE);
                        mButton.setVisibility(mExpanded ? VISIBLE : INVISIBLE);
                    } else {
                        mTitle.setVisibility(GONE);
                        mButton.setVisibility(GONE);
                    }
                    mTitle.clearAnimation();
                    mButton.clearAnimation();
                }
            }, delay);
        }

        public int getId() {
            return mButton.getId();
        }
    }
}

