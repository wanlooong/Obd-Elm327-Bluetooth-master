/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.obdelm327;

import java.util.ArrayList;
import java.util.List;

import com.obdelm327.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 这是显示当前聊天会话的主要活动。
 */
public class OBDActivity extends Activity {
    private boolean gps = false, gpsreq = true, gps_enabled = false;
    boolean getname = false, getprotocol = false, commandmode = false, getecho = false, initialized = false, setprotocol = false, fuelc = true;
    String devicename, deviceprotocol, tmpmsg, gzm;
    private int rpmval = 0, currenttemp = 0, Enginedisplacement = 1500, Enginetype = 0, FaceColor = 0;
    private ArrayList<Double> avgconsumption;

    private static LocationManager locationManager = null;
    private LocationListener locationListener = null;
    // 调试
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;
    private static boolean actionbar = true;
    private static final String[] PIDS = {
            "01", "02", "03", "04", "05", "06", "07", "08",
            "09", "0A", "0B", "0C", "0D", "0E", "0F", "10",
            "11", "12", "13", "14", "15", "16", "17", "18",
            "19", "1A", "1B", "1C", "1D", "1E", "1F", "20"};
    // 来自蓝牙聊天服务处理程序的消息类型
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // 来自蓝牙聊天服务处理程序的关键名称
    public static final String DEVICE_NAME = "device_nametoas";
    public static final String TOAST = "toast";

    // 意图请求代码
    private static final int REQUEST_CONNECT_DEVICE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // 布局视图
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;
    String currentMessage;
    // 连接设备的名称
    private String mConnectedDeviceName = null;
    // 对话线程的数组适配器
    private ArrayAdapter<String> mConversationArrayAdapter;
    // 用于传出消息的字符串缓冲区
    private StringBuffer mOutStringBuffer;
    // 本地蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter = null;
    // 聊天服务的成员对象
    private BluetoothService mChatService = null;
    private TextView Load, Temp, Status,
            speed, rpm, TA, ITemp, MAF, TP, IMAP,
            Loadtext, Temptext,
            speedtext, rpmtext, TAtext, ITemptext, MAFtext, TPtext, IMAPtext,
            Centertext, gzmdm, gzmgs, gzmgstext;

    /***
     * 指令定义
     ***/
    String[] commands;
    String[] initializeCommands;
    int whichCommand = 0;

    private PowerManager.WakeLock wl;

    @Override

    /***创建时的回调函数***/
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
        wl.acquire();
        setContentView(R.layout.activity_main);
        PackageManager PM = getPackageManager();
        gps = PM.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        Status = (TextView) findViewById(R.id.Status);
        Load = (TextView) findViewById(R.id.Load);
        Temp = (TextView) findViewById(R.id.Temp);
        speed = (TextView) findViewById(R.id.GaugeSpeed);
        rpm = (TextView) findViewById(R.id.GaugeRpm);
        TA = (TextView) findViewById(R.id.TA);
        ITemp = (TextView) findViewById(R.id.ITemp);
        MAF = (TextView) findViewById(R.id.MAF);
        TP = (TextView) findViewById(R.id.TP);
        IMAP = (TextView) findViewById(R.id.IMAP);
        Loadtext = (TextView) findViewById(R.id.Load_text);
        Temptext = (TextView) findViewById(R.id.Temp_text);
        speedtext = (TextView) findViewById(R.id.GaugeSpeed_text);
        rpmtext = (TextView) findViewById(R.id.GaugeRpm_text);
        TAtext = (TextView) findViewById(R.id.TA_text);
        ITemptext = (TextView) findViewById(R.id.ITemp_text);
        MAFtext = (TextView) findViewById(R.id.MAF_text);
        TPtext = (TextView) findViewById(R.id.TP_text);
        IMAPtext = (TextView) findViewById(R.id.IMAP_text);

