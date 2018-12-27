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

import java.util.Set;

import com.obdelm327.R;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * 该活动将作为一个对话框出现。
 * 它列出在该区域检测到的任何配对设备。
 * 当设备被用户选择时，设备的MAC地址将被发送回结果意图中的父活动。
 **/
public class DeviceListActivity extends Activity {
    // 调试
    private static final String TAG = "DeviceListActivity";
    private static final boolean D = true;

    // 返回选定设备
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // 成员字段
    private BluetoothAdapter mBtAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置窗口
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.device_list);

        // 设置结果CANCELED ，以防用户退出。
        setResult(Activity.RESULT_CANCELED);

        // 初始化按钮以执行设备发现
        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doDiscovery();
                v.setVisibility(View.GONE);
            }
        });

        // 数组初始化适配器。一个用于已经成对的设备，一个用于新发现的设备
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

        // 查找并设置成对设备的ListView
        ListView pairedListView = (ListView) findViewById(R.id.paired_devices);
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        // 查找并设置新发现设备的ListView
        ListView newDevicesListView = (ListView) findViewById(R.id.new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // 当设备被发现时，注册广播
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // 当发现完成时，注册广播
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        // 获取本地蓝牙适配器
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();

        // 获取一组当前配对的设备
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        // 如果有成对的设备，将每个设备添加到ArrayAdapter中
        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.none_paired).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 确保我们不再进行探索
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }

        // 注销广播听众
        this.unregisterReceiver(mReceiver);
    }

    /**
     * 使用蓝牙适配器启动设备
     **/
    private void doDiscovery() {
        if (D) Log.d(TAG, "doDiscovery()");

        // 在标题中显示扫描
        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.scanning);

        // 打开新设备的子标题
        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        // 如果我们已经发现，停止它
        if (mBtAdapter.isDiscovering()) {
            mBtAdapter.cancelDiscovery();
        }

        // 从蓝牙适配器中请求发现
        mBtAdapter.startDiscovery();
    }

    // ListViews中所有设备的on-click侦听器
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // 取消发现，因为它很重要，我们需要连接。
            mBtAdapter.cancelDiscovery();

            // 获取设备MAC地址，这是视图中最后的17个字符
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // 创建结果意图并包括MAC地址
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            // 设置结果并完成此活动
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    // 侦听发现的设备并在发现完成时更改标题的广播接收器
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // 当发现找到一个装置
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 从目标中获取蓝牙设备对象
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 如果它已经配对了，跳过它，因为它已经被列出了
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            // 当发现完成后，更改活动标题
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.select_device);
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

}
