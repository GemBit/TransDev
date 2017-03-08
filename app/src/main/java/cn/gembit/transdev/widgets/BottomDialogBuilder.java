package cn.gembit.transdev.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.BottomSheetDialog;
import android.support.v7.widget.AppCompatButton;
import android.view.View;
import android.widget.TextView;

import cn.gembit.transdev.R;
import cn.gembit.transdev.activities.BaseActivity;

public class BottomDialogBuilder {

    private BottomSheetDialog mDialog;

    public static BottomDialogBuilder make(Context context, String message) {
        final BottomDialogBuilder builder = new BottomDialogBuilder();
        builder.mDialog = new BottomSheetDialog(context);
        builder.mDialog.setCancelable(false);

        View rootView = View.inflate(context, R.layout.dialog_bottom, null);
        builder.mDialog.setContentView(rootView);

        ((TextView) rootView.findViewById(R.id.message)).setText(message);
        AppCompatButton btnOK = (AppCompatButton) rootView.findViewById(R.id.btnOK);
        btnOK.setText("确认");
        btnOK.setTextColor(BaseActivity.getAttrColor(context, R.attr.titleTextColor));
        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                builder.mDialog.dismiss();
            }
        });
        return builder;
    }

    public BottomDialogBuilder setOnDismissListener(DialogInterface.OnDismissListener listener) {
        mDialog.setOnDismissListener(listener);
        return this;
    }

    public void show() {
        mDialog.show();
    }
}
