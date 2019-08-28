package com.example.mqttdemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ClientActivity";

    // 连接 ActiveMQ 的URI
    private String serverUri = "tcp://172.18.90.20:1883";

    // 客户端 ID，用以识别客户端，该id必须为唯一，否则两个客户端会不断的重连，所以使用sn码效果会更好
    private String clientId;

    // 订阅的主题名称,该主题名称可自定义
    final String subscriptionTopic = "newNotification";

    // MQTT 客户端
    private MqttAndroidClient mqttAndroidClient;

    //订阅参数配置
    private MqttConnectOptions mqttConnectOptions;

    //连接账号和密码，没有设置连接密码，所以为null
    private String userName = null;
    private char[] password = null;

    //连接超时
    private int connectionTimeout = 30;

    //心跳
    private int keepAliveInterval = 60;

    //连接按钮
    Button connect;
    //订阅按钮
    Button subscribe;

    //是否已经订阅
    boolean isSubscribe = false;

    //loading
    private ProgressDialog progressDialog;

    private ScrollView scrollView;

    //事件到来回调
    private MqttCallbackExtended mqttCallbackExtended = new MqttCallbackExtended() {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {

            if (reconnect) {
                // 自动重连连接成功
                Log.d(TAG, "connectComplete: ");
                connect.setText(R.string.connected);
                if (isSubscribe) {
                    subscribeToTopic();
                }
            }
        }

        @Override
        public void connectionLost(Throwable cause) {
            Log.d(TAG, "connectionLost: ");
            // 连接中断
            connect.setText(R.string.toConnect);
            subscribe.setText(R.string.toSubscribe);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // 消息到达
            Log.d(TAG, "messageArrived: " + topic + "：：" + message);

            //我使用的是scrollView,用来直观的显示是否能收到消息，如果是测试大量并发，注释掉这行
            fillingView(topic, message);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // 消息成功传输后调用
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        finish();
        initView();
        clientId = android.os.Build.SERIAL;
    }

    private void initView() {
        scrollView = findViewById(R.id.scrollView);

        connect = findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (isConnect()) {
                        connect.setText(R.string.toConnect);
                        mqttAndroidClient.disconnect();
                        isSubscribe = false;
                        subscribe.setText(R.string.toSubscribe);
                        mqttAndroidClient = null;
                        return;
                    }

                    //初始化连接器
                    initMQTT();
                    //提示加载框
                    buildProgressDialog(getResources().getString(R.string.connecting));
                    //连接并且订阅消息
                    connectRemote();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });

        subscribe = findViewById(R.id.subscribe);
        subscribe.setText(R.string.toSubscribe);
        subscribe.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isSubscribe) {
                    subscribe.setText(R.string.toSubscribe);
                    unSubscribe();
                    isSubscribe = false;
                    return;
                }

                subscribeToTopic();
            }
        });

        //发布消息
        Button publish = findViewById(R.id.publish);
        publish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toPublish();
            }
        });

        //发布大量消息
        Button publishBig = findViewById(R.id.publishBig);
        publishBig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isConnect()) {
                    return;
                }

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < 10000; i++) {
                            try {
                                MqttMessage mqttMessage = new MqttMessage((i + "").getBytes());
                                mqttAndroidClient.publish(subscriptionTopic, mqttMessage);
                            } catch (MqttException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();

            }
        });
    }

    private void initMQTT() {
        if (mqttAndroidClient != null && mqttAndroidClient.isConnected()) {
            return;
        }

        //用来连接
        if (mqttAndroidClient == null) {
            mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        }

        //设置监听
        mqttAndroidClient.setCallback(mqttCallbackExtended);

        // 设置 MQTT 连接参数
        mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        //清除会话缓存
        mqttConnectOptions.setCleanSession(true);
        //我没有设置连接密码
        mqttConnectOptions.setUserName(userName);
        mqttConnectOptions.setPassword(password);
        mqttConnectOptions.setConnectionTimeout(connectionTimeout);  //超时时间
        mqttConnectOptions.setKeepAliveInterval(keepAliveInterval); //心跳时间,单位秒
        mqttConnectOptions.setAutomaticReconnect(true);//自动重连
    }

    /**
     * 使用 MQTT 协议连接 ActiveMQ
     */
    private void connectRemote() throws MqttException {
        connect.setText(R.string.connecting);
        mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {

                //连接成功之后设置连接断开的缓冲配置
                DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                disconnectedBufferOptions.setBufferEnabled(true);
                disconnectedBufferOptions.setBufferSize(100);
                disconnectedBufferOptions.setPersistBuffer(false);
                disconnectedBufferOptions.setDeleteOldestMessages(false);
                mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                cancelProgressDialog();
                connect.setText(R.string.connected);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                //失败
                cancelProgressDialog();
                exception.printStackTrace();
                connect.setText(R.string.toConnect);

            }
        });
    }

    //订阅消息
    private void subscribeToTopic() {
        if (!isConnect()) {
            Toast.makeText(getApplicationContext(),
                    getResources().getString(R.string.toConnect), Toast.LENGTH_LONG).show();
            return;
        }

        buildProgressDialog(getResources().getString(R.string.toSubscribe));

        try {
            //订阅主题
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    //订阅成功的回调
                    subscribe.setText(R.string.subscribed);
                    isSubscribe = true;
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    //订阅石板的回调
                    subscribe.setText(R.string.toSubscribe);
                    isSubscribe = false;
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        } finally {
            cancelProgressDialog();
        }
    }

    //取消订阅某个事件
    private void unSubscribe() {
        if (isConnect()) {
            try {
                mqttAndroidClient.unsubscribe(subscriptionTopic);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }


    //判断是否连接
    private boolean isConnect() {
        if (mqttAndroidClient == null) {
            return false;
        }

        return mqttAndroidClient.isConnected();
    }


    /**
     * //代表设置粘性事件
     * //  mqttMessage.setRetained(true);
     * <p>
     * // 想要取消粘性事件，要这样构建消息
     * String m = et.getText().toString();
     * MqttMessage mqttMessage = new MqttMessage();
     * byte[] bytes = {};
     * mqttMessage.setPayload(bytes);
     * mqttMessage.setRetained(true);
     */
    //发布事件
    private void toPublish() {
        if (!isConnect()) {
            return;
        }

        final EditText et = new EditText(this);
        et.setHint(getResources().getString(R.string.inputContent));

        new AlertDialog.Builder(this).setTitle(getResources().getString(R.string.dialogTitle))
                .setView(et)
                .setPositiveButton(getResources().getString(R.string.dialogOk), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        String input = et.getText().toString();
                        if (input.equals("")) {
                            Toast.makeText(getApplicationContext(),
                                    getResources().getString(R.string.contentIsNull) + input, Toast.LENGTH_LONG).show();
                            return;
                        }

                        String m = et.getText().toString();
                        MqttMessage mqttMessage = new MqttMessage();
                        mqttMessage.setPayload(m.getBytes());
                        try {
                            mqttAndroidClient.publish(subscriptionTopic, mqttMessage);
                        } catch (MqttException e) {
                            e.printStackTrace();
                        }
                    }

                })
                .setNegativeButton(getResources().getString(R.string.dialogCancel), null)
                .show();

    }

    //向ScrollView填充数据
    private void fillingView(String topic, MqttMessage mqttMessage) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_rv, null, false);
        TextView subscription = view.findViewById(R.id.subscription);
        TextView message = view.findViewById(R.id.message);

        subscription.setText(topic);
        message.setText(mqttMessage.toString());

        LinearLayout linearLayout = scrollView.findViewById(R.id.ll_content);
        linearLayout.addView(view, 0);

    }

    //加载框
    public void buildProgressDialog(String m) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
        progressDialog.setMessage(m);
        progressDialog.setCancelable(true);
        progressDialog.show();
    }

    //取消加载框
    public void cancelProgressDialog() {
        if (progressDialog != null)
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
    }

    //断开与服务器的连接
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mqttAndroidClient != null) {
                mqttAndroidClient.disconnect();
                mqttAndroidClient.close();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
