package cn.gembit.transdev.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.annotation.Px;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.activities.BaseActivity;

import static android.content.Context.WINDOW_SERVICE;

public class FloatingActionButtonMenu extends CardView {

    private final static Interpolator ANIMATION_INTERPOLATOR = new OvershootInterpolator();

    private final static RotateAnimation ROTATE_ANIMATION;
    private final static RotateAnimation REVERSE_ROTATE_ANIMATION;
    private final static AlphaAnimation APPEAR_ALPHA_ANIMATION;
    private final static AlphaAnimation DISAPPEAR_ALPHA_ANIMATION;

    private final static int ANIMATION_DURATION = 350;
    private final static float ROTATE_ANGLE = -45f;

    static {
        ROTATE_ANIMATION = new RotateAnimation(-ROTATE_ANGLE, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        REVERSE_ROTATE_ANIMATION = new RotateAnimation(ROTATE_ANGLE, 0f,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);

        ROTATE_ANIMATION.setDuration(ANIMATION_DURATION);
        ROTATE_ANIMATION.setInterpolator(ANIMATION_INTERPOLATOR);
        REVERSE_ROTATE_ANIMATION.setDuration(ANIMATION_DURATION);
        REVERSE_ROTATE_ANIMATION.setInterpolator(ANIMATION_INTERPOLATOR);

        APPEAR_ALPHA_ANIMATION = new AlphaAnimation(0f, 1f);
        DISAPPEAR_ALPHA_ANIMATION = new AlphaAnimation(1f, 0f);
    }

    private GridLayout mGridLayout;
    private ImageButton mBaseButton;
    private SubItem[] mSubItems;

    private boolean mExpanded;
    private boolean mAtDrawerMode;

    private Drawable mDrawerModeIcon;
    private Drawable mButtonModeIcon;

    private int mDarkBackground;
    private int mTransparentBackground;

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
        mGridLayout = new GridLayout(getContext());
        LayoutParams params =
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        int fabGap = getContext().getResources().getDimensionPixelSize(R.dimen.fabGap);
        mGridLayout.setPadding(fabGap, 0, fabGap, fabGap);
        addView(mGridLayout, params);

        mGridLayout.setClipChildren(false);
        mGridLayout.setClipToPadding(false);
        setClipChildren(false);
        setClipToPadding(false);

        mTransparentBackground = ContextCompat.getColor(getContext(), android.R.color.transparent);
        mDarkBackground = BaseActivity.getAttrColor(getContext(), android.R.attr.colorBackground);
        mDarkBackground = mDarkBackground & 0xffffff | 0x99000000;
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

        mBaseButton = new ImageButton(getContext());
        mBaseButton.setImageDrawable(mAtDrawerMode ? mDrawerModeIcon : mButtonModeIcon);
        mBaseButton.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.bg_floating_action_button));
        mBaseButton.setId(View.generateViewId());

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
        mBaseButton.startAnimation(toExpandIt ? ROTATE_ANIMATION : REVERSE_ROTATE_ANIMATION);

        setClickable(toExpandIt);
        setCardBackgroundColor(toExpandIt ? mDarkBackground : mTransparentBackground);

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
                    animation.addAnimation(APPEAR_ALPHA_ANIMATION);
                } else {
                    animation.addAnimation(new TranslateAnimation(0f, 0f, 0f, height));
                    animation.addAnimation(DISAPPEAR_ALPHA_ANIMATION);
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

        private ImageButton mButton;
        private TextView mTitle;

        public SubItem(Context context, String text, int drawableId, OnClickListener listener) {
            mValid = true;

            int hPadding = context.getResources()
                    .getDimensionPixelSize(R.dimen.fabTitleHorizontalPadding);
            int vPadding = context.getResources()
                    .getDimensionPixelOffset(R.dimen.fabTitleVerticalPadding);

            mButton = new ImageButton(context);
            mButton.setImageDrawable(ContextCompat.getDrawable(getContext(), drawableId));
            mButton.setBackground(ContextCompat.getDrawable(
                    getContext(), R.drawable.bg_floating_action_button));
            mButton.setId(View.generateViewId());
            mButton.setOnClickListener(listener);
            mButton.setVisibility(INVISIBLE);

            mTitle = new TextView(context);
            mTitle.setTextColor(BaseActivity.getAttrColor(getContext(), R.attr.titleTextColor));
            mTitle.setBackgroundColor(BaseActivity.getAttrColor(getContext(), R.attr.colorAccent));
            mTitle.setText(text);
            mTitle.setPadding(hPadding, vPadding, hPadding, vPadding);
            mTitle.setId(View.generateViewId());
            mTitle.setVisibility(INVISIBLE);
        }

        public void setValidity(boolean valid) {
            if (this.mValid = valid) {
                mTitle.setVisibility(mExpanded ? VISIBLE : INVISIBLE);
                mButton.setVisibility(mExpanded ? VISIBLE : INVISIBLE);
            } else {
                mTitle.setVisibility(GONE);
                mButton.setVisibility(GONE);
            }
        }

        public int getId() {
            return mButton.getId();
        }
    }
}

