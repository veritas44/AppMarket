package com.dongji.market.activity;

import java.io.IOException;

import org.myjson.JSONException;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.dongji.market.R;
import com.dongji.market.download.AConstDefine;
import com.dongji.market.helper.AndroidUtils;
import com.dongji.market.helper.TitleUtil;
import com.dongji.market.protocol.DataManager;
import com.dongji.market.receiver.CommonReceiver;
import com.umeng.analytics.MobclickAgent;

public class FeedbackActivity extends Activity {

	private TitleUtil titleUtil;
	private View mTopView;
	private EditText mContentET, mContactET;
	private Button mSubmitBtn;
	private ImageView mTopLogoIV;
	private InputMethodManager imm;

	private MyHandler mHandler;
	private CommonReceiver receiver;

	private int responseStatus;
	private boolean isEmpty;
	private static final int EVENT_SEND_FEEDBACK = 0;
	private static final int CHECK_RESPONSE_TIMEOUT = 1;
	private static final int EVENT_SEND_PROMPT = 2;
	private static final int EVENT_NET_ERROR_PROMPT = 3;
	private static final int EVENT_CONTENT_EMPTY_PROMPT = 4;
	private static final int EVENT_EXIT_PAGE = 5;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_feedback);
		overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left);
		// mTopView = findViewById(R.id.login_top);
		// titleUtil = new TitleUtil(this, mTopView, R.string.feedback, null);

		initView();
	}

	private void initView() {
		mTopLogoIV = (ImageView) findViewById(R.id.top_logo);
		mContentET = (EditText) findViewById(R.id.content_et);
		mContactET = (EditText) findViewById(R.id.contact_et);
		mSubmitBtn = (Button) findViewById(R.id.submit_btn);

		// mContactET.setOnFocusChangeListener(new OnFocusChangeListener() {
		//
		// @Override
		// public void onFocusChange(View v, final boolean hasFocus) {
		// (new Handler()).postDelayed(new Runnable() {
		//
		// @Override
		// public void run() {
		// imm = (InputMethodManager)
		// getSystemService(Context.INPUT_METHOD_SERVICE);
		// if (hasFocus) {
		// // imm.toggleSoftInput(0,
		// // InputMethodManager.HIDE_NOT_ALWAYS);
		// imm.showSoftInput(mContactET,
		// InputMethodManager.SHOW_IMPLICIT);
		// }
		// }
		// }, 100);
		// }
		// });

		mContactET.setOnEditorActionListener(new OnEditorActionListener() {

			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					judgeIfSend();
				}
				return false;
			}
		});

		mSubmitBtn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				judgeIfSend();
			}
		});

		mTopLogoIV.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				sendBroadcast(new Intent(AConstDefine.GO_HOME_BROADCAST));
			}
		});

		initHandler();
		
		receiver = new CommonReceiver();
		registerReceiver(receiver, new IntentFilter(AConstDefine.GO_HOME_BROADCAST));
	}

	private void judgeIfSend() {
		if (TextUtils.isEmpty(mContentET.getText().toString())) {
			mHandler.sendEmptyMessage(EVENT_CONTENT_EMPTY_PROMPT);
		} else if (!AndroidUtils.isNetworkAvailable(FeedbackActivity.this)) {
			mHandler.sendEmptyMessage(EVENT_NET_ERROR_PROMPT);
		} else {
			mHandler.sendEmptyMessage(EVENT_SEND_FEEDBACK);
		}
	}

	// 提交反馈内容
	private void commitContent() {
		// Toast.makeText(this, "提交反馈", Toast.LENGTH_SHORT).show();
		responseStatus = AConstDefine.THRESHOLD;
		String contentStr = mContentET.getText().toString();
		String contactStr = mContactET.getText().toString();
		try {
			String appName = getPackageManager().getApplicationLabel(
					getApplicationInfo()).toString(); // 应用名称
			String appVersion = getPackageManager().getPackageInfo(
					getPackageName(), 0).versionName; // 应用版本
			String deviceModel = Build.MODEL; // 设备型号
			String sdkVersion = Build.VERSION.SDK; // 设备SDK版本
			String sysVersion = Build.VERSION.RELEASE; // 系统版本
//			System.out.println("content: " + contentStr + "\n" + "contact: "
//					+ contactStr + "\n" + "appName: " + appName + "\n"
//					+ "appVersion: " + appVersion + "\n" + "deviceModel: "
//					+ deviceModel + "\n" + "sdkVersion: " + sdkVersion + "\n"
//					+ "sysVersion: " + sysVersion);
			responseStatus = DataManager.newInstance()
					.feedback(appName, appVersion, deviceModel, sysVersion,
							contactStr, contentStr);
			// feedbackPrompt(responseStatus);
			mHandler.sendEmptyMessage(EVENT_SEND_PROMPT);
		} catch (NameNotFoundException e) {
			mHandler.sendEmptyMessage(CHECK_RESPONSE_TIMEOUT);
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void feedbackPrompt(int status) {
		switch (status) {
		case 0:
			AndroidUtils.showToast(getApplicationContext(),
					R.string.feedback_failed);
			break;
		case 1:
			AndroidUtils.showToast(getApplicationContext(),
					R.string.feedback_success);
			mHandler.sendEmptyMessageDelayed(EVENT_EXIT_PAGE, 500);
			break;
		default:
			break;
		}
	}

//	@Override
//	public void onBackPressed() {
//		// TODO Auto-generated method stub
//		super.onBackPressed();
//		overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
//	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		MobclickAgent.onResume(this);
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		super.onPause();
		if (isFinishing()) {
			overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
		}
		MobclickAgent.onPause(this);
	}

	// @Override
	// public boolean onCreateOptionsMenu(Menu menu) {
	// // TODO Auto-generated method stub
	// menu.add("test");
	// return super.onCreateOptionsMenu(menu);
	// }

	// @Override
	// public boolean onMenuOpened(int featureId, Menu menu) {
	// titleUtil.showOrDismissSettingPopupWindow();
	// return false;
	// }

	@Override
	protected void onDestroy() {
		super.onDestroy();
		removeMessage();
		unregisterReceiver(receiver);
	}

	private void removeMessage() {
		if (mHandler != null) {
			if (mHandler.hasMessages(EVENT_SEND_FEEDBACK)) {
				mHandler.removeMessages(EVENT_SEND_FEEDBACK);
			}
			if (mHandler.hasMessages(CHECK_RESPONSE_TIMEOUT)) {
				mHandler.removeMessages(CHECK_RESPONSE_TIMEOUT);
			}
			if (mHandler.hasMessages(EVENT_SEND_PROMPT)) {
				mHandler.removeMessages(EVENT_SEND_PROMPT);
			}
			if (mHandler.hasMessages(EVENT_CONTENT_EMPTY_PROMPT)) {
				mHandler.removeMessages(EVENT_CONTENT_EMPTY_PROMPT);
			}
		}
	}

	private void initHandler() {
		HandlerThread thread = new HandlerThread("handler");
		thread.start();
		mHandler = new MyHandler(thread.getLooper());
	}

	class MyHandler extends Handler {

		public MyHandler(Looper looper) {
			super(looper);
			// TODO Auto-generated constructor stub
		}

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			switch (msg.what) {
			case EVENT_SEND_FEEDBACK:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						mSubmitBtn.setClickable(false);
						AndroidUtils.showToast(FeedbackActivity.this,
								R.string.is_committing);
						mHandler.sendEmptyMessageDelayed(
								CHECK_RESPONSE_TIMEOUT, 10000);
					}
				});
				commitContent();
				break;
			case CHECK_RESPONSE_TIMEOUT:
				if (responseStatus == AConstDefine.THRESHOLD) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							AndroidUtils.showToast(FeedbackActivity.this,
									R.string.net_conn_timeout);
							mSubmitBtn.setClickable(true);
						}
					});
				}
				break;
			case EVENT_SEND_PROMPT:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						feedbackPrompt(responseStatus);
						mSubmitBtn.setClickable(true);
					}
				});
				break;
			case EVENT_NET_ERROR_PROMPT:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						AndroidUtils.showToast(FeedbackActivity.this,
								R.string.net_error);
						mSubmitBtn.setClickable(true);
					}
				});
				break;
			case EVENT_CONTENT_EMPTY_PROMPT:
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						// TODO Auto-generated method stub
						AndroidUtils.showToast(FeedbackActivity.this,
								R.string.content_empty);
						mSubmitBtn.setClickable(true);
					}
				});
				break;
			case EVENT_EXIT_PAGE:
				finish();
				break;
			default:
				break;
			}
		}

	}

}