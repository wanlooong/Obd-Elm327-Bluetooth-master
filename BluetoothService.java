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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

/**
 * 负责设置和管理与其他设备的蓝牙连接。
 * 有一个线程，用于侦听传入的连接、连接设备的线程和连接时执行数据传输的线程
 **/
public class BluetoothService {
    // 调试
    private static final String TAG = "BluetoothService";
    private static final boolean D = true;

    // 创建服务器套接字时的SDP记录的名称
    private static final String NAME = "SensBox";

    // 此应用程序的通用唯一识别码
    private static final UUID MY_UUID =
            UUID.fromString("0001101-0000-1000-8000-00805F9B34FB");

    //INSECURE	"8ce255c0-200a-11e0-ac64-0800200c9a66"
    //SECURE	"fa87c0d0-afac-11de-8a39-0800200c9a66"
    //SPP		"0001101-0000-1000-8000-00805F9B34FB"

    // 成员字段
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    // 指示当前连接状态的常量
    public static final int STATE_NONE = 0;        // 什么都不做
    public static final int STATE_LISTEN = 1;      // 监听输入连接
    public static final int STATE_CONNECTING = 2;  // 启动输出连接
    public static final int STATE_CONNECTED = 3;   // 连接到远程设备

    /**
     * 构造函数  创建一个新的蓝牙聊天会话
     *
     * @param context UI活动背景
     * @param handler 将消息发送回UI活动的处理程序
     */
    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;
    }

    /**
     * 设置聊天连接的当前状态
     *
     * @param state 定义当前连接状态的整数
     **/
    private synchronized void setState(int state) {
        if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;

        // 将新状态交给处理程序，以便UI活动可以更新
        mHandler.obtainMessage(OBDActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * 返回当前连接状态
     **/
    public synchronized int getState() {
        return mState;
    }

    /**
     * 开始聊天服务。启动AcceptThread开始一个会话(服务器)模式。由活动onResume()调用
     **/
    public synchronized void start() {
        if (D) Log.d(TAG, "start");

        // 取消任何试图连接的线程
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // 取消当前运行连接的任何线程
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        setState(STATE_LISTEN);

        // 启动线程来监听蓝牙服务器套接字
        if (mAcceptThread == null) {
            mAcceptThread = new AcceptThread();
            mAcceptThread.start();
        }
    }

    /**
     * 启动ConnectThread来启动与远程设备的连接
     *
     * @param device 蓝牙设备连接
     **/
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device);

        // 取消任何试图连接的线程
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // 取消当前运行连接的任何线程
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // 启动线程以连接到给定的设备
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
    }

    /**
     * 启动ConnectedThread开始管理蓝牙连接
     *
     * @param socket 蓝牙通信连接端口
     * @param device 已经连接的蓝牙设备
     **/
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device, final String socketType) {
        if (D) Log.d(TAG, "connected, Socket Type:" + socketType);

        // 取消完成连接的线程
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // 取消当前运行连接的任何线程
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // 取消接收线程，因为我们只想连接到一个设备
        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        // 启动线程来管理连接并执行传输
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // 将连接设备的名称发送回UI活动
        Message msg = mHandler.obtainMessage(OBDActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(OBDActivity.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    /**
     * 停止所有线程
     **/
    public synchronized void stop() {
        if (D) Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }
        setState(STATE_NONE);
    }

    /**
     * 以不同步的方式写入ConnectedThread
     *
     * @param out 字节写入
     * @see ConnectedThread#write(byte[])
     **/
    public void write(byte[] out) {
        // 创建临时对象
        ConnectedThread r;
        // 同步一个ConnectedThread的副本
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // 执行写同步
        r.write(out);
    }

    /**
     * 指示连接尝试失败并通知UI活动
     **/
    private void connectionFailed() {
        // 将失败消息发送回活动
        Message msg = mHandler.obtainMessage(OBDActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(OBDActivity.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // 启动服务，重新启动监听模式。
        BluetoothService.this.start();
    }

    /**
     * 指示连接丢失并通知UI活动
     **/
    private void connectionLost() {
        // 将失败消息发送回活动
        Message msg = mHandler.obtainMessage(OBDActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(OBDActivity.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // 启动服务，重新启动监听模式。
        BluetoothService.this.start();
    }

    /**
     * 此线程在侦听传入连接时运行。
     * 它的行为就像一个服务器端客户端。它运行直到连接被接受(或者被取消)
     **/
    private class AcceptThread extends Thread {
        // 本地服务器套接字
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // 创建一个新的监听服务器套接字
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (D) Log.d(TAG, "Socket Type: " + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // 如果没有完成连接，监听服务器套接字
            while (mState != STATE_CONNECTED) {
                try {
                    // 这是一个阻塞调用，只返回成功的连接或异常
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + mSocketType + "accept() failed", e);
                    break;
                }

                // 如果一个连接被接受
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // 情况正常。开始连接线程。
                                connected(socket, socket.getRemoteDevice(), mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // 要么没有准备好，要么已经连接好了。终止新的套接字。
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            if (D) Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);

        }

        public void cancel() {
            if (D) Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    /**
     * 此线程在尝试与设备进行传出连接时运行。
     * 它直接通过运行;连接要么成功，要么失败。
     **/
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            BluetoothSocket tmp = null;

            //为给定的蓝牙设备连接一个蓝牙接口
            try {
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // 总是取消发现，因为它会减慢连接速度。
            mAdapter.cancelDiscovery();

            // 连接到蓝牙接口
            try {
                // 这是一个阻塞调用，只返回成功的连接或异常。
                mmSocket.connect();
            } catch (IOException e) {
                // 关闭接口
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // 重新设置ConnectThread，因为我们已经完成了。
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // 开始连接线程
            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * 此线程在与远程设备的连接期间运行。
     * 它处理所有传入和传出的传输。
     **/
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // 获取蓝牙套接字输入和输出流。
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        String s, msg;

        public void run() {
            while (true) {
                try {
                    byte[] buffer = new byte[1];
                    int bytes = mmInStream.read(buffer, 0, buffer.length);
                    s = new String(buffer);
                    for (int i = 0; i < s.length(); i++) {
                        char x = s.charAt(i);
                        msg = msg + x;
                        if (x == 0x3e) {
                            mHandler.obtainMessage(OBDActivity.MESSAGE_READ, buffer.length, -1, msg).sendToTarget();
                            msg = "";
                        }
                    }
                    // 做一些有字节读入的操作。在tempBuffer中有bytesRead字节。
                } catch (IOException e) {
                    connectionLost();
                    // 启动服务，重新启动监听模式。
                    BluetoothService.this.start();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // 将发送的消息共享回UI活动。
                mHandler.obtainMessage(OBDActivity.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
