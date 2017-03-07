package cn.gembit.transdev.activities;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import cn.gembit.transdev.R;
import cn.gembit.transdev.file.FileMeta;
import cn.gembit.transdev.work.GlobalClipboard;
import cn.gembit.transdev.work.TaskService;
import cn.gembit.transdev.widgets.AutoFitRecyclerView;

public class TaskActivity extends BaseActivity {

    private GridLayoutManager mManager;
    private TaskListAdapter mAdapter;
    private Timer mTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task);

        Toolbar toolbar = (Toolbar) findViewById(R.id.title);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            Drawable drawable = toolbar.getNavigationIcon();
            if (drawable != null) {
                drawable.setColorFilter(BaseActivity.getColor(this, R.attr.titleTextColor),
                        PorterDuff.Mode.SRC_ATOP);
            }
        }

        mAdapter = new TaskListAdapter();
        if (mAdapter.mTasks.size() > 0) {
            findViewById(R.id.bgEmpty).setVisibility(View.GONE);
            AutoFitRecyclerView recyclerView = ((AutoFitRecyclerView) findViewById(R.id.taskList));
            recyclerView.setAdapter(mAdapter);
            recyclerView.setItemAnimator(null);
            mManager = recyclerView.getLayoutManager();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mManager != null) {
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int first = mManager.findFirstVisibleItemPosition();
                            int end = mManager.findLastVisibleItemPosition();
                            mAdapter.notifyItemRangeChanged(first, end - first + 1);

                        }
                    });
                }
            }, 500, 500);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mTimer != null) {
            mTimer.cancel();
        }
    }


    private static class ItemViewHolder extends RecyclerView.ViewHolder {

        private boolean mNewBorn;
        private boolean mHasDied;

        private AppCompatButton mBtnKillTask;

        private TextView mTvTaskTitle;
        private TextView mTvTaskSrcDest;
        private TextView mTvTaskStatus;

        private TextView mTvDirTotal;
        private TextView mTvDirDone;
        private TextView mTvFileTotal;
        private TextView mTvFileDone;
        private TextView mTvSizeTotal;
        private TextView mTvSizeDone;

        private TextView mTvTaskError;

        private ItemViewHolder(View itemView, final TaskActivity activity) {
            super(itemView);
            mNewBorn = true;
            mHasDied = false;

            mBtnKillTask = (AppCompatButton) itemView.findViewById(R.id.killTask);

            mTvTaskTitle = (TextView) itemView.findViewById(R.id.taskTitle);
            mTvTaskSrcDest = (TextView) itemView.findViewById(R.id.taskSrcDest);
            mTvTaskStatus = (TextView) itemView.findViewById(R.id.taskStatus);

            mTvDirTotal = (TextView) itemView.findViewById(R.id.dirTotal);
            mTvDirDone = (TextView) itemView.findViewById(R.id.dirDone);
            mTvFileTotal = (TextView) itemView.findViewById(R.id.fileTotal);
            mTvFileDone = (TextView) itemView.findViewById(R.id.fileDone);
            mTvSizeTotal = (TextView) itemView.findViewById(R.id.sizeTotal);
            mTvSizeDone = (TextView) itemView.findViewById(R.id.sizeDone);

            mTvTaskError = (TextView) itemView.findViewById(R.id.taskError);

            mBtnKillTask.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(activity)
                            .setCancelable(false)
                            .setMessage("终止任务吗？")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    int position = getAdapterPosition();
                                    activity.mAdapter.mTasks.get(position).kill();
                                    activity.mAdapter.notifyItemChanged(position);
                                }
                            })
                            .show();
                }
            });
        }
    }

    private class TaskListAdapter extends RecyclerView.Adapter<ItemViewHolder> {

        private List<TaskService.Task> mTasks;

        private TaskListAdapter() {
            mTasks = TaskService.getTasks();
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ItemViewHolder(LayoutInflater.from(TaskActivity.this)
                    .inflate(R.layout.item_task_list, parent, false),
                    TaskActivity.this);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            if (holder.mHasDied) {
                return;
            }

            TaskService.Task task = mTasks.get(holder.getAdapterPosition());

            if (holder.mNewBorn) {
                holder.mNewBorn = false;

                holder.mTvTaskTitle.setText(task.source.isToCopy ? "复制任务" : "剪切任务");

                String srcDest = "从【";
                if (task.source instanceof GlobalClipboard.Source.Local) {
                    srcDest += "本地";
                } else {
                    srcDest += ((GlobalClipboard.Source.Client) task.source).connectArg.alias;
                }
                srcDest += ":" + task.source.dir.pathString + "】到【";
                if (task.destination instanceof GlobalClipboard.Destination.Local) {
                    srcDest += "本地";
                } else {
                    srcDest += ((GlobalClipboard.Destination.Client) task.destination)
                            .connectArg.alias;
                }
                srcDest += ":" + task.destination.dir.pathString + "】";
                holder.mTvTaskSrcDest.setText(srcDest);
            }


            String fileName = task.currentFile == null ? "" :
                    task.currentFile.getExtraDepthString(task.source.dir);
            if (task.status == TaskService.Task.STATUS.analyzing) {
                holder.mTvTaskStatus.setText("正在分析");

            } else if (task.status == TaskService.Task.STATUS.transferring) {
                holder.mTvTaskStatus.setText("正在复制：".concat(fileName));

            } else if (task.status == TaskService.Task.STATUS.deleting) {
                holder.mTvTaskStatus.setText("正在删除：".concat(fileName));

            } else if (task.status == TaskService.Task.STATUS.finished ||
                    task.status == TaskService.Task.STATUS.interrupted) {
                holder.mHasDied = true;
                holder.mTvTaskStatus.setVisibility(View.GONE);
                if (task.status == TaskService.Task.STATUS.finished) {
                    holder.mBtnKillTask.setText("已完成");
                } else {
                    holder.mBtnKillTask.setText("已终止");
                }

                ViewCompat.setBackgroundTintList(holder.mBtnKillTask, ColorStateList.valueOf(
                        BaseActivity.getColor(TaskActivity.this, android.R.attr.colorBackground)));
                holder.mBtnKillTask.setTextColor(BaseActivity.getColor(TaskActivity.this,
                        android.R.attr.textColor));
                holder.mBtnKillTask.setEnabled(false);
            }

            holder.mTvDirTotal.setText(String.valueOf(task.dirTotal));
            holder.mTvDirDone.setText(String.valueOf(task.dirDone));
            holder.mTvFileTotal.setText(String.valueOf(task.fileTotal));
            holder.mTvFileDone.setText(String.valueOf(task.fileDone));
            holder.mTvSizeTotal.setText(FileMeta.formatSize(task.sizeTotal));
            holder.mTvSizeDone.setText(FileMeta.formatSize(task.sizeDone));

            if (task.status == TaskService.Task.STATUS.interrupted) {
                holder.mTvTaskError.setVisibility(View.VISIBLE);
                holder.mTvTaskError.setText("任务被终止");
            } else {
                StringBuilder builder = new StringBuilder();
                if (task.analysisError > 0) {
                    builder.append("\n分析过程出错数：").append(task.analysisError);
                }
                if (task.transferError > 0) {
                    builder.append("\n传输过程出错数：").append(task.transferError);
                }
                if (task.deletionError > 0) {
                    builder.append("\n删除过程出错数：").append(task.deletionError);
                }
                if (builder.length() > 0) {
                    holder.mTvTaskError.setVisibility(View.VISIBLE);
                    holder.mTvTaskError.setText(builder.substring(1));
                }
            }
        }
    }
}
