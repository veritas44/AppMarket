package com.dongji.market.download;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.dongji.market.adapter.DownloadAdapter;
import com.dongji.market.application.AppMarket;
import com.dongji.market.database.DownloadDBHelper;
import com.dongji.market.helper.AndroidUtils;
import com.dongji.market.helper.DJMarketUtils;
import com.dongji.market.pojo.ApkItem;
import com.dongji.market.protocol.DataManager;

public class DownloadService extends Service implements DownloadConstDefine,
		OnDownloadListener {
	private static final int EVENT_QUERY_DOWNLOAD = 1; // 查询所有下载
	private static final int EVENT_ADD_DOWNLOAD = 2; // 添加应用到下载队列开始下载
	private static final int EVENT_UPDATE_DATA_DONE = 3; // 当更新数据请求到了
	private static final int EVENT_DWONLOAD_NEXT = 4; // 处理下一个下载任务
	private static final int EVENT_DOWNLOAD_CANCEL = 5; // 处理下载取消
	private static final int EVENT_DOWNLOAD_COMPLETE = 6; // 处理下载完成
	private static final int EVENT_ONEKEY_UPDATE = 7; // 一键更新广播
	private static final int EVENT_REMOVE_DOWNLOAD = 8; // 删除下载
	private static final int EVENT_CONTINUE_DOWNLOAD = 9; // 继续下载
	private static final int EVENT_IGNORE_UPDATE = 10; // 忽略更新
	private static final int EVENT_CANCEL_IGNORE = 11; // 取消忽略
	private static final int EVENT_SINGLE_UPDATE_DATA_DONE = 12; // 单个应用的更新数据请求处理
	private static final int EVENT_START_ALL_DOWNLOAD = 13; // 下载所有应用
	private static final int EVENT_CLOUD_RESTORE = 14; // 下载云恢复
	private static final int EVENT_SEND_STATISTICS_INSTALL = 15; // 安装统计

	private static List<DownloadEntity> downloadList = new ArrayList<DownloadEntity>(); // 下载队列
	private static final int MAX_DOWNLOAD_NUM = 3; // 最大下载数量
	private int currentDownloadNum; // 当前下载数量
	public static DownloadService mDownloadService;

	private static DownloadStatusListener mDownloadStatusListener;

	private static Object obj = new Object();

	private DownloadDBHelper db;

	private MyHandler mHandler;
	private AppMarket mApp;

	private long currentGprsTraffic; // 当前已使用的流量
	private long maxGprsTraffic; // 设置的最大流量
	private boolean isOnlyWifi; // 是否只使用 Wifi 下载

	private boolean isBackgroundDownload;

	@Override
	public void onCreate() {
		super.onCreate();

		System.out.println("download service oncreate!");

		mDownloadService = this;
		mApp = (AppMarket) getApplication();
		isBackgroundDownload = false;
		registerAllReceiver();
		initHandler();
	}

	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

	};

	public void onDestroy() {
		super.onDestroy();
		System.out.println("download service ondestroy");
		saveGprsTraffic();
		saveDownloadInDB();
		downloadList.clear();
		unregisterAllReceiver();
	};

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	private void initHandler() {
		HandlerThread mHandlerThread = new HandlerThread(
				"DownloadServiceHandler");
		mHandlerThread.start();
		mHandler = new MyHandler(mHandlerThread.getLooper());
		mHandler.sendEmptyMessage(EVENT_QUERY_DOWNLOAD);
	}

	/**
	 * 注册所需广播
	 */
	private void registerAllReceiver() {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BROADCAST_ACTION_ADD_DOWNLOAD); // 添加下载应用
		intentFilter.addAction(BROADCAST_ACTION_APP_UPDATE_DATADONE); // 更新数据已请求到
		intentFilter.addAction(BROADCAST_ACTION_PAUSE_DOWNLOAD); // 暂停下载应用
		intentFilter.addAction(BROADCAST_ACTION_ONEKEY_UPDATE); // 一键更新
		intentFilter.addAction(BROADCAST_ACTION_CANCEL_DOWNLOAD); // 取消下载
		intentFilter.addAction(BROADCAST_ACTION_REMOVE_DOWNLOAD); // 删除下载
		// intentFilter.addAction(BROADCAST_ACTION_TRAFFIC_OVER); // 流量消耗完广播
		intentFilter.addAction(BROADCAST_ACTION_GPRS_SETTING_CHANGE); // 设置流量改变广播
		intentFilter.addAction(BROADCAST_ACTION_BACKGROUND_DOWNLOAD); // 程序退出后台继续下载
		intentFilter.addAction(BROADCAST_ACTION_IGNORE_UPDATE); // 忽略更新
		intentFilter.addAction(BROADCAST_ACTION_CANCEL_IGNORE); // 取消忽略
		intentFilter.addAction(BROADCAST_ACTION_SINGLE_UPDATE_DONE); // 单个应用的更新请求到了
		intentFilter.addAction(BROADCAST_ACTION_START_ALL_DOWNLOAD); // 下载所有应用
		intentFilter.addAction(BROADCAST_ACTION_CLOUD_RESTORE); // 下载云备份

		IntentFilter packageIntentFilter = new IntentFilter();
		packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
		packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
		packageIntentFilter.addDataScheme("package");

		registerReceiver(mReceiver, intentFilter);
		registerReceiver(mReceiver, packageIntentFilter);
	}

	/**
	 * 初始化下载流量统计
	 */
	private void initGprsTraffic() {
		int tempTraffic = DJMarketUtils.getMaxFlow(this);
		isOnlyWifi = DJMarketUtils.isOnlyWifi(this);
		if (tempTraffic > 0) {
			maxGprsTraffic = tempTraffic * 1024 * 1024;
		}
		SharedPreferences pref = getSharedPreferences(
				AConstDefine.DONGJI_SHAREPREFERENCES, Context.MODE_PRIVATE);
		currentGprsTraffic = pref.getLong(AConstDefine.SHARE_DOWNLOADSIZE, 0);
	}

	/**
	 * 保存下载流量
	 */
	private void saveGprsTraffic() {
		SharedPreferences pref = getSharedPreferences(
				AConstDefine.DONGJI_SHAREPREFERENCES, Context.MODE_PRIVATE);
		SharedPreferences.Editor mEditor = pref.edit();
		mEditor.putLong(AConstDefine.SHARE_DOWNLOADSIZE, currentGprsTraffic);
		mEditor.commit();
	}

	/**
	 * 注销所有广播
	 */
	private void unregisterAllReceiver() {
		if (mReceiver != null) {
			unregisterReceiver(mReceiver);
		}
	}

	/**
	 * 初始化下载数据
	 */
	private void initDownloadListData() {
		db = new DownloadDBHelper(this);
		db.getAllDownloadEntity(downloadList);
		checkDownloadFile();

		checkPrepareDownload();
		// startAllDownload();
	}

	private void checkPrepareDownload() {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity entity = downloadList.get(i);
			if (entity.getStatus() == STATUS_OF_PREPARE
					|| entity.getStatus() == STATUS_OF_PAUSE_ON_EXIT_SYSTEM) {
				Intent intent = new Intent(BROADCAST_ACTION_CHECK_DOWNLOAD);
				sendBroadcast(intent);
				break;
			}
		}
	}

	/**
	 * 容错处理，检查下载完成的文件是否存在
	 */
	private void checkDownloadFile() {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity entity = downloadList.get(i);
			String path = DOWNLOAD_ROOT_PATH + entity.hashCode();
			if (entity.getStatus() == STATUS_OF_COMPLETE) {
				path += DOWNLOAD_FILE_POST_SUFFIX;
				File file = new File(path);
				if (!file.exists()) {
					downloadList.remove(i--);
					db.deleteDownloadEntity(entity);
				}
			} else if (entity.getStatus() == STATUS_OF_DOWNLOADING
					|| entity.getStatus() == STATUS_OF_EXCEPTION) {
				path += DOWNLOAD_FILE_PREPARE_SUFFIX;
				File file = new File(path);
				if (!file.exists()) {
					downloadList.remove(i--);
					db.deleteDownloadEntity(entity);
				}
			}
		}
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String packageStr = intent.getDataString();
			if (BROADCAST_ACTION_ADD_DOWNLOAD.equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						System.out.println(entity.appName + " "
								+ mHandler.hasMessages(EVENT_ADD_DOWNLOAD));
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_ADD_DOWNLOAD;
						msg.obj = entity;
						// synchronized (mHandler) {
						mHandler.sendMessage(msg);
						// }
					}
				}
			} else if (BROADCAST_ACTION_APP_UPDATE_DATADONE.equals(intent
					.getAction())) {
				mHandler.sendEmptyMessage(EVENT_UPDATE_DATA_DONE);
			} else if (BROADCAST_ACTION_PAUSE_DOWNLOAD.equals(intent
					.getAction())) {
				mHandler.sendEmptyMessage(EVENT_DWONLOAD_NEXT);
			} else if (BROADCAST_ACTION_CANCEL_DOWNLOAD.equals(intent
					.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_DOWNLOAD_CANCEL;
						msg.obj = entity;
						mHandler.sendMessage(msg);
					}
				}
			} else if (BROADCAST_ACTION_ONEKEY_UPDATE
					.equals(intent.getAction())) {
				mHandler.sendEmptyMessage(EVENT_ONEKEY_UPDATE);
			} else if (BROADCAST_ACTION_REMOVE_DOWNLOAD.equals(intent
					.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_REMOVE_DOWNLOAD;
						msg.obj = entity;
						mHandler.sendMessage(msg);
					}
				}
			} else if (BROADCAST_ACTION_GPRS_SETTING_CHANGE.equals(intent
					.getAction())) {
				Bundle bundle = intent.getExtras();
				long limitTraffic = bundle.getLong("limitFlow", -1);
				isOnlyWifi = bundle.getBoolean("isOnlyWifi", true);
				if (limitTraffic != -1) {
					currentGprsTraffic = 0;
					maxGprsTraffic = limitTraffic * 1024 * 1024;
				}
				mHandler.sendEmptyMessage(EVENT_CONTINUE_DOWNLOAD);
			} else if (BROADCAST_ACTION_IGNORE_UPDATE
					.equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_IGNORE_UPDATE;
						msg.obj = entity;
						mHandler.sendMessage(msg);
					}
				}
			} else if (BROADCAST_ACTION_CANCEL_IGNORE
					.equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_CANCEL_IGNORE;
						msg.obj = entity;
						mHandler.sendMessage(msg);
					}
				}
			} else if (BROADCAST_ACTION_SINGLE_UPDATE_DONE.equals(intent
					.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					DownloadEntity entity = bundle
							.getParcelable(DOWNLOAD_ENTITY);
					if (entity != null) {
						Message msg = mHandler.obtainMessage();
						msg.what = EVENT_SINGLE_UPDATE_DATA_DONE;
						msg.obj = entity;
						mHandler.sendMessage(msg);
					}
				}
			} else if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
				if (!TextUtils.isEmpty(packageStr)) {
					packageStr = DownloadUtils.parsePackageName(packageStr);
					PackageInfo info = AndroidUtils.getPackageInfo(
							mDownloadService, packageStr);
					if (info != null) {
						DownloadEntity entity = removeDownloadEntity(
								info.packageName, info.versionCode);
						installDownloadEntityDone(entity);
					}
				}
			} else if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
				if (!TextUtils.isEmpty(packageStr)) {
					packageStr = DownloadUtils.parsePackageName(packageStr);
					onAppRemoved(packageStr);
				}
			} else if (BROADCAST_ACTION_START_ALL_DOWNLOAD.equals(intent
					.getAction())) {
				mHandler.sendEmptyMessage(EVENT_START_ALL_DOWNLOAD);
			} else if (BROADCAST_ACTION_CLOUD_RESTORE
					.equals(intent.getAction())) {
				Bundle bundle = intent.getExtras();
				if (bundle != null) {
					ArrayList<ApkItem> items = bundle
							.getParcelableArrayList("cloudList");
					Message msg = mHandler.obtainMessage();
					msg.what = EVENT_CLOUD_RESTORE;
					msg.obj = items;
					mHandler.sendMessage(msg);
				}
			}
		}
	};

	/**
	 * 当应用卸载
	 * 
	 * @param packageName
	 */
	private void onAppRemoved(String packageName) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity entity = downloadList.get(i);
			if (entity.packageName.equals(packageName)
					&& entity.downloadType == TYPE_OF_UPDATE) { // 是否正在更新的应用保留？
				downloadList.remove(i);
				Intent intent = new Intent(BROADCAST_ACTION_REMOVE_COMPLETE);
				Bundle bundle = new Bundle();
				bundle.putParcelable(DOWNLOAD_ENTITY, entity);
				intent.putExtras(bundle);
				sendBroadcast(intent);
				break;
			}
		}
	}

	/**
	 * 当应用下载安装完成后判断是否需要删除安装包及通知界面
	 * 
	 * @param entity
	 */
	private void installDownloadEntityDone(DownloadEntity entity) {
		if (entity != null) {
			Intent intent = new Intent(BROADCAST_ACTION_INSTALL_COMPLETE);
			Bundle bundle = new Bundle();
			bundle.putParcelable(DOWNLOAD_ENTITY, entity);
			intent.putExtras(bundle);
			sendBroadcast(intent);
			boolean isDeleteApkFile = DJMarketUtils
					.isAutoDelPkg(mDownloadService);
			if (isDeleteApkFile) {
				String path = DOWNLOAD_ROOT_PATH + entity.hashCode()
						+ DOWNLOAD_FILE_POST_SUFFIX;
				DownloadUtils.deleteDownloadFile(path);
			}
			if (mHandler != null) {
				Message msg = mHandler.obtainMessage();
				msg.obj = entity;
				msg.what = EVENT_SEND_STATISTICS_INSTALL;
				mHandler.sendMessage(msg);
			}
		}
	}

	/**
	 * 保存下载数据
	 */
	private void saveDownloadInDB() {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity entity = downloadList.get(i);
			if (entity.getStatus() == STATUS_OF_DOWNLOADING) {
				entity.setStatus(STATUS_OF_PAUSE_ON_EXIT_SYSTEM);
			}
			if (entity.downloadType != TYPE_OF_UPDATE
					|| (entity.downloadType == TYPE_OF_UPDATE && entity
							.getStatus() != STATUS_OF_INITIAL)
					|| entity.getStatus() == STATUS_OF_PAUSE_ON_EXIT_SYSTEM
					|| entity.downloadType == TYPE_OF_IGNORE) {
				db.addOrUpdateDownload(entity);
			}
		}
	}

	/**
	 * 一键更新
	 */
	private void onekeyUpdate() {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity entity = downloadList.get(i);
			if (entity.downloadType == TYPE_OF_UPDATE) {
				if (entity.getStatus() != STATUS_OF_DOWNLOADING
						&& entity.getStatus() != STATUS_OF_COMPLETE) {
					entity.setStatus(STATUS_OF_PREPARE); // 否则无法显示进度条
					if (currentDownloadNum < MAX_DOWNLOAD_NUM) {
						entity.setStatus(STATUS_OF_PREPARE);
						startDownloadByEntity(entity);
					}
				}
			}
		}
	}

	/**
	 * 添加应用到下载队列
	 * 
	 * @param entity
	 */
	private void addDownloadToQueue(DownloadEntity entity) {
		if (entity != null) {
			/*
			 * boolean exists=hasDownloadEntity(entity); if(!exists) {
			 * downloadList.add(entity); }
			 */
			// startAllDownload();
			startDownload(entity);
		}
	}

	/**
	 * 判断此应用是否存在于下载队列
	 * 
	 * @param entity
	 * @return
	 */
	private boolean hasDownloadEntity(DownloadEntity entity) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (entity.appId == d.appId && entity.category == d.category) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 是否有正在下载的应用
	 * 
	 * @return
	 */
	public boolean hasDownloading() {
		return currentDownloadNum > 0;
	}

	/**
	 * 开始下载可以下载的应用
	 */
	private void startAllDownload() {
		if (currentDownloadNum < MAX_DOWNLOAD_NUM) {
			for (int i = 0; i < downloadList.size(); i++) {
				final DownloadEntity entity = downloadList.get(i);
				// 当此应用为初始化状态或应用退出后暂停状态时，则需要立即开始下载
				if (entity.getStatus() == STATUS_OF_PREPARE
						|| entity.getStatus() == STATUS_OF_PAUSE_ON_EXIT_SYSTEM) {
					entity.setOnDownloadListener(DownloadService.this);
					currentDownloadNum++;
					new Thread(entity).start();
				}
				if (currentDownloadNum == MAX_DOWNLOAD_NUM) {
					break;
				}
			}
		}
	}

	/**
	 * 开始下载下一个应用
	 */
	private void startNextDownload() {
		System.out.println("startNextDownload " + downloadList.size());
		if (currentDownloadNum < MAX_DOWNLOAD_NUM) {
			for (int i = 0; i < downloadList.size(); i++) {
				DownloadEntity d = downloadList.get(i);

				if (d.getStatus() == STATUS_OF_PREPARE) {
					startDownloadByEntity(d);
				} else if (d.getStatus() == STATUS_OF_PAUSE_ON_TRAFFIC_LIMIT) { // 当此应用是因为流量达到限制而暂停下载则修改流量限制后需要继续下载
					d.setStatus(STATUS_OF_PREPARE);
					startDownloadByEntity(d);
				}
				if (currentDownloadNum == MAX_DOWNLOAD_NUM) {
					break;
				}
			}
		}
	}

	/**
	 * 继续下载因流量限制而暂停的应用
	 */
	private void startTrafficLimitDownload() {
		if (currentGprsTraffic < maxGprsTraffic) {
			for (int i = 0; i < downloadList.size(); i++) {
				DownloadEntity d = downloadList.get(i);
				if (d.getStatus() == STATUS_OF_PAUSE_ON_TRAFFIC_LIMIT) { // 当此应用是因为流量达到限制而暂停下载则修改流量限制后需要继续下载
					d.setStatus(STATUS_OF_PREPARE);
					startDownloadByEntity(d);
				}
				if (currentDownloadNum == MAX_DOWNLOAD_NUM) {
					break;
				}
			}
			startNextDownload();
		}
	}

	private synchronized void startDownloadByEntity(DownloadEntity entity) {
		// System.out.println("======"+entity.getStatus()+", "+entity.appName);
		// synchronized (this) {
		// if (entity.getStatus() == STATUS_OF_PREPARE &&
		// entity.getStatus()!=STATUS_OF_DOWNLOADING) {
		if (entity.canDownload()) {
			entity.setOnDownloadListener(this);
			currentDownloadNum++;
			System.out.println("====================currentDownloadNum:"
					+ currentDownloadNum);
			entity.setStatus(STATUS_OF_DOWNLOADING);
			new Thread(entity).start();
			// mHandler.post(entity);
		}
		// }
	}

	/**
	 * 开始单个下载
	 * 
	 * @param entity
	 */
	private void startDownload(DownloadEntity entity) {
		int i = 0;
		boolean hasEntity = false; // 用来判断是否该应用是否已存在于下载列表
		for (; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			synchronized (d) {
				if (entity.appId == d.appId && entity.category == d.category) {
					hasEntity = true;
					if (entity.getStatus() == STATUS_OF_PREPARE) {
						d.setStatus(STATUS_OF_PREPARE);
						if (currentDownloadNum < MAX_DOWNLOAD_NUM) {
							startDownloadByEntity(d);
							break;
						}
					}
				}
				// if (currentDownloadNum >= MAX_DOWNLOAD_NUM) {
				// System.out.println("add entity "+entity.appName+", "+entity.getStatus()+", "+entity.downloadType);
				// downloadList.add(entity);
				// int lastIndex=downloadList.size()-1;
				// downloadList.get(lastIndex).downloadType = TYPE_OF_DOWNLOAD;
				// break;
				// }
			}
		}
		if (i == downloadList.size() && !hasEntity) { // downloadList.size() > 0
														// &&
			System.out.println("add entity " + entity.appName + ", "
					+ entity.getStatus() + ", " + entity.downloadType + ", "
					+ downloadList.size());
			downloadList.add(entity);
			int lastIndex = downloadList.size() - 1;
			downloadList.get(lastIndex).downloadType = TYPE_OF_DOWNLOAD;
			if (currentDownloadNum < MAX_DOWNLOAD_NUM
					&& entity.getStatus() == STATUS_OF_PREPARE) {
				startDownloadByEntity(downloadList.get(lastIndex));
			} else {
				downloadList.get(lastIndex).setStatus(STATUS_OF_PREPARE);
			}
		}

		// 判断如果是新开始下载的应用则需要提示
		// DownloadUtils.fillUpdateAndUpdatingNotifycation(this, downloadList);
	}

	/**
	 * 取消下载操作
	 * 
	 * @param entity
	 */
	private void cancelDownload(DownloadEntity entity) {
		/*
		 * if (entity.downloadType == TYPE_OF_DOWNLOAD) { for(int
		 * i=0;i<downloadList.size();i++) { DownloadEntity
		 * d=downloadList.get(i); if(d.equals(entity)) { downloadList.remove(i);
		 * boolean flag=DownloadUtils.deleteDownloadFile(DOWNLOAD_ROOT_PATH +
		 * d.hashCode() + DOWNLOAD_FILE_PREPARE_SUFFIX);
		 * System.out.println(entity.appName+" delete file "+flag);
		 * startNextDownload(); break; } } } else if (entity.downloadType ==
		 * TYPE_OF_UPDATE) { DownloadUtils.deleteDownloadFile(DOWNLOAD_ROOT_PATH
		 * + entity.hashCode() + DOWNLOAD_FILE_PREPARE_SUFFIX);
		 * startNextDownload(); } db.deleteDownloadEntity(entity);
		 */
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (d.packageName.equals(entity.packageName)
					&& d.versionCode == entity.versionCode) {
				if (d.downloadType == TYPE_OF_DOWNLOAD) {
					d.setStatus(STATUS_OF_PAUSE);
					downloadList.remove(i);
					boolean flag = DownloadUtils
							.deleteDownloadFile(DOWNLOAD_ROOT_PATH
									+ d.hashCode()
									+ DOWNLOAD_FILE_PREPARE_SUFFIX);
					db.deleteDownloadEntity(d);
					if (mDownloadStatusListener != null) {
						mDownloadStatusListener.onRemoveDownload(d);
					}
					// startNextDownload();
				} else if (d.downloadType == TYPE_OF_UPDATE) {
					d.reset();
					boolean flag = DownloadUtils
							.deleteDownloadFile(DOWNLOAD_ROOT_PATH
									+ d.hashCode()
									+ DOWNLOAD_FILE_PREPARE_SUFFIX);
					// startNextDownload();
					db.deleteDownloadEntity(d);
				}
				break;
			}
		}

	}

	/**
	 * 当应用更新的数据请求到了
	 */
	private void updateDataDone() {
		ArrayList<ApkItem> list = mApp.getUpdateList();
		List<DownloadEntity> tempList = new ArrayList<DownloadEntity>();
		if (list != null && list.size() > 0) {
			for (int i = 0; i < list.size(); i++) {
				ApkItem item = list.get(i);
				int j = 0;
				for (; j < downloadList.size(); j++) {
					DownloadEntity entity = downloadList.get(j);
					// if(entity.downloadType==TYPE_OF_DOWNLOAD ||
					// entity.downloadType==TYPE_OF_COMPLETE) {
					if (entity.appId == item.appId
							&& entity.category == item.category) {
						break;
					}
					// }
				}
				if (j == downloadList.size()) {
					DownloadEntity d = new DownloadEntity(item);
					d.downloadType = TYPE_OF_UPDATE;
					DownloadUtils.setInstallDownloadEntity(this, d);
					tempList.add(d);
				}
			}
		}
		boolean isAutoUpdate = DJMarketUtils.isAutoUpdate(this);
		if (isAutoUpdate) {
			autoUpdate(tempList);
		}

		downloadList.addAll(tempList);

		if (mDownloadStatusListener != null) {
			mDownloadStatusListener.onUpdateListDone(tempList);
		}
		Intent intent = new Intent(BROADCAST_ACTION_UPDATE_DATA_MERGE_DONE);
		sendBroadcast(intent);

		DownloadUtils.fillUpdateNotifycation(this, downloadList); // 显示标题栏可更新数目
	}

	private void autoUpdate(List<DownloadEntity> updateList) {
		for (int i = 0; i < updateList.size(); i++) {
			final DownloadEntity entity = updateList.get(i);
			entity.setStatus(STATUS_OF_PREPARE);
			if (currentDownloadNum < MAX_DOWNLOAD_NUM) {
				if (entity.getStatus() == STATUS_OF_PREPARE
						|| entity.getStatus() == STATUS_OF_PAUSE_ON_EXIT_SYSTEM) {
					entity.setOnDownloadListener(DownloadService.this);
					currentDownloadNum++;
					new Thread(entity).start();
				}
			}
		}
	}

	/**
	 * 获取下载队列
	 * 
	 * @return
	 */
	public List<DownloadEntity> getAllDownloadList() {
		return downloadList;
	}

	@Override
	public void onDownloadStatusChanged(DownloadEntity entity) {
		System.out.println(entity.appName + " onDownloadStatusChanged status:"
				+ entity.getStatus());

		// synchronized (obj) {
		switch (entity.getStatus()) {
		case STATUS_OF_COMPLETE:
			Message msg = mHandler.obtainMessage();
			msg.what = EVENT_DOWNLOAD_COMPLETE;
			msg.obj = entity;
			mHandler.sendMessage(msg);
			System.out.println(entity.appName + " download complete");
			break;
		case STATUS_OF_EXCEPTION:
			mHandler.sendEmptyMessage(EVENT_DWONLOAD_NEXT);
			System.out.println(entity.appName + " download error");
			break;
		case STATUS_OF_PAUSE:
			mHandler.sendEmptyMessage(EVENT_DWONLOAD_NEXT);
			break;
		case STATUS_OF_PAUSE_ON_TRAFFIC_LIMIT:
			currentDownloadNum--;
			System.out
					.println("STATUS_OF_PAUSE_ON_TRAFFIC_LIMIT currentDownloadNum:"
							+ currentDownloadNum);
			break;
		case STATUS_OF_INITIAL:
			mHandler.sendEmptyMessage(EVENT_DWONLOAD_NEXT);
			break;
		}
		// }
	}

	private class MyHandler extends Handler {
		MyHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			DownloadEntity entity = null;
			switch (msg.what) {
			case EVENT_QUERY_DOWNLOAD:
				initGprsTraffic();
				initDownloadListData();
				break;
			case EVENT_ADD_DOWNLOAD:
				entity = (DownloadEntity) msg.obj;
				addDownloadToQueue(entity);
				break;
			case EVENT_UPDATE_DATA_DONE:
				updateDataDone();
				break;
			case EVENT_DWONLOAD_NEXT:
				currentDownloadNum--;
				System.out.println("EVENT_DWONLOAD_NEXT currentDownloadNum:"
						+ currentDownloadNum + ", ");
				startNextDownload();
				break;
			case EVENT_DOWNLOAD_CANCEL:
				entity = (DownloadEntity) msg.obj;
				// currentDownloadNum--;
				System.out.println("EVENT_DOWNLOAD_CANCEL currentDownloadNum:"
						+ currentDownloadNum);
				cancelDownload(entity);
				break;
			case EVENT_ONEKEY_UPDATE:
				onekeyUpdate();
				break;
			case EVENT_DOWNLOAD_COMPLETE:
				entity = (DownloadEntity) msg.obj;
				checkDownloadCompleteApk(entity);
				break;
			case EVENT_REMOVE_DOWNLOAD:
				entity = (DownloadEntity) msg.obj;
				removeDownloadEntity(entity);
				break;
			case EVENT_CONTINUE_DOWNLOAD:
				startTrafficLimitDownload();
				break;
			case EVENT_IGNORE_UPDATE:
				entity = (DownloadEntity) msg.obj;
				ignoreUpdateEntity(entity);
				break;
			case EVENT_CANCEL_IGNORE:
				entity = (DownloadEntity) msg.obj;
				cancelIgnore(entity);
				break;
			case EVENT_SINGLE_UPDATE_DATA_DONE:
				entity = (DownloadEntity) msg.obj;
				singleUpdateDataDone(entity);
				break;
			case EVENT_START_ALL_DOWNLOAD:
				startAllDownload();
				break;
			case EVENT_CLOUD_RESTORE:
				ArrayList<ApkItem> items = (ArrayList<ApkItem>) msg.obj;
				cloudRestore(items);
				break;
			case EVENT_SEND_STATISTICS_INSTALL:
				entity = (DownloadEntity) msg.obj;
				sendStatisticsInstall(entity);
				break;
			}
		}
	}

	/**
	 * 安装统计
	 * 
	 * @param entity
	 */
	private void sendStatisticsInstall(DownloadEntity entity) {
		System.out.println("send "
				+ DataManager.newInstance().statisticsForInstall(entity.appId,
						entity.category));
	}

	/**
	 * 下载云恢复
	 */
	private void cloudRestore(ArrayList<ApkItem> items) {
		for (int j = 0; j < items.size(); j++) {
			ApkItem item = items.get(j);
			int i = 0;
			for (; i < downloadList.size(); i++) {
				DownloadEntity entity = downloadList.get(i);
				if (entity.packageName.equals(item.packageName)
						&& entity.versionCode == item.versionCode) {
					break;
				}
			}
			if (i == downloadList.size()) {
				DownloadEntity d = new DownloadEntity(item);
				d.setStatus(STATUS_OF_PREPARE);
				startDownload(d);
				Intent intent = new Intent(BROADCAST_ACTION_ADD_DOWNLOAD_LIST);
				Bundle bundle = new Bundle();
				bundle.putParcelable(DOWNLOAD_ENTITY, d);
				intent.putExtras(bundle);
				sendBroadcast(intent);
			}
		}
	}

	/**
	 * 单个应用的更新数据处理
	 * 
	 * @param entity
	 */
	private void singleUpdateDataDone(DownloadEntity entity) {
		DownloadUtils.setInstallDownloadEntity(this, entity);
		entity.downloadType = TYPE_OF_UPDATE;
		downloadList.add(entity);
		Intent intent = new Intent(BROADCAST_ACTION_ADD_UPDATE);
		Bundle bundle = new Bundle();
		bundle.putParcelable(DOWNLOAD_ENTITY, entity);
		intent.putExtras(bundle);
		sendBroadcast(intent);
	}

	/**
	 * 取消忽略
	 * 
	 * @param entity
	 */
	private void cancelIgnore(DownloadEntity entity) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (d.packageName.equals(entity.packageName)
					&& d.versionCode == entity.versionCode) {
				downloadList.remove(i);
				db.deleteDownloadEntity(d);

				Intent intent = new Intent(
						BROADCAST_ACTION_REQUEST_SINGLE_UPDATE);
				Bundle bundle = new Bundle();
				bundle.putParcelable(DOWNLOAD_ENTITY, entity);
				intent.putExtras(bundle);
				sendBroadcast(intent);
				break;
			}
		}
	}

	/**
	 * 忽略更新
	 * 
	 * @param entity
	 */
	private void ignoreUpdateEntity(DownloadEntity entity) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (d.packageName.equals(entity.packageName)
					&& d.versionCode == entity.versionCode) {
				d.reset();
				break;
			}
		}
	}

	/**
	 * 处理下载完成后的操作
	 * 
	 * @param entity
	 */
	private void checkDownloadCompleteApk(DownloadEntity entity) {
		String filePath = DOWNLOAD_ROOT_PATH + entity.hashCode()
				+ DOWNLOAD_FILE_POST_SUFFIX;
		// 验证此 apk 文件是否正确
		boolean flag = DownloadUtils.checkApkFile(filePath);

		System.out
				.println(entity.appName + " checkDownloadCompleteApk " + flag);

		if (flag) {
			entity.downloadType = TYPE_OF_COMPLETE;
			Intent intent = new Intent(BROADCAST_ACTION_COMPLETE_DOWNLOAD);
			Bundle bundle = new Bundle();
			bundle.putParcelable(DOWNLOAD_ENTITY, entity);
			intent.putExtras(bundle);
			sendBroadcast(intent);

			installApp(entity);

			DownloadUtils.fillAll(this);
		} else {
			entity.setStatus(STATUS_OF_PREPARE);
			DownloadUtils.deleteDownloadFile(filePath);
		}
		mHandler.sendEmptyMessage(EVENT_DWONLOAD_NEXT);
	}

	/**
	 * 安装 Apk
	 * 
	 * @param entity
	 */
	private void installApp(final DownloadEntity entity) {
		final String path = DOWNLOAD_ROOT_PATH + entity.hashCode()
				+ DOWNLOAD_FILE_POST_SUFFIX;
		if (DJMarketUtils.isDefaultInstall(this)) {
			if (AndroidUtils.isRoot()) {
				// System.out.println("root install ......");
				DownloadAdapter.rootApkList.add(entity.packageName);
				new Thread(new Runnable() {

					@Override
					public void run() {
						boolean succeed = AndroidUtils.rootInstallApp(path);
						// System.out.println("root installed state ====> " +
						// succeed);
						if (!succeed) {
							// DownloadUtils.installApk(this, path);
							for (int i = 0; i < DownloadAdapter.rootApkList
									.size(); i++) {
								if (DownloadAdapter.rootApkList.get(i).equals(
										entity.packageName)) {
									DownloadAdapter.rootApkList
											.remove(entity.packageName);
									break;
								}
							}
							Intent intent = new Intent();
							intent.setAction(BROADCAST_ACTION_UPDATE_ROOTSTATUS);
							intent.putExtra(DOWNLOAD_APKPACKAGENAME,
									entity.packageName);
							sendBroadcast(intent);
						}
					}
				}).start();

			} else {
				DownloadUtils.installApk(this, path);
			}
		} else {
			DownloadUtils.installApk(this, path);
		}
	}

	/**
	 * 根据指定的下载对象在下载队列中移除
	 * 
	 * @param entity
	 * @return
	 */
	private boolean removeDownloadEntity(DownloadEntity entity) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (d.appId == entity.appId && d.category == entity.category) {
				ArrayList<ApkItem> updateList = mApp.getUpdateList();
				if (updateList != null) {
					int j = 0;
					for (; j < updateList.size(); j++) {
						ApkItem item = updateList.get(j);
						if (item.packageName.equals(d.packageName)
								&& item.versionCode == d.versionCode) {
							d.downloadType = TYPE_OF_UPDATE;
							boolean flag = d.reset(); // 判断是否已经重置，防止在下载页面已安装栏进行重复点击删除
							if (flag) {
								Intent intent = new Intent(
										BROADCAST_ACTION_ADD_UPDATE);
								Bundle bundle = new Bundle();
								bundle.putParcelable(DOWNLOAD_ENTITY, d);
								intent.putExtras(bundle);
								sendBroadcast(intent);
							}
							db.deleteDownloadEntity(d);
							return true;
						}
					}
					if (j == updateList.size()) {
						System.out.println("removeDownloadEntity "
								+ entity.appName);
						db.deleteDownloadEntity(d);
						return downloadList.remove(i) != null;
					}
				} else {
					db.deleteDownloadEntity(d);
					return downloadList.remove(i) != null;
				}
			}
		}
		return false;
	}

	/**
	 * 是否可以继续使用 GPRS 下载
	 */
	public boolean canUseGprsDownload() {
		if (currentGprsTraffic >= maxGprsTraffic) {
			return false;
		}
		return true;
	}

	/**
	 * 添加手机下载 GPRS 流量
	 * 
	 * @return
	 */
	boolean addGprsTraffic(int traffic) {
		currentGprsTraffic += traffic;
		boolean flag = canUseGprsDownload();
		if (!flag) {
			Intent intent = new Intent(AConstDefine.BROADCAST_ACTION_NOFLOW);
			sendBroadcast(intent);
		}
		return flag;
	}

	/**
	 * 获取已经使用的 GPRS 流量
	 * 
	 * @return
	 */
	public long getAlreadyUseGprsTraffic() {
		return currentGprsTraffic;
	}

	/**
	 * 设置后台下载
	 * 
	 * @param isBack
	 */
	public void setBackgroundDownload(boolean isBack) {
		this.isBackgroundDownload = isBack;
	}

	/**
	 * 移除下载队列中对应的下载对象
	 * 
	 * @param packageName
	 * @param versionCode
	 * @return
	 */
	private DownloadEntity removeDownloadEntity(String packageName,
			int versionCode) {
		for (int i = 0; i < downloadList.size(); i++) {
			DownloadEntity d = downloadList.get(i);
			if (d.packageName.equals(packageName)
					&& d.versionCode == versionCode) {
				return downloadList.remove(i);
			}
		}
		return null;
	}

	/**
	 * 添加下载监听
	 * 
	 * @param listener
	 */
	public static void setDownloadStatusListener(DownloadStatusListener listener) {
		mDownloadStatusListener = listener;
	}

	public interface DownloadStatusListener {
		void onDownloadChanged(DownloadEntity entity);

		void onUpdateListDone(List<DownloadEntity> list);

		void onRemoveDownload(DownloadEntity entity);
	}
}