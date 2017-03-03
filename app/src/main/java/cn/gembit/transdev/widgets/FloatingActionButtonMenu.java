package cn.gembit.transdev.widgets;

import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.CardView;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import cn.gembit.transdev.R;

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

    private RelativeLayout mRelativeLayout;
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
        mRelativeLayout = new RelativeLayout(getContext());
        addView(mRelativeLayout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        mTransparentBackground = ContextCompat.getColor(getContext(), android.R.color.transparent);
        mDarkBackground = ContextCompat.getColor(getContext(), R.color.colorBackground);
        mDarkBackground = mDarkBackground & 0xffffff | 0x99000000;
    }

    public void setContent(int drawerModeIconId, int buttonModeIconId,
                           final OnClickListener clickListener,
                           final OnLongClickListener longClickListener,
                           SubItem... subItems) {
        mSubItems = subItems == null ? new SubItem[0] : subItems;
        mExpanded = false;
        mAtDrawerMode = true;
        mDrawerModeIcon = ContextCompat.getDrawable(getContext(), drawerModeIconId);
        mButtonModeIcon = ContextCompat.getDrawable(getContext(), buttonModeIconId);

        mBaseButton = new ImageButton(getContext());
        mBaseButton.setImageDrawable(mAtDrawerMode ? mDrawerModeIcon : mButtonModeIcon);
        mBaseButton.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.round_button));
        mBaseButton.setId(View.generateViewId());

        int fabMargin = getContext().getResources().getDimensionPixelSize(R.dimen.fabMargin);
        int fabNormal = getContext().getResources().getDimensionPixelSize(R.dimen.fabNormal);
        int fabMini = getContext().getResources().getDimensionPixelSize(R.dimen.fabMini);

        RelativeLayout.LayoutParams baseButtonParams
                = new RelativeLayout.LayoutParams(fabNormal, fabNormal);
        baseButtonParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        baseButtonParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        baseButtonParams.setMargins(0, 0, fabMargin, fabMargin);
        mRelativeLayout.addView(mBaseButton, baseButtonParams);

        ImageButton lowerOne = mBaseButton;
        for (SubItem item : mSubItems) {
            item.mButtonParas.addRule(RelativeLayout.ABOVE, lowerOne.getId());
            item.mButtonParas.width = item.mButtonParas.height = fabMini;
            mRelativeLayout.addView(item.mButton, item.mButtonParas);
            mRelativeLayout.addView(item.mTitle, item.mTitleParas);
            lowerOne = item.mButton;
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
        int[] location = new int[2];
        ((WindowManager) getContext().getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getSize(size);
        mBaseButton.getLocationOnScreen(location);
        return size.y - location[1];
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (SubItem item : mSubItems) {
            item.mButton.setEnabled(enabled);
        }
        mBaseButton.setEnabled(enabled);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        for (SubItem item : mSubItems) {
            int deltaX = (int) ((mBaseButton.getX() + mBaseButton.getWidth() / 2) -
                    (item.mButton.getX() + item.mButton.getWidth() / 2));
            item.mButton.offsetLeftAndRight(deltaX);
            item.mTitle.offsetLeftAndRight(deltaX);
            int deltaY = (item.mTitle.getHeight() - item.mButton.getHeight()) / 2;
            item.mTitle.offsetTopAndBottom(deltaY);
        }
    }

    public class SubItem {

        private boolean mValid;

        private ImageButton mButton;
        private TextView mTitle;

        private RelativeLayout.LayoutParams mButtonParas;
        private RelativeLayout.LayoutParams mTitleParas;

        public SubItem(Context context, String text, int drawableId, OnClickListener listener) {
            mValid = true;

            int fabMargin = context.getResources().getDimensionPixelOffset(R.dimen.fabMargin);
            int hPadding = context.getResources()
                    .getDimensionPixelSize(R.dimen.fabTitleHorizontalPadding);
            int vPadding = context.getResources()
                    .getDimensionPixelOffset(R.dimen.fabTitleVerticalPadding);

            mButton = new ImageButton(context);
            mButton.setImageDrawable(ContextCompat.getDrawable(getContext(), drawableId));
            mButton.setBackground(ContextCompat.getDrawable(getContext(), R.drawable.round_button));
            mButton.setId(View.generateViewId());
            mButton.setOnClickListener(listener);
            mButton.setVisibility(INVISIBLE);

            mTitle = new TextView(context);
            mTitle.setTextColor(ContextCompat.getColor(getContext(), R.color.textColorInverse));
            mTitle.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.textColor));
            mTitle.setText(text);
            mTitle.setPadding(hPadding, vPadding, hPadding, vPadding);
            mTitle.setId(View.generateViewId());
            mTitle.setVisibility(INVISIBLE);

            mButtonParas = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            mButtonParas.addRule(RelativeLayout.ALIGN_PARENT_END);
            mButtonParas.setMargins(0, 0, 0, fabMargin);

            mTitleParas = new RelativeLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            mTitleParas.addRule(RelativeLayout.START_OF, mButton.getId());
            mTitleParas.addRule(RelativeLayout.ALIGN_BOTTOM, mButton.getId());
            mTitleParas.setMargins(fabMargin, 0, fabMargin, 0);
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

