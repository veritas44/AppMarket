package com.dongji.market.activity;

import java.util.ArrayList;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

import com.dongji.market.R;
import com.dongji.market.adapter.UninstallAdapter;
import com.dongji.market.download.AConstDefine;
import com.dongji.market.helper.AndroidUtils;
import com.dongji.market.helper.DJMarketUtils;
import com.dongji.market.helper.FileLoadTask;
import com.dongji.market.pojo.InstalledAppInfo;
import com.dongji.market.receiver.CommonReceiver;
import com.dongji.market.widget.CustomDialog;
import com.dongji.market.widget.LoginDialog;
import com.dongji.market.widget.ScrollListView;

public class Uninstall_list_Activity extends Activity implements
		ScrollListView.OnScrollTouchListener {

	public static final int FLAG_RESTORE = 1;
	public static final int FLAG_BACKUP = 2;

	private static final int EVENT_REQUEST_SOFTWARE_LIST = 0;
	private static final int EVENT_LOADED = 1;
	// private static final int EVENT_RESTORE = 2;
	// private static final int EVENT_BACKUP = 3;

	private UninstallAdapter adapter;
	private MyHandler mHandler;
	private LoginDialog dialog;
	private CustomDialog restoreDialog;

	private ScrollListView mListView;

	private FileLoadTask task;
	private View mLoadingView;

	// private NotificationManager mNotificationManager;
	// private Notification mNotification;
	public static boolean cloudBackupOngoing = false;
	public static boolean cloudRestoreOngoing = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list);

		// mNotificationManager = (NotificationManager)
		// getSystemService(NOTIFICATION_SERVICE);
		mListView = (ScrollListView) findViewById(R.id.list);
		mListView.setOnScrollTouchListener(this);
		Button mCloudRestore = (Button) findViewById(R.id.cloud_restore);
		Button mCloudBackup = (Button) findViewById(R.id.cloud_backup);

		initHandler();

		registerAllReceiver();

		mCloudRestore.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (!DJMarketUtils.isLogin(Uninstall_list_Activity.this)) {
					if (!isFinishing()) {
						// dialog = new
						// LoginDialog(Uninstall_list_Activity.this,
						// FLAG_RESTORE);
						// if (dialog != null) {
						// dialog.show();
						// }
						showLoginDialog(FLAG_RESTORE);
					}
				} else {
					if (cloudBackupOngoing) {
						AndroidUtils.showToast(Uninstall_list_Activity.this,
								R.string.backup_running_prompt);
					} else if (cloudRestoreOngoing) {
						AndroidUtils.showToast(Uninstall_list_Activity.this,
								R.string.restore_running_prompt);
					} else {
						if (!isFinishing()) {
							if (restoreDialog == null) {
								restoreDialog = new CustomDialog(
										Uninstall_list_Activity.this).setIcon(
										R.drawable.icon).setMessage(
										R.string.cloud_restore_prompt);
								restoreDialog.setPositiveButton(
										R.string.local_restore,
										new OnClickListener() {

											@Override
											public void onClick(View v) {
												Intent intent = new Intent(
														AConstDefine.BROADCAST_ACTION_SHOWBANDRLIST);
												intent.putExtra(
														AConstDefine.FLAG_ACTIVITY_BANDR,
														AConstDefine.ACTIVITY_RESTORE);
												sendBroadcast(intent);

												// Intent intent = new Intent();
												// intent.putExtra(
												// AConstDefine.FLAG_ACTIVITY_BANDR,
												// AConstDefine.ACTIVITY_RESTORE);
												// intent.setClass(
												// Uninstall_list_Activity.this,
												// BackupOrRestoreActivity.class);
												// startActivity(intent);

												restoreDialog.dismiss();
											}
										}).setNegativeButton(
										R.string.cloud_restore,
										new OnClickListener() {

											@Override
											public void onClick(View v) {
												// if (AndroidUtils
												// .isWifiAvailable(Uninstall_list_Activity.this))
												// {
												//
												// mHandler.sendEmptyMessageDelayed(
												// EVENT_RESTORE, 500);
												// showNotification(
												// R.drawable.icon,
												// getResources()
												// .getString(
												// R.string.cloud_restore),
												// getResources()
												// .getString(
												// R.string.cloud_restoring));

												// Intent intent = new Intent();
												// intent.putExtra(
												// AConstDefine.FLAG_ACTIVITY_BANDR,
												// AConstDefine.ACTIVITY_CLOUD_RESTORE);
												// intent.setClass(
												// Uninstall_list_Activity.this,
												// BackupOrRestoreActivity.class);
												// startActivity(intent);
												Intent intent = new Intent(
														AConstDefine.BROADCAST_ACTION_SHOWBANDRLIST);
												intent.putExtra(
														AConstDefine.FLAG_ACTIVITY_BANDR,
														AConstDefine.ACTIVITY_CLOUD_RESTORE);
												sendBroadcast(intent);
												// }
												// else {
												// AndroidUtils
												// .showToast(
												// Uninstall_list_Activity.this,
												// R.string.no_available_wifi);
												// }
												restoreDialog.dismiss();
											}
										});
								restoreDialog
										.setTitle(R.string.chooserestoretype);
							}
							if (restoreDialog != null) {
								restoreDialog.show();
							}
						}
					}
				}
			}
		});
		mCloudBackup.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (!DJMarketUtils.isLogin(Uninstall_list_Activity.this)) {
					if (!isFinishing()) {
						// LoginDialog dialog = new LoginDialog(
						// Uninstall_list_Activity.this, FLAG_BACKUP);
						// if (dialog != null) {
						// dialog.show();
						// }
						showLoginDialog(FLAG_BACKUP);
					}
				} else {
					if (cloudRestoreOngoing) {
						AndroidUtils.showToast(Uninstall_list_Activity.this,
								R.string.restore_running_prompt);
					} else if (cloudBackupOngoing) {
						AndroidUtils.showToast(Uninstall_list_Activity.this,
								R.string.backup_running_prompt);
					} else {
						if (!isFinishing()) {
							final CustomDialog backupDialog = new CustomDialog(
									Uninstall_list_Activity.this)
									.setIcon(R.drawable.icon);
							backupDialog.setPositiveButton(
									R.string.local_backup,
									new OnClickListener() {

										@Override
										public void onClick(View v) {
											// Intent intent = new Intent();
											// intent.putExtra(
											// AConstDefine.FLAG_ACTIVITY_BANDR,
											// AConstDefine.ACTIVITY_BACKUP);
											// intent.setClass(
											// Uninstall_list_Activity.this,
											// BackupOrRestoreActivity.class);
											// startActivity(intent);
											Intent intent = new Intent(
													AConstDefine.BROADCAST_ACTION_SHOWBANDRLIST);
											intent.putExtra(
													AConstDefine.FLAG_ACTIVITY_BANDR,
													AConstDefine.ACTIVITY_BACKUP);
											sendBroadcast(intent);
											backupDialog.dismiss();
										}
									}).setNegativeButton(R.string.cloud_backup,
									new OnClickListener() {

										@Override
										public void onClick(View v) {
											// TODO 云备份的操作
											// Intent intent = new Intent();
											// intent.putExtra(
											// AConstDefine.FLAG_ACTIVITY_BANDR,
											// AConstDefine.ACTIVITY_CLOUD_BACKUP);
											// intent.setClass(
											// Uninstall_list_Activity.this,
											// BackupOrRestoreActivity.class);
											// startActivity(intent);
											Intent intent = new Intent(
													AConstDefine.BROADCAST_ACTION_SHOWBANDRLIST);
											intent.putExtra(
													AConstDefine.FLAG_ACTIVITY_BANDR,
													AConstDefine.ACTIVITY_CLOUD_BACKUP);
											sendBroadcast(intent);
											backupDialog.dismiss();
										}
									});
							backupDialog.setTitle(R.string.choosebackuptype);
							if (backupDialog != null) {
								backupDialog.show();
							}
						}
					}
				}
			}
		});

		startLoad();
		
