package cn.gembit.transdev.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.addition.MyApp;

public abstract class InputDialog {

    private AlertDialog mDialog;
    private EditText mInputView;
    private TextView mErrorView;

    public InputDialog(Context context, String title) {

        Resources resources = context.getResources();
        int padding = resources.getDimensionPixelSize(R.dimen.mediumGap);
        int mediumTextSize = resources.getDimensionPixelSize(R.dimen.mediumTextSize);
        int largeTextSize = resources.getDimensionPixelSize(R.dimen.largeTextSize);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize);
        container.addView(titleView,
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        mErrorView = new TextView(context);
        mErrorView.setTextColor(MyApp.getColor(context, R.attr.textColorError));
        mErrorView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mediumTextSize);
        container.addView(mErrorView,
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        mInputView = new EditText(context);
        mInputView.setTextSize(TypedValue.COMPLEX_UNIT_PX, largeTextSize);
        container.addView(mInputView,
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        mDialog = new AlertDialog.Builder(context)
                .setView(container)
                .setCancelable(false)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
    }

    public InputDialog(Context context, String title, String defaultInput) {
        this(context, title);
        mInputView.setText(defaultInput);
    }

    public InputDialog(Context context, String title, String defaultInput,
                       int selectionStart, int selectionEnd) {
        this(context, title, defaultInput);
        mInputView.setSelection(Math.max(0, selectionStart),
                Math.min(defaultInput.length(), selectionEnd));
    }

    public abstract String checkOut(String input);

    public void show() {
        mDialog.show();
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String error = InputDialog.this.checkOut(
                        mInputView.getText().toString());
                if (error == null) {
                    mDialog.dismiss();
                } else {
                    mErrorView.setText(error);
                }
            }
        });
    }
}
