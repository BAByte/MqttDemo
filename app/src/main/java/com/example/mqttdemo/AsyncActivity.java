package com.example.mqttdemo;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.ArrayList;
import java.util.List;

public class AsyncActivity extends AppCompatActivity {
    private static final String TAG = "AsyncActivity";

    // 连接 ActiveMQ 的URI
    private String serverUri = "tcp://172.18.90.20:1883";

    // 客户端 ID，用以识别客户端，该id必须为唯一，否则两个客户端会不断的重连，所以使用sn码效果会更好
    private String clientId;

    // 订阅的主题名称,该主题名称可自定义
    final String subscriptionTopic = "newNotification";

    //订阅参数配置
    private MqttConnectOptions mqttConnectOptions;

    //连接账号和密码，没有设置连接密码，所以为null
    private String userName = null;
    private char[] password = null;

    //默认消息数量
    private static final int COUNT = 10000;

    private EditText countEdit;

    private List<MqttAsyncClient> mqttAsyncClients = new ArrayList<>();

    //连接超时
    private int connectionTimeout = 30;

    //心跳
    private int keepAliveInterval = 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_async);

        clientId = android.os.Build.SERIAL;

        initView();

    }

    private void initView() {
        countEdit = findViewById(R.id.countEdit);

        //发布大量消息
        Button publishBig = findViewById(R.id.publishBig);
        publishBig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    int count = COUNT;
                    String text = countEdit.getText().toString();
                    if (!"".equals(text)) {
                        count = Integer.parseInt(text);
                    }

                    connectRemote(initMQTT(clientId + System.currentTimeMillis()), count);
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void openOneClientAndSend(final MqttAsyncClient client, final int count) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: " + count);
                for (int i = 0; i < count; i++) {
                    try {
                        MqttMessage mqttMessage = new MqttMessage((i + "").getBytes());
                        client.publish(subscriptionTopic, mqttMessage);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();
    }

    private MqttAsyncClient initMQTT(String clientId) {
        try {
            MqttAsyncClient mqttAsyncClient = new MqttAsyncClient(serverUri, clientId, new MemoryPersistence());

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

            mqttAsyncClients.add(mqttAsyncClient);
            return mqttAsyncClient;
        } catch (MqttException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * 使用 MQTT 协议连接 ActiveMQ
     */
    private void connectRemote(final MqttAsyncClient client, final int count) throws MqttException {
        client.connect(mqttConnectOptions, null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {

                //连接成功之后设置连接断开的缓冲配置
                DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                disconnectedBufferOptions.setBufferEnabled(true);
                disconnectedBufferOptions.setBufferSize(100);
                disconnectedBufferOptions.setPersistBuffer(false);
                disconnectedBufferOptions.setDeleteOldestMessages(false);
                client.setBufferOpts(disconnectedBufferOptions);

                //连接成功去发送
                openOneClientAndSend(client, count);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Toast.makeText(AsyncActivity.this, R.string.connectFail, Toast.LENGTH_SHORT).show();
                //失败
                exception.printStackTrace();
            }
        });
    }


    //断开与服务器的连接
    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (MqttAsyncClient client : mqttAsyncClients) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }

        mqttAsyncClients.clear();

    }
}