        Centertext = (TextView) findViewById(R.id.Center_text);
        gzmdm = (TextView) findViewById(R.id.gzmdm);
        gzmgs = (TextView) findViewById(R.id.gzmgs);
        gzmgstext = (TextView) findViewById(R.id.gzmgstext);


        /***指令初始化***/
        avgconsumption = new ArrayList<Double>();
        commands = new String[]{"ATRV", "010C", "0104", "0105", "010D", "010B", "010E", "010F", "0110", "0111", "03"};
        initializeCommands = new String[]{"ATDP", "ATS0", "ATL0", "ATAT0", "ATST10", "ATSPA0", "ATE0"};
        whichCommand = 0;

        RelativeLayout rlayout = (RelativeLayout) findViewById(R.id.mainscreen);
        rlayout.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (actionbar) {
                    getActionBar().hide();
                    actionbar = false;
                } else {
                    getActionBar().show();
                    actionbar = true;
                }
            }
        });
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (mChatService != null) {
            if (mChatService.getState() == BluetoothService.STATE_NONE) {
                mChatService.start();
            }
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mChatService == null) setupChat();
        }
        if (gps) {
            locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
            try {
                gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            } catch (Exception ex) {
            }
            if (gps_enabled) {
                initGps();
            }
        }
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    Thread.sleep(1500);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        speed.setText("0");
//                        rpm.setText("0");
//                    }
//                });
//            }
//        }).start();

    }

    private void setDefaultOrientation() {
        try {
            settextsixe();
        } catch (Exception e) {
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setDefaultOrientation();
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        setDefaultOrientation();
        hideVirturalKeyboard();
        SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(getBaseContext());
        FaceColor = Integer.parseInt(preferences.getString("FaceColor", "0"));
        Enginedisplacement = Integer.parseInt(preferences.getString("Enginedisplacement", "1500"));
        Enginetype = Integer.parseInt(preferences.getString("EngineType", "0"));
        Enginedisplacement = Enginedisplacement / 1500;

    }

    GpsStatus.Listener gpsStatusListener = null;

    @SuppressLint("NewApi")
    protected void initGps() {
        Criteria myCriteria = new Criteria();
        myCriteria.setAccuracy(Criteria.ACCURACY_FINE);
        locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                try {
                    float gpsspeed = location.getSpeed() * 3600 / 1000;
                    CharSequence sp = String.valueOf(gpsspeed);
                    speed.setText(sp);
                } catch (Exception e) {
                }
            }

            @Override
            public void onProviderDisabled(String provider) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onProviderEnabled(String provider) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // TODO Auto-generated method stub
            }
        };
        locationManager.requestLocationUpdates(0L, // 最小时间
                0.0f, // 最小距离
                myCriteria, // 标准
                locationListener, // 侦听器
                null); // 线程
        gpsStatusListener = new GpsStatus.Listener() {
            @Override
            public void onGpsStatusChanged(int event) {
                if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                    GpsStatus status = locationManager.getGpsStatus(null);
                    if (status != null) {
                        Iterable<GpsSatellite> sats = status.getSatellites();
                        int i = 0;
                        for (GpsSatellite sat : sats) {
                            i++;
                            if (!tryconnect) {
                                if (sat.usedInFix())
                                    Status.setText("Gps Active");
                                else
                                    Status.setText("Checking Gps - Sats: " + String.valueOf(i));
                            }
                        }
                    }
                }
            }
        };
        locationManager.addGpsStatusListener(gpsStatusListener);
    }

    private void setupChat() {
        Log.d(TAG, "setupC+hat()");

        // 初始化对话线程的阵列适配器
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // 为返回键使用侦听器初始化组合字段
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // 用一个监听器初始化send按钮，用于单击事件
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // 使用编辑文本小部件的内容发送消息
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
                //view.setText(message);
        }
        });
        // 初始化蓝牙聊天服务来执行蓝牙连接
        mChatService = new BluetoothService(this, mHandler);
        // 初始化传出消息的缓冲区
        mOutStringBuffer = new StringBuffer("");
        invisiblecmd();
    }

    public void resetvalues() {
        speed.setText("0 km/h");
        rpm.setText("0 Rpm");
        Load.setText("0 %");
        Temp.setText("0 °C");
        TA.setText("0 °");
        ITemp.setText("0 °C");
        MAF.setText("0 g/s");
        TP.setText("0 %");
        IMAP.setText("0 Kpa");
        gzmgs.setText("0");
        avgconsumption.clear();
        initialized = false;

    }

    public void invisiblecmd() {
        mConversationView.setVisibility(View.INVISIBLE);
        mOutEditText.setVisibility(View.INVISIBLE);
        mSendButton.setVisibility(View.INVISIBLE);
        rpm.setVisibility(View.VISIBLE);
        speed.setVisibility(View.VISIBLE);
        Load.setVisibility(View.VISIBLE);
        Temp.setVisibility(View.VISIBLE);
        TA.setVisibility(View.VISIBLE);
        ITemp.setVisibility(View.VISIBLE);
        MAF.setVisibility(View.VISIBLE);
        TP.setVisibility(View.VISIBLE);
        IMAP.setVisibility(View.VISIBLE);
        Loadtext.setVisibility(View.VISIBLE);
        Temptext.setVisibility(View.VISIBLE);
        speedtext.setVisibility(View.VISIBLE);
        rpmtext.setVisibility(View.VISIBLE);
        TAtext.setVisibility(View.VISIBLE);
        ITemptext.setVisibility(View.VISIBLE);
        MAFtext.setVisibility(View.VISIBLE);
        TPtext.setVisibility(View.VISIBLE);
        IMAPtext.setVisibility(View.VISIBLE);
        Centertext.setVisibility(View.VISIBLE);
        gzmdm.setVisibility(View.VISIBLE);
        gzmgs.setVisibility(View.VISIBLE);
        gzmgstext.setVisibility(View.VISIBLE);
    }

    public void visiblecmd() {
        rpm.setVisibility(View.INVISIBLE);
        speed.setVisibility(View.INVISIBLE);
        Load.setVisibility(View.INVISIBLE);
        Temp.setVisibility(View.INVISIBLE);
        Temp.setVisibility(View.INVISIBLE);
        TA.setVisibility(View.INVISIBLE);
        ITemp.setVisibility(View.INVISIBLE);
        MAF.setVisibility(View.INVISIBLE);
        TP.setVisibility(View.INVISIBLE);
        IMAP.setVisibility(View.INVISIBLE);
        Loadtext.setVisibility(View.INVISIBLE);
        Temptext.setVisibility(View.INVISIBLE);
        speedtext.setVisibility(View.INVISIBLE);
        rpmtext.setVisibility(View.INVISIBLE);
        TAtext.setVisibility(View.INVISIBLE);
        ITemptext.setVisibility(View.INVISIBLE);
        MAFtext.setVisibility(View.INVISIBLE);
        TPtext.setVisibility(View.INVISIBLE);
        IMAPtext.setVisibility(View.INVISIBLE);
        mConversationView.setVisibility(View.VISIBLE);
        mOutEditText.setVisibility(View.VISIBLE);
        mSendButton.setVisibility(View.VISIBLE);
        Centertext.setVisibility(View.INVISIBLE);
        gzmdm.setVisibility(View.INVISIBLE);
        gzmgs.setVisibility(View.INVISIBLE);
        gzmgstext.setVisibility(View.INVISIBLE);
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @SuppressLint("Wakelock")
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) mChatService.stop();
        try {
            if (locationManager != null) {
                locationManager.removeGpsStatusListener(gpsStatusListener);
                locationManager.removeUpdates(locationListener);
            }
        } catch (Exception e) {
        }
        wl.release();
    }

    private void ensureDiscoverable() {
        if (D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * 发送一条消息.
     *
     * @param message 发送一串文本
     */
    private void sendMessage(String message) {
        // 在尝试任何事情之前，请检查并确认我们确实已连接好
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_LONG).show();
            return;
        }

        // 检查一下是否有东西要发送
        if (message.length() > 0) {
            message = message + "\r";
            // 获取消息字节，并告诉蓝牙聊天服务进行写入
            byte[] send = message.getBytes();
            mChatService.write(send);

            // 将字符串缓冲区重置为零，并清除编辑文本字段
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // EditText小部件的动作监听器，监听返回键
    private TextView.OnEditorActionListener mWriteListener = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // 如果该操作是返回键上的关键事件，则发送消息
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if (D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };
    boolean tryconnect = false;
    // 从蓝牙聊天服务中获取信息的处理程序
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if (D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            try {
                                if (locationManager != null) {
                                    locationManager.removeGpsStatusListener(gpsStatusListener);
                                    locationManager.removeUpdates(locationListener);
                                }
                            } catch (Exception e) {
                            }
                            Status.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
                            OBDActivity.this.sendMessage("ATZ");
                            mConversationArrayAdapter.clear();
                            MenuItem itemtemp = menu.findItem(R.id.menu_connect_scan);
                            itemtemp.setTitle("取消连接");
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            Status.setText(R.string.title_connecting);
                            tryconnect = true;
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            tryconnect = false;
                            Status.setText(R.string.title_not_connected);
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // 从缓冲区构造一个字符串
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Command:  " + writeMessage.trim());
                    break;
                case MESSAGE_READ:
                    compileMessage(msg.obj.toString());
                    break;
                case MESSAGE_DEVICE_NAME:
                    // 保存连接设备的名称
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                            + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    private void settextsixe() {
        int txtsize = 14;
        int txtsizelarge = 18;
        int sttxtsize = 12;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindow().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int densityDpi = displayMetrics.densityDpi;
        if (densityDpi == DisplayMetrics.DENSITY_LOW) {
            Status.setTextSize(txtsize);
            Temp.setTextSize(txtsize);
            Load.setTextSize(txtsize);
            speed.setTextSize(txtsize);
            rpm.setTextSize(txtsize);
            TA.setTextSize(txtsize);
            ITemp.setTextSize(txtsize);
            MAF.setTextSize(txtsize);
            TP.setTextSize(txtsize);
            IMAP.setTextSize(txtsize);
            Temptext.setTextSize(txtsize);
            Loadtext.setTextSize(txtsize);
            speedtext.setTextSize(txtsize);
            rpmtext.setTextSize(txtsize);
            TAtext.setTextSize(txtsize);
            ITemptext.setTextSize(txtsize);
            MAFtext.setTextSize(txtsize);
            TPtext.setTextSize(txtsize);
            IMAPtext.setTextSize(txtsize);
            Centertext.setTextSize(txtsize);
            gzmdm.setTextSize(txtsize);
            gzmgs.setTextSize(txtsize);
            gzmgstext.setTextSize(txtsize);

        } else if (densityDpi == DisplayMetrics.DENSITY_MEDIUM) {
            Status.setTextSize(sttxtsize * 2);
            Temp.setTextSize(txtsizelarge * 2);
            Load.setTextSize(txtsizelarge * 2);
            speed.setTextSize(txtsizelarge * 2);
            rpm.setTextSize(txtsizelarge * 2);
            TA.setTextSize(txtsizelarge * 2);
            ITemp.setTextSize(txtsizelarge * 2);
            MAF.setTextSize(txtsizelarge * 2);
            TP.setTextSize(txtsizelarge * 2);
            IMAP.setTextSize(txtsizelarge * 2);
            Temptext.setTextSize(txtsizelarge * 2);
            Loadtext.setTextSize(txtsizelarge * 2);
            speedtext.setTextSize(txtsizelarge * 2);
            rpmtext.setTextSize(txtsizelarge * 2);
            TAtext.setTextSize(txtsizelarge * 2);
            ITemptext.setTextSize(txtsizelarge * 2);
            MAFtext.setTextSize(txtsizelarge * 2);
            TPtext.setTextSize(txtsizelarge * 2);
            IMAPtext.setTextSize(txtsizelarge * 2);
            Centertext.setTextSize(txtsizelarge * 2);
            gzmdm.setTextSize(txtsizelarge * 2);
            gzmgs.setTextSize(txtsizelarge * 2);
            gzmgstext.setTextSize(txtsizelarge * 2);

        } else if (densityDpi == DisplayMetrics.DENSITY_HIGH) {
            Status.setTextSize(sttxtsize * 3);
            Temp.setTextSize(txtsize * 3);
            Load.setTextSize(txtsize * 3);
            speed.setTextSize(txtsize * 3);
            rpm.setTextSize(txtsize * 3);
            TA.setTextSize(txtsize * 3);
            ITemp.setTextSize(txtsize * 3);
            MAF.setTextSize(txtsize * 3);
            TP.setTextSize(txtsize * 3);
            IMAP.setTextSize(txtsize * 3);
            Temptext.setTextSize(txtsize * 3);
            Loadtext.setTextSize(txtsize * 3);
            speedtext.setTextSize(txtsize * 3);
            rpmtext.setTextSize(txtsize * 3);
            TAtext.setTextSize(txtsize * 3);
            ITemptext.setTextSize(txtsize * 3);
            MAFtext.setTextSize(txtsize * 3);
            TPtext.setTextSize(txtsize * 3);
            IMAPtext.setTextSize(txtsize * 3);
            Centertext.setTextSize(txtsize * 3);
            gzmdm.setTextSize(txtsize * 3);
            gzmgs.setTextSize(txtsize * 3);
            gzmgstext.setTextSize(txtsize * 3);

        }
    }

    /***
     * 编译报文
     ***/
    private void compileMessage(String msg) {
        msg = msg.replace("null", "");
        msg = msg.substring(0, msg.length() - 2);
        msg = msg.replaceAll("\n", "");
        msg = msg.replaceAll("\r", "");
        /**
         * 判断是否初始化，如果没有初始化，就进行初始化，初始化完后判断命令模式
         */
        if (!initialized) {
            if (msg.contains("ELM327")) {
                msg = msg.replaceAll("ATZ", "");
                msg = msg.replaceAll("ATI", "");
                devicename = msg;
                getname = true;
                Status.setText(devicename);
            }
            if (msg.contains("ATDP")) {
                deviceprotocol = msg.replace("ATDP", "");
                getprotocol = true;
                Status.setText(devicename + " " + deviceprotocol);
            }
            String send = initializeCommands[whichCommand];
            OBDActivity.this.sendMessage(send);
            if (whichCommand == initializeCommands.length - 1) {
                whichCommand = 0;
                initialized = true;
                OBDActivity.this.sendMessage("ATRV");
            } else {
                whichCommand++;
            }
        } else {
            if (commandmode)
                mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + msg);
            else {
                int obdval = 0;
                tmpmsg = "";
                gzm = "";
                if (msg.length() > 4) {
                    if (msg.substring(0, 2).equals("41"))
                        try {
                            tmpmsg = msg.substring(0, 4);
                            obdval = Integer.parseInt(msg.substring(4, msg.length()), 16);
                        } catch (NumberFormatException nFE) {
                        }

                    else if (msg.substring(0, 2).equals("43"))
                        try {
                            tmpmsg = msg.substring(0, 2);
                            gzm = msg.substring(2, msg.length());
                            gzm = gzm.replaceAll("43", "");
                        } catch (NumberFormatException nFE) {
                        }
                }

                /*** 车速 ***/
                if (tmpmsg.equals("410D")) {
                    int SP = (int) (obdval);
                    speed.setText(Integer.toString(SP) + "km/h");
                }
                /*** 转速 ***/
                else if (tmpmsg.equals("410C")) {
                    int val = (int) (obdval / 4);
                    rpmval = val;
                    rpm.setText(Integer.toString(rpmval) + "Rpm");
                }
                /*** 冷却液温度 ***/
                else if (tmpmsg.equals("4105")) {
                    int tempC = obdval - 40;
                    currenttemp = tempC;
                    Temp.setText(Integer.toString(tempC) + "°C");
                }
                /*** 进气温度 ***/
                else if (tmpmsg.equals("410F")) {
                    int IAtemp = obdval - 40;
                    ITemp.setText(Integer.toString(IAtemp) + "°C");
                }
                /*** 进气流量 ***/
                else if (tmpmsg.equals("4110")) {
                    int MAFrate = (int) (obdval / 100);
                    MAF.setText(Integer.toString(MAFrate) + "g/s");
                }
                /*** 进气歧管压力 ***/
                else if (tmpmsg.equals("410B")) {
                    int InMAP = (int) (obdval);
                    IMAP.setText(Integer.toString(InMAP) + "Kpa");
                }
                /*** 节气门位置 ***/
                else if (tmpmsg.equals("4111")) {
                    int Tpos = obdval * 100 / 255;
                    TP.setText(Integer.toString(Tpos) + " %");
                }
                /*** 点火提前角 ***/
                else if (tmpmsg.equals("410E")) {
                    int Tad = (int) (obdval / 2 - 64);
                    TA.setText(Integer.toString(Tad) + "°");
                }
                /*** 发动机负荷 ***/
                else if (tmpmsg.contains("4104")) {
                    int calcLoad = obdval * 100 / 255;
                    Load.setText(Integer.toString(calcLoad) + " %");
                    String avg = null;
                    if (Enginetype == 0) {
                        if (currenttemp <= 55) {
                            avg = String.format("%10.1f", (0.001 * 0.004 * 4 * Enginedisplacement * rpmval * 60 * calcLoad / 20)).trim();
                            avgconsumption.add((0.001 * 0.004 * 4 * Enginedisplacement * rpmval * 60 * calcLoad / 20));
                        } else if (currenttemp > 55) {
                            avg = String.format("%10.1f", (0.001 * 0.003 * 4 * Enginedisplacement * rpmval * 60 * calcLoad / 20)).trim();
                            avgconsumption.add((0.001 * 0.003 * 4 * Enginedisplacement * rpmval * 60 * calcLoad / 20));
                        }
                    } else if (Enginetype == 1) {
                        if (currenttemp <= 55) {
                            avg = String.format("%10.1f", (0.001 * 0.004 * 4 * 1.35 * Enginedisplacement * rpmval * 60 * calcLoad / 20)).trim();
                            avgconsumption.add((0.001 * 0.004 * 4 * 1.35 * Enginedisplacement * rpmval * 60 * calcLoad / 20));
                        } else if (currenttemp > 55) {
                            avg = String.format("%10.1f", (0.001 * 0.003 * 4 * 1.35 * Enginedisplacement * rpmval * 60 * calcLoad / 20)).trim();
                            avgconsumption.add((0.001 * 0.003 * 4 * 1.35 * Enginedisplacement * rpmval * 60 * calcLoad / 20));
                        }
                    }
                }
                //解析故障码
//                else if (tmpmsg.contains("43")) {
//                    Log.e("xwl", gzm);
//                    String g = "", g1 = "", g2 = "";
//                    for (int j = 0, k = 0; k < (gzm.length() - 4); k++) {
//                        if (gzm.substring(k, k + 4).equals("0000")) {
//                            if (k == 0) {
//                                j = 0;
//                                g = "";
//                            } else {
//                                for (int t = 0; t < k; t = t + 4) {
//                                    j = j + 1;
//                                    g2 = gzm.substring(t, t + 4);
//                                    g = g1 + g2;
//                                    g1 = g;
//                                }
//                            }
//                            gzmgs.setText(j);
//                            gzmdm.setText(g);
//                            break;
//                        }
//                    }
//                }
                else if (tmpmsg.contains("43")) {
                    String[] arr = gzm.trim().split("");
                    String[] b = new String[arr.length - 1];
                    for (int i = 0; i < b.length; i++) {
                        b[i] = arr[i + 1];
                    }
                    String[] a = new String[b.length / 4];
                    for (int i = 0; i < b.length; i = i + 4) {//String数组，不过arr[0]为空
                        a[i / 4] = b[i] + b[i + 1] + b[i + 2] + b[i + 3];
                    }
                    gzmgs.setText(a.length - 1 + "");
                    String gzmstr = "";
                    for (int i = 0; i < a.length; i++) {
                        if (a[i].equals("0000")) {
                            continue;
                        }
                        gzmstr += a[i] + " ";
                    }
                    List<String> list = new ArrayList<String>();
                    for (int i = 0; i < a.length; i++) {
                        if (a[i].equals("0000")) {
                            continue;
                        }
                        list.add(a[i]);
                    }
                    gzmgs.setText(list.size()+"");
                    gzmdm.setText(gzmstr);
                }


                ////命令/////////////
                String send = commands[whichCommand];
                OBDActivity.this.sendMessage(send);
                if (whichCommand >= commands.length - 1) {
                    whichCommand = 0;
                } else {
                    whichCommand++;
                }

            }
        }
    }

    private double calculateAverage(ArrayList<Double> listavg) {
        Double sum = 0.0;
        for (Double val : listavg) {
            sum += val;
        }
        return sum.doubleValue() / listavg.size();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // 当DeviceListActivity以一个设备返回连接时
                if (resultCode == Activity.RESULT_OK) {
                    getname = false;
                    connectDevice(data);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    if (mChatService == null) setupChat();
                } else {
                    Toast.makeText(this, "BT not enabled", Toast.LENGTH_SHORT).show();
                    if (mChatService == null) setupChat();
                }
        }
    }

    private void connectDevice(Intent data) {
        // 获取设备MAC地址
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // 获取蓝牙设备对象
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // 尝试连接到设备
        getname = false;
        getprotocol = false;
        commandmode = false;
        getecho = false;
        initialized = false;
        setprotocol = false;
        whichCommand = 0;

        mChatService.connect(device);
    }

    private Menu menu;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    private void hideVirturalKeyboard() {
        try {
            InputMethodManager inputManager = (InputMethodManager)
                    this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        hideVirturalKeyboard();
        switch (item.getItemId()) {
            case R.id.menu_connect_scan:
                if (item.getTitle().equals("连接设备")) {
                    // 启动DeviceListActivity查看设备并进行扫描
                    serverIntent = new Intent(this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                } else {
                    if (mChatService != null) mChatService.stop();
                    item.setTitle("连接设备");
                    Status.setText("Not Connected");
                    resetvalues();
                    if (gps) {
                        if (gps_enabled) {
                            initGps();
                        }
                    }
                }

                return true;
            case R.id.menu_command:
                mConversationArrayAdapter.clear();
                if (item.getTitle().equals("通信会话")) {
                    commandmode = true;
                    visiblecmd();
                    item.setTitle("常用参数");
                } else {
                    invisiblecmd();
                    item.setTitle("通信会话");
                    commandmode = false;
                    OBDActivity.this.sendMessage("ATRV");
                }
                return true;

            case R.id.menu_settings:
                // 启动DeviceListActivity查看设备并进行扫描
                serverIntent = new Intent(this, Prefs.class);
                startActivity(serverIntent);
                return true;

            case R.id.menu_reset:
                getname = false;
                getprotocol = false;
                commandmode = false;
                getecho = false;
                setprotocol = false;
                whichCommand = 0;
                invisiblecmd();
                commandmode = false;
                MenuItem itemtemp = menu.findItem(R.id.menu_command);
                itemtemp.setTitle("SETPID");
                resetvalues();
                OBDActivity.this.sendMessage("ATZ");
                return true;
        }
        return false;
    }

}