//		((SoftwareManageActivity)getParent()).setMenuSlide(mListView);

	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == FLAG_BACKUP) {
				Intent intent = new Intent(
						LoginDialog.BROADCAST_ACTION_SHOWBANDRLIST);
				intent.putExtra(LoginDialog.FLAG_ACTIVITY_BANDR,
						LoginDialog.ACTIVITY_CLOUD_BACKUP);
				sendBroadcast(intent);
			} else if (requestCode == FLAG_RESTORE) {
				Intent intent = new Intent(
						LoginDialog.BROADCAST_ACTION_SHOWBANDRLIST);
				intent.putExtra(LoginDialog.FLAG_ACTIVITY_BANDR,
						LoginDialog.ACTIVITY_CLOUD_RESTORE);
				sendBroadcast(intent);
			}
		}
	}

	private void showLoginDialog(int flag) {
		if (dialog == null) {
			dialog = new LoginDialog(this, flag);
		} else if (flag != dialog.getFlag()) {
			dialog.setFlag(flag);
			dialog.refreshContent();
		}
		if (dialog != null) {
			dialog.show();
		}
	}

	@Override
	public void onBackPressed() {
		finish();
	}

	private void registerAllReceiver() {

		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(AConstDefine.BROADCAST_SYS_ACTION_APPINSTALL);
		intentFilter.addAction(AConstDefine.BROADCAST_SYS_ACTION_APPREMOVE);
		intentFilter.addDataScheme("package");
		registerReceiver(mReceiver, intentFilter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return getParent().onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		// TODO Auto-generated method stub
		return getParent().onMenuOpened(featureId, menu);
	}

	private void startLoad() {
		mLoadingView = findViewById(R.id.loadinglayout);
		mHandler.sendEmptyMessage(EVENT_REQUEST_SOFTWARE_LIST);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(mReceiver);
		super.onDestroy();
	}

	class MyHandler extends Handler {

		public MyHandler(Looper looper) {
			super(looper);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_REQUEST_SOFTWARE_LIST:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						if (adapter == null) {
							adapter = new UninstallAdapter(
									Uninstall_list_Activity.this,
									new ArrayList<InstalledAppInfo>());
							mListView.setAdapter(adapter);
							mListView
									.setOnItemClickListener(new OnItemClickListener() {

										@Override
										public void onItemClick(
												AdapterView<?> parent,
												View view, int position, long id) {
											// TODO Auto-generated method stub
											AndroidUtils
													.showInstalledAppDetails(
															Uninstall_list_Activity.this,
															((InstalledAppInfo) adapter
																	.getItem(position))
																	.getPkgName());
										}
									});
						}
						task = new FileLoadTask(Uninstall_list_Activity.this,
								adapter, mHandler);// 本地图片异步加载
						task.execute();
					}
				});
				break;
			case EVENT_LOADED:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						mLoadingView.setVisibility(View.GONE);
						mListView.setVisibility(View.VISIBLE);
					}

				});
				break;
			// case EVENT_BACKUP:
			// /**
			// * 执行备份操作 ......
			// */
			// runOnUiThread(new Runnable() {
			//
			// @Override
			// public void run() {
			// if (progress != 100) {
			// progress += 10;
			// mNotification.contentView.setProgressBar(
			// R.id.cloud_opt_progress, 100, progress,
			// false);
			// mNotification.contentView.setTextViewText(
			// R.id.cloud_progress_rate, progress + "%");
			// sendEmptyMessageDelayed(EVENT_BACKUP, 500);
			// } else {
			// cloudBackupOngoing = false;
			// mNotification.contentView.setTextViewText(
			// R.id.cloud_title,
			// getResources().getString(
			// R.string.cloud_backup_completed));
			// AndroidUtils.showToast(
			// Uninstall_list_Activity.this,
			// R.string.cloud_backup_completed);
			// progress = 0;
			// }
			// mNotificationManager.notify(0, mNotification);
			// }
			// });
			// break;
			// case EVENT_RESTORE:
			// /**
			// * 执行恢复操作 ......
			// */
			// runOnUiThread(new Runnable() {
			//
			// @Override
			// public void run() {
			//
			// if (progress != 100) {
			// progress += 10;
			// mNotification.contentView.setProgressBar(
			// R.id.cloud_opt_progress, 100, progress,
			// false);
			// mNotification.contentView.setTextViewText(
			// R.id.cloud_progress_rate, progress + "%");
			// sendEmptyMessageDelayed(EVENT_RESTORE, 500);
			// } else {
			// cloudRestoreOngoing = false;
			// mNotification.flags = Notification.FLAG_AUTO_CANCEL;
			// mNotification.contentView.setTextViewText(
			// R.id.cloud_title,
			// getResources().getString(
			// R.string.cloud_restore_completed));
			// AndroidUtils.showToast(
			// Uninstall_list_Activity.this,
			// R.string.cloud_restore_completed);
			// progress = 0;
			// }
			// mNotificationManager.notify(0, mNotification);
			// }
			// });
			// break;
			default:
				break;
			}
		}

	}

	private void initHandler() {
		HandlerThread handlerThread = new HandlerThread("handler");
		handlerThread.start();
		mHandler = new MyHandler(handlerThread.getLooper());
	}

	// private void showNotification(int icon, String tickerText, String title)
	// {
	// // mNotification = new Notification(icon, tickerText,
	// // System.currentTimeMillis());
	// mNotification = new Notification();
	// mNotification.icon = icon;
	// mNotification.tickerText = tickerText;
	// // mNotification.when = System.currentTimeMillis();
	// mNotification.flags = Notification.FLAG_AUTO_CANCEL;
	//
	// // 点击notification的动作
	// // Intent intent = new Intent(this, SoftwareManageActivity.class);
	// Intent intent = new Intent();
	// PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
	// intent, 0);
	// mNotification.contentIntent = pendingIntent;
	//
	// RemoteViews remoteViews = new RemoteViews(getPackageName(),
	// R.layout.layout_cloud_progress);
	// remoteViews.setTextViewText(R.id.cloud_title, title);
	// remoteViews.setTextViewText(R.id.cloud_progress_rate, "0%");
	// remoteViews.setProgressBar(R.id.cloud_opt_progress, 100, 0, false);
	// mNotification.contentView = remoteViews;
	//
	// mNotificationManager.notify(0, mNotification);
	// }

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String packageName;
			if (AConstDefine.BROADCAST_SYS_ACTION_APPINSTALL.equals(intent
					.getAction())) {
				if (adapter != null) {
					packageName = DJMarketUtils.convertPackageName(intent
							.getDataString());
					InstalledAppInfo info = DJMarketUtils
							.getInstalledAppInfoByPackageName(context,
									packageName);
					adapter.addAppData(info);
				}
			} else if (AConstDefine.BROADCAST_SYS_ACTION_APPREMOVE
					.equals(intent.getAction())) {
				if (adapter != null) {
					packageName = DJMarketUtils.convertPackageName(intent
							.getDataString());
					adapter.removeAppDataByPackageName(packageName);
				}
			}
		}
	};

	@Override
	public void onScrollTouch(int scrollState) {
		switch (scrollState) {
		case ScrollListView.OnScrollTouchListener.SCROLL_BOTTOM:
//			((SoftwareManageActivity) getParent()).scrollOperation(true);
			break;
		case ScrollListView.OnScrollTouchListener.SCROLL_TOP:
//			((SoftwareManageActivity) getParent()).scrollOperation(false);
			break;
		}
	}

	private int locStep;
	void onToolBarClick() {
		if(mListView!=null) {
//			if (!mListView.isStackFromBottom()) {
//				mListView.setStackFromBottom(true);
//			}
//			mListView.setStackFromBottom(false);
			locStep = (int) Math.ceil(mListView.getFirstVisiblePosition()/AConstDefine.AUTO_SCRLL_TIMES);
			mListView.post(scrollToTop);
		}
	}
	
	Runnable scrollToTop = new Runnable() {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			if (mListView.getFirstVisiblePosition() > 0) {
				if (mListView.getFirstVisiblePosition() < AConstDefine.AUTO_SCRLL_TIMES) {
					mListView.setSelection(mListView
							.getFirstVisiblePosition() - 1);
				} else {
					mListView.setSelection(Math.max(mListView.getFirstVisiblePosition() - locStep, 0));
				}
				// mAppListView.postDelayed(this, 1);
				mListView.post(this);
			}
			return;
		}
	};
}