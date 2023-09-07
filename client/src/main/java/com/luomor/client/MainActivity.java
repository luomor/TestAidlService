package com.luomor.client;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.luomor.testaidlservice.IAidlCallBack;
import com.luomor.testaidlservice.IAidlInterface;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private final String tag = MainActivity.class.getSimpleName();

    @BindView(R.id.list_view)
    ListView listView;
    Button m_startService, m_stopServie, m_printerInfo, m_printPhoto, m_resetPrinter, m_resumeJob,
            m_clearText, m_serviceStatus, m_ejectJam, m_setAutoPowerOff, m_cleanPaperPath, m_updateFW;
    private IAidlInterface iAidlInterface;
    private int num;
    private List<String> messages = new ArrayList<>();
    private ArrayAdapter arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Intent intent = new Intent();
        String ACTION = "AIDL.service";
        intent.setAction(ACTION);
        intent.setPackage("com.luomor.testaidlservice");
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        m_startService = (Button) findViewById(R.id.b_startService);
        m_stopServie = (Button) findViewById(R.id.b_stopService);
        m_printerInfo = (Button) findViewById(R.id.b_printerInfo);
        m_printPhoto = (Button) findViewById(R.id.b_printPhoto);
        m_resetPrinter = (Button) findViewById(R.id.b_resetPrinter);
        m_resumeJob = (Button) findViewById(R.id.b_resumeJob);
        m_ejectJam = (Button) findViewById(R.id.b_ejectPaperJam);
        m_cleanPaperPath = (Button) findViewById(R.id.b_cleanPaperPath);
        m_clearText = (Button) findViewById(R.id.b_clearText);
        m_serviceStatus = (Button) findViewById(R.id.b_serviceStatus);
        m_setAutoPowerOff = (Button) findViewById(R.id.b_setAutoPowerOff);
        m_updateFW = (Button) findViewById(R.id.b_updateFW);

        m_startService.setOnClickListener(this);
        m_stopServie.setOnClickListener(this);
        m_printerInfo.setOnClickListener(this);
        m_printPhoto.setOnClickListener(this);
        m_resetPrinter.setOnClickListener(this);
        m_resumeJob.setOnClickListener(this);
        m_clearText.setOnClickListener(this);
        m_serviceStatus.setOnClickListener(this);
        m_ejectJam.setOnClickListener(this);
        m_setAutoPowerOff.setOnClickListener(this);
        m_cleanPaperPath.setOnClickListener(this);
        m_updateFW.setOnClickListener(this);
    }


    @OnClick(R.id.send_message)
    public void onViewClicked(View view) {
        if (iAidlInterface != null) {
            try {
                iAidlInterface.sendMessage("客户端消息" + num);
                num++;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @OnClick(R.id.start_service)
    public void onViewClicked1(View view) {
        if (iAidlInterface != null) {
            try {
                iAidlInterface.sendMessage("startService");
                num++;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClick(View v) {
        String actionName = (String) v.getTag();
        Log.i(tag, "onClick actionName: " + actionName);

        switch (v.getId()) {

            //
            // Start background service
            //
            case R.id.b_startService:
                if (iAidlInterface != null) {
                    try {
                        iAidlInterface.sendMessage("startService");
                        num++;
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                break;

            //
            // Stop and destroy background service
            //
            case R.id.b_stopService:

                break;

            /* print photo */
            case R.id.b_printPhoto:
                break;

            /* reset printer */
            case R.id.b_resetPrinter:
                break;

            /* clear error and continue job */
            case R.id.b_resumeJob:
                break;

            case R.id.b_updateFW:
                break;

            case R.id.b_ejectPaperJam:
                break;

            case R.id.b_setAutoPowerOff:
                break;
            case R.id.b_clearText:
                break;

            case R.id.b_serviceStatus:
                break;

            case R.id.b_printerInfo:
                break;

            case R.id.b_cleanPaperPath:
                break;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Toast.makeText(getApplicationContext(), "已连接服务器", Toast.LENGTH_LONG).show();
            iAidlInterface = IAidlInterface.Stub.asInterface(iBinder);
            try {
                iAidlInterface.asBinder().linkToDeath(mDeathRecipient, 0);
                iAidlInterface.registerCallBack(iAidlCallBack);
                messages.addAll(iAidlInterface.getMessages());
                listView.setAdapter(arrayAdapter = new ArrayAdapter<>(getApplicationContext(), android.R.layout.simple_list_item_1, messages));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
        //当承载IBinder的进程消失时接收回调的接口
        @Override
        public void binderDied() {
            if (null == iAidlInterface) {
                return;
            }
            iAidlInterface.asBinder().unlinkToDeath(mDeathRecipient, 0);
            iAidlInterface = null;
            //断线重来逻辑
        }
    };

    private IAidlCallBack iAidlCallBack = new IAidlCallBack.Stub() {
        @Override
        public void onMessageSuccess(String message) {
            if (messages != null && arrayAdapter != null) {
                messages.add(message);
                handler.sendEmptyMessage(1);
            }
        }
    };

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            arrayAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onDestroy() {
        //解除注册
        if (null != iAidlInterface && iAidlInterface.asBinder().isBinderAlive()) {
            try {
                iAidlInterface.unregisterCallBack(iAidlCallBack);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        //解除绑定服务
        unbindService(serviceConnection);
        super.onDestroy();
    }
}
