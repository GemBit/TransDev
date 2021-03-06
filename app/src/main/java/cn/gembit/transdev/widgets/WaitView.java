package cn.gembit.transdev.widgets;

import android.content.Context;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.activities.BaseActivity;

public class WaitView extends FrameLayout {

    private final static int APPEAR_DELAY = 500;

    private RotateAnimation mAnimation;

    private Handler mHandler;
    private Runnable mAppearance;

    public WaitView(Context context) {
        super(context);
        init();
    }

    public WaitView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaitView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        super.setVisibility(GONE);

        mAnimation = new RotateAnimation(0, 360,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        mAnimation.setRepeatCount(Animation.INFINITE);
        mAnimation.setDuration(1000);
        mAnimation.setInterpolator(new LinearInterpolator());

        ImageView background = new ImageView(getContext());
        background.setImageDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.ic_wait_background));
        background.setColorFilter(BaseActivity.getAttrColor(getContext(), R.attr.colorAccent));
        addView(background, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        ImageView foreground = new ImageView(getContext());
        foreground.setImageDrawable(
                ContextCompat.getDrawable(getContext(), R.drawable.ic_wait_foreground));
        foreground.setColorFilter(BaseActivity.getAttrColor(getContext(), R.attr.colorPrimary));
        addView(foreground, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        mHandler = new Handler();
        mAppearance = new Runnable() {
            @Override
            public void run() {
                WaitView.super.setVisibility(VISIBLE);
                startAnimation(mAnimation);
            }
        };
    }

    @Override
    public void setVisibility(int visibility) {
        if (visibility == VISIBLE) {
            mHandler.postDelayed(mAppearance, APPEAR_DELAY);
        } else {
            mHandler.removeCallbacks(mAppearance);
            super.setVisibility(visibility);
            clearAnimation();
        }
    }
}