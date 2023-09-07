package com.luomor.testaidlservice;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.hiti.usb.bitmapmanager.BitmapMonitor;
import com.hiti.usb.bitmapmanager.BitmapMonitorResult;
import com.hiti.usb.jni.JniData;
import com.hiti.usb.printer.PrinterJob;
import com.hiti.usb.printer.PrinterStatus;
import com.hiti.usb.service.Action;
import com.hiti.usb.service.ErrorCode;
import com.hiti.usb.service.ServiceConnector;
import com.hiti.usb.utility.ByteUtility;
import com.hiti.usb.utility.FileUtility;
import com.hiti.usb.utility.MobileInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private final String tag = MainActivity.class.getSimpleName();

    @BindView(R.id.list_view)
    ListView listView;
    TextView m_info, m_serviceInfo;
    ImageView m_infoView;

    //MyReceiver receiver;
    ServiceConnector serviceConnector;
    PrinterOperation operation;
    private int PaperType;
    private short MATTE, PRINTCOUNT, PRINTMODE;

    //update firmware
    String m_fwversion, m_fwpath, m_fwfolderpath, m_fwBootpath, m_fwKernelpath;

    ScheduledExecutorService exec3, exec2;
    String mSelectedPath = "";
    String m_strTablesCopyRoot = "";
    String m_strTablesRoot = "";
    private IAidlInterface iAidlInterface;
    private int num;
    private List<String> messages = new ArrayList<>();
    private ArrayAdapter arrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        Intent intent = new Intent(getApplicationContext(), AidlService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);

        m_info = (TextView) findViewById(R.id.t_info);
        m_serviceInfo = (TextView) findViewById(R.id.t_service);
        m_infoView = (ImageView) findViewById(R.id.m_infoView);

        Log.v(tag, "onCreate");
        //Create and Copy color bin file from asset folder
        m_strTablesCopyRoot = this.getExternalFilesDir(null).getAbsolutePath();
        m_strTablesRoot = this.getExternalFilesDir(null).getAbsolutePath() + "/Tables";
        if (!FileUtility.FileExist(m_strTablesRoot)) {
            FileUtility.CreateFolder(m_strTablesRoot);
            copyFileOrDir("Tables");
        }
        InitialValue();
        serviceConnector = ServiceConnector.register(this, null);

        operation = new PrinterOperation(this, serviceConnector);
        //operation.m_strTablesRoot = m_strTablesRoot;
        operation.m_strTablesRoot = "";
    }


    @OnClick(R.id.send_message)
    public void onViewClicked(View view) {
        if (iAidlInterface != null) {
            try {
                iAidlInterface.sendMessage("消息" + num);
                num++;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            iAidlInterface = IAidlInterface.Stub.asInterface(iBinder);
            try {
                iAidlInterface.asBinder().linkToDeath(mDeathRecipient, 0);//监听进程是否消失
                iAidlInterface.registerCallBack(iAidlCallBack);//注册消息回调
                messages.addAll(iAidlInterface.getMessages());//获取历史消息
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
            try {
                iAidlInterface.unregisterCallBack(iAidlCallBack);//注销
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            iAidlInterface.asBinder().unlinkToDeath(mDeathRecipient, 0);
            iAidlInterface = null;
        }
    };

    private IAidlCallBack iAidlCallBack = new IAidlCallBack.Stub() {
        @Override
        public void onMessageSuccess(String message) {
            if (messages != null && arrayAdapter != null) {
                messages.add(message);
                handler.sendEmptyMessage(1);
                if (message.equals("startService")) {
                    handlerPrinter.sendEmptyMessage(1);
                } else if(message.equals("serviceStatus")) {
                    handlerPrinter.sendEmptyMessage(10);
                }
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

    @SuppressLint("HandlerLeak")
    private Handler handlerPrinter = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            printerControl(msg.what);
        }
    };

    private void InitialValue() {
        //init value
        PaperType = 2; //Set printout size. 2:4x6, 3:5x7, 4:6x8
        MATTE = 1; //1:matte, 0:notmatte
        PRINTCOUNT = 1; //Want to print count
        PRINTMODE = 0; //Only for P232W, default 0. 1:fine mode(HOD), 0:standard mode

        //FW version and path
        m_fwversion = "1.16.0.Z";
        m_fwfolderpath = this.getExternalFilesDir(null).getAbsolutePath() + "/HiTi_FW";
        m_fwpath = m_fwfolderpath + "/ROM_ALL_p520l.bin";
        if (!FileUtility.FileExist(m_fwfolderpath)) {
            FileUtility.CreateFolder(m_fwfolderpath);
        }
        //Copy asset fw to absolutepath
        AssetManager assetManager = this.getAssets();
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open("HiTi_FW/ROM_ALL_p520l.bin");
            out = new FileOutputStream(m_fwpath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    private void copyFileOrDir(String path) {
        AssetManager assetManager = this.getAssets();
        String assets[] = null;
        try {
            assets = assetManager.list(path);
            if (assets.length == 0) {
                copyFile(path);
            } else {
                String fullPath = "/data/data/" + this.getPackageName() + "/" + path;
                File dir = new File(fullPath);
                if (!dir.exists())
                    dir.mkdir();
                for (int i = 0; i < assets.length; ++i) {
                    copyFileOrDir(path + "/" + assets[i]);
                }
            }
        } catch (IOException ex) {
            Log.e("tag", "I/O Exception", ex);
        }
    }

    private void copyFile(String filename) {
        AssetManager assetManager = this.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            Log.e(tag, "filename: " + filename);
            Log.e(tag, "m_strTablesCopyRoot: " + m_strTablesCopyRoot);
            in = assetManager.open(filename);
            String newFileName = m_strTablesCopyRoot + "/" + filename;
            out = new FileOutputStream(newFileName);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }
    }

    public void printerControl(int what) {

        String actionName = "action" + what;
        Log.i(tag, "printerControl: " + actionName);
        ErrorCode errorCode = null;

        switch (what) {

            //
            // Start background service
            //
            case 1:
                errorCode = serviceConnector.StartService();
                //printerService(actionName, null, errorCode);

                //exec3 = Executors.newSingleThreadScheduledExecutor();
                //exec3.scheduleAtFixedRate(new ClockTask(), 3000, 3000, TimeUnit.MILLISECONDS);
                //exec2 = Executors.newSingleThreadScheduledExecutor();
                //exec2.scheduleAtFixedRate(new ClockTask1(), 3000, 3000, TimeUnit.MILLISECONDS);

                appendInfo(actionName, errorCode);

                break;

            //
            // Stop and destroy background service
            //
            case 2:
                errorCode = serviceConnector.StopService();
                //printerService(actionName, null, errorCode);
                exec3.shutdown();
                exec2.shutdown();
                appendInfo(actionName, errorCode);
                break;

            /* print photo */
            case 3:
                //operatePrinter(Action.USB_PRINT_PHOTOS);
                callActionSelectorDiag(null, printPhotoPathMap, "photoSelect");
                break;

            /* reset printer */
            case 4:
                operatePrinter(Action.USB_COMMAND_RESET_PRINTER);
                break;

            /* clear error and continue job */
            case 5:
                operatePrinter(Action.USB_COMMAND_RESUME_JOB);
                break;

            case 6:
                operatePrinter(Action.USB_COMMAND_UPDATE_FW);
                break;

            case 7:
                operatePrinter(Action.USB_EJECT_PAPER_JAM);
                break;

            case 8:
                operatePrinter(Action.USB_SET_AUTO_POWER_OFF);
                break;
            case 9:
                m_info.setText("");
                m_infoView.setImageBitmap(null);
                break;

            case 10:
                m_serviceInfo.setText(serviceConnector.getHitiServiceStatus());
                break;

            case 11:
                callActionSelectorDiag(printerInfoDiag, null, "Printer information");
                break;

            case 12:
                operatePrinter(Action.USB_CLEAN_PAPER_PATH);
                break;
        }
    }

    void appendInfo(String name, ErrorCode errorCode) {
        if (errorCode != null && errorCode != ErrorCode.ERR_CODE_SUCCESS) {
            StringBuilder bu = new StringBuilder("\n>>>").append(name)
                    .append(": err<0x").append(Integer.toHexString(errorCode.value)).append(" ")
                    .append(errorCode.description).append(">");
            m_info.append(bu.toString());
        }
    }

    static Map<String, Action> printerInfoDiag;
    static Map<String, String> printPhotoPathMap;

    static {
        printerInfoDiag = new LinkedHashMap<>();

        printerInfoDiag.put("Printer status", Action.USB_CHECK_PRINTER_STATUS);
        printerInfoDiag.put("Model name", Action.USB_DEVICE_MODEL_NAME);
        printerInfoDiag.put("Serial number", Action.USB_DEVICE_SERIAL_NUM);
        printerInfoDiag.put("Firmware version", Action.USB_DEVICE_FW_VERSION);
        printerInfoDiag.put("Ribbon information", Action.USB_DEVICE_RIBBON_INFO);
        printerInfoDiag.put("Print count", Action.USB_DEVICE_PRINT_COUNT);
        printerInfoDiag.put("Get Storage ID", Action.USB_GET_STORAGE_ID);
        printerInfoDiag.put("Get Object Number", Action.USB_GET_OBJECT_NUMBER);
        printerInfoDiag.put("Get Object Handle ID", Action.USB_GET_OBJECT_HANDLE_ID);
        printerInfoDiag.put("Get Object Info", Action.USB_GET_OBJECT_INFO);
        printerInfoDiag.put("Get Object data", Action.USB_GET_OBJECT_DATA);

        printPhotoPathMap = new LinkedHashMap<>();

        printPhotoPathMap.put("4x6 , photo1", "photo1");
        printPhotoPathMap.put("photo2", "photo2");
        printPhotoPathMap.put("photo3", "photo3");
        printPhotoPathMap.put("photo4", "photo4");
        printPhotoPathMap.put("4x6 split 2up , photo5", "pic1844x1240");
    }

    void callActionSelectorDiag(final Map<String, Action> map, final Map<String, String> map2, final String title) {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(this);
        builderSingle.setTitle(title);

        final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.select_dialog_singlechoice);

        String[] strArr = new String[1];
        strArr = map != null ? (String[]) (map.keySet().toArray(strArr)) : (String[]) (map2.keySet().toArray(strArr));

        for (String str : strArr) {
            arrayAdapter.add(str);
        }

        builderSingle.setCancelable(true);

        builderSingle.setNegativeButton("cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builderSingle.setAdapter(
                arrayAdapter,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        dialog.dismiss();

                        String name = arrayAdapter.getItem(which);
                        //printerService(name, map.get(name), null);

                        if (map != null) {
                            operatePrinter(map.get(name));
                        }
                        if (map2 != null) {

                            switch (which) {
                                case 4: {
                                    PaperType = 5;
                                }
                                break;

                                default: {
                                    PaperType = 2;
                                }
                                break;

                            }
                            mSelectedPath = map2.get(name);
/*
							SimpleDateFormat formatter1 = new SimpleDateFormat("yyyy-MM-dd");
							Date curDate = new Date(System.currentTimeMillis()) ;
							String str1 = formatter1.format(curDate);
							FileUtility.WriteFile("/storage/emulated/0/Android/data/com.hiti.test/files/Tables" +"/debug_"+str1+"_log", "** Write Debig Test \n");
*/
                            operatePrinter(Action.USB_PRINT_PHOTOS);
                        }

                    }
                });
        builderSingle.show();
    }

    interface IFirmware {
        void updateDone(ErrorCode code);
    }

    public void updateFirmware(final IFirmware listener) {

        new AsyncTask<Object, Void, ErrorCode>() {
            @Override
            protected ErrorCode doInBackground(Object... params) {
                return operation.updateFirmware(m_fwversion, m_fwpath);
            }

            @Override
            protected void onPostExecute(ErrorCode code) {
                if (listener != null) listener.updateDone(code);
            }

        }.execute(this, m_fwpath, m_fwversion);
    }

    /*
     * Printer operator
     */
    void operatePrinter(final Action action) {
        Thread task = new Thread() {

            PrinterJob job = null;
            String ret = null;

            @Override
            public void run() {

                Log.i(tag, "start to do operation.. " + action.name());

                switch (action) {

                    case USB_CHECK_PRINTER_STATUS:
                        operation.m_strTablesRoot = m_strTablesRoot;

                        //operation.m_strTablesRoot = null;
//                        operation.m_strTablesRoot = m_strTablesRoot;

                        job = operation.getPrinterStatus();
                        break;

                    case USB_DEVICE_MODEL_NAME:
                        job = operation.getModelName();
                        break;

                    case USB_DEVICE_SERIAL_NUM:
                        job = operation.getSerialNumber();
                        break;

                    case USB_DEVICE_FW_VERSION:
                        job = operation.getFirmwareVersion();
                        break;

                    case USB_DEVICE_RIBBON_INFO:
                        job = operation.getRibbonInfo();
                        break;

                    case USB_DEVICE_PRINT_COUNT:
                        job = operation.getPrintCount();
                        break;

                    case USB_COMMAND_RESET_PRINTER:
                        job = operation.resetPrinter();
                        break;

                    case USB_COMMAND_RESUME_JOB:
                        job = operation.resumeJob();
                        break;

                    case USB_COMMAND_UPDATE_FW:
//						job.errCode = operation.updateFirmware(m_fwversion, m_fwpath);
                        operation.updateFirmware(m_fwversion, m_fwpath);
//						updateFirmware(new IFirmware() {
//							@Override
//							public void updateDone(ErrorCode code) {
//								job.errCode = code;
//								showResponse(job);
//							}
//						});
                        break;

                    case USB_EJECT_PAPER_JAM:
                        job = operation.ejectPaperJam();
                        break;

                    case USB_CLEAN_PAPER_PATH:
                        job = operation.cleanPaperPath();
                        break;

                    case USB_PRINT_PHOTOS:
                        operation.PRINTCOUNT = PRINTCOUNT;
                        operation.MATTE = MATTE;
                        operation.PRINTMODE = PRINTMODE;
                        operation.PaperType = PaperType;
                        operation.m_strTablesRoot = m_strTablesRoot;
//						Log.e("MainActivity", "mSelectedPath: "+mSelectedPath);
                        job = operation.print(mSelectedPath);
                        operation.m_strTablesRoot = "";
                        break;

                    case USB_SET_AUTO_POWER_OFF:
                        job = operation.setAutoPowerOff((short) 10);
                        break;

                    case USB_GET_STORAGE_ID:
                        job = operation.getStorageID();
                        break;

                    case USB_GET_OBJECT_NUMBER:
//					long lobjectId = Long.parseLong("4",16);
//					job = operation.getObjectNumber(1, (byte)0x02, lobjectId);
                        showDialog(new ICallback() {
                            @Override
                            public void operation(long object_id, byte formatType) {

                                job = operation.getObjectNumber(1, formatType, object_id);
                                showResponse(job);
                            }
                        });
                        break;

                    case USB_GET_OBJECT_HANDLE_ID:
                        long lobjectId = Long.parseLong("-1", 16);
                        job = operation.getObjectHandleId(1, (byte) 0x02, lobjectId);
                        showResponse(job);
//					showDialog(new ICallback() {
//						@Override
//						public void operation(long object_id, byte formatType) {
//
//							job = operation.getObjectHandleId(1, formatType, object_id);
//							showResponse(job);
//						}
//					});
                        break;

                    case USB_GET_OBJECT_INFO:
//					lobjectId = Long.parseLong("16",16);
//					job = operation.getObjectInfo(1, lobjectId);
                        showDialog(new ICallback() {
                            @Override
                            public void operation(long object_id, byte formatType) {

                                job = operation.getObjectInfo(1, object_id);
                                showResponse(job);
                            }
                        });
                        break;
                    case USB_GET_OBJECT_DATA:

                        showDialog(new ICallback() {
                            @Override
                            public void operation(long object_id, byte formatType) {

                                job = operation.getObjectData(1, object_id, formatType);
                                showResponse(job);
                            }
                        });
//					lobjectId = Long.parseLong("17",16);
//					job = operation.getObjectData(1, lobjectId, (byte)0x02);
                        break;

                    default:
                }

                if (action != Action.USB_GET_OBJECT_NUMBER && action != Action.USB_GET_OBJECT_INFO
                        && action != Action.USB_GET_OBJECT_HANDLE_ID && action != Action.USB_GET_OBJECT_DATA
                        && action != Action.USB_COMMAND_UPDATE_FW)
                    showResponse(job);
            }
        };

        task.start();
    }

    void showResponse(PrinterJob job) {

        final String ret = retrieveData(job);

        // show result to text
        runOnUiThread(new Runnable() {
            public void run() {
                m_info.append(ret);
            }

        });
    }

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
        //
        // Disconnect USB Service Connector
        // It is used to release service connector or resource will not be released.
        //
        serviceConnector.unregister();
        //解除绑定服务
        unbindService(serviceConnection);
        super.onDestroy();
    }

    //==================================================================================================//
    // Return data
    //==================================================================================================//

    /**
     * Return data format.
     *
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * |  Name of action            		   |  Action meaning       	   	  | Data type     | Data number |    data[0]    |    data[1]    |    data[2]    |    data[3]    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_PRINT_PHOTOS      	           | Print photo        	      |   -----       |    -----    |	   -----    |	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_COMMAND_RESET_PRINTER  		   | Reset printer 			      |   -----       |    -----    |	   -----    |	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_COMMAND_RESUME_JOB     		   | Clear error and resume job   |   -----       |    -----    |	   -----    |	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_CHECK_PRINTER_STATUS   		   | Printer status               | PrinterStatus | 1 object    | Printer status|	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_DEVICE_MODEL_NAME      		   | Printer model name           | String        | 1 string    | model name	|	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_DEVICE_SERIAL_NUM      		   | Manufacture serial number    | String        | 1 string    | serial number |	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_DEVICE_FW_VERSION      		   | Printer firmware version     | String        | 1 string    | FW version    |	   -----    |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_DEVICE_RIBBON_INFO     		   | Current ribbon information   | IntArray      | 2 integer   | Ribbon type   | Remain count  |	   -----    |	   -----    |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     * | USB_DEVICE_PRINT_COUNT     		   | Number of printed sheets     | IntArray      | 4 integer   | Total         | 4x6           | 5x7           | 6x8           |
     * +------------------------------------+------------------------------+---------------+-------------+---------------+---------------+---------------+---------------+
     */


    /**
     * Ribbon type.
     * <p>
     * +-----------------------------+------------+
     * |  Ribbon type                |  Value     |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_YMCKO      | 0          |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_K          | 1 		 |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_KO         | 3          |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_YMCKOK     | 4          |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_HALF_YMCKO | 5          |
     * +-----------------------------+------------+
     * | HITI_RIBBON_TYPE_YMCKFO     | 12         |
     * +-----------------------------+------------+
     */

    /*
     * According to parameter to retrieve and parsing return data
     *
     * @param job
     * @return
     */
    private String retrieveData(PrinterJob job) {

        Log.e(tag, "job.action.name: " + job.action.name());

        StringBuilder bu = new StringBuilder("\n\n<<<");

        /** get action name, job id */
        bu.append(job.action.name()).append(" -ID").append(job.getId())

                /** get error code */
                .append(" : err <0x").append(Integer.toHexString(job.errCode.value)).append(" ")
                .append(job.errCode.description).append(">");

        if (job.retData == null) {
            Log.e(tag, "no data");
            return bu.toString();
        }

        /** parsing return data */
        switch (job.action) {

            //
            // no return data
            //

            /* print photo */
            case USB_PRINT_PHOTOS:
                /* reset printer */
            case USB_COMMAND_RESET_PRINTER:
                /* clear error and continue job */
            case USB_COMMAND_RESUME_JOB:
            case USB_EJECT_PAPER_JAM:
                break;

            //
            // Check printer status
            //	printer status return data  ------>         1 PrinterStatus type object. [printer status]
            case USB_CHECK_PRINTER_STATUS:
                PrinterStatus status = ((PrinterStatus) job.retData);
                bu.append("\nStatus: 0x").append(Integer.toHexString(status.statusValue)).append(" ")
                        .append(status.statusDescription);
                break;


            //
            // return string format.
            //
            case USB_DEVICE_MODEL_NAME:
            case USB_DEVICE_SERIAL_NUM:
            case USB_DEVICE_FW_VERSION:
            case USB_GET_STORAGE_ID:
            case USB_GET_OBJECT_NUMBER:

                /* get return data , string */
                if (job.retData instanceof String) {
                    if (job.retData != null)
                        bu.append("\ndata: ").append((String) job.retData);
//					try
//					{
//						FileWriter fw = new FileWriter(m_strTmpRoot+"/retData", false);
//						BufferedWriter bw = new BufferedWriter(fw);
//						bw.write((String)job.retData);
//						bw.close();
//					}
//					catch (Exception e)
//					{
//						e.printStackTrace();
//					}
                }

                break;
            case USB_GET_OBJECT_INFO:
                if (job.errCode.equals(ErrorCode.ERR_CODE_SUCCESS)) {
                    byte[] data = ((byte[]) job.retData);
                    int[] date = ByteUtility.getDate(data);
                    for (int time : date) bu.append(time).append(',');

                    String name = ByteUtility.getEncodingName(data);

                    bu.append("\ndata: ").append(name.toString());
                }
                break;
            case USB_GET_OBJECT_DATA:
                String path = GetPhoto(job);
                if (path != null) bu.append("\npath: ").append(path);
                break;
            //
            // Get ribbon information
            //	ribbon information return data  ------>     2 integer values. [Ribbon type, Remain count]
            case USB_DEVICE_RIBBON_INFO:

                //
                // Get Printer print count
                //	Printer print count  return data  ------>     4 integer values. [Total, 4x6, 5x7, 6x8]
            case USB_GET_OBJECT_HANDLE_ID:
            case USB_DEVICE_PRINT_COUNT:

                if (job.retData instanceof JniData.IntArray) {
                    for (int i = 0; i < ((JniData.IntArray) job.retData).getSize(); i++) {
                        bu.append("\ndata[").append(i).append("]: ")
                                .append(((JniData.IntArray) job.retData).get(i));
                    }
                }

                break;

            default:
                break;

        }

        Log.e(tag, "bu.toString(): " + bu.toString());
        return bu.toString();
    }


    String GetPhoto(PrinterJob job) {
        if (job.retData instanceof byte[]) {

            final String path = ByteUtility.byteToFile(this, (byte[]) job.retData, MobileInfo.GetDateStamp() + MobileInfo.GetHmsSStamp() + ".jpg");

            if (FileUtility.FileExist(path)) {
                Map sides = BitmapMonitor.GetPhotoTwoSide(this, Uri.fromFile(new File(path)));
                BitmapMonitorResult bmr = new BitmapMonitorResult();
                if (sides != null) {
                    int oriWidth = (int) sides.get("Width");
                    int oriHeight = (int) sides.get("Height");

                    DisplayMetrics dm = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(dm);
                    int width = (int) dm.density * 300;
                    int height = (int) (width * ((float) oriHeight / oriWidth));
                    bmr = BitmapMonitor.CreateCroppedBitmapNew(this, Uri.fromFile(new File(path)), width, height);
                }
                final BitmapMonitorResult photo = bmr;
                if (photo.IsSuccess())
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            m_infoView.setImageBitmap(photo.GetBitmap());
                        }
                    });
                else
                    return null;
            }

            return path;
        }

        return null;
    }


    public interface ICallback {

        void operation(long object_id, byte formatType);
    }

    class Dialog extends AlertDialog {

        Context context;
        EditText idEditView, formatEditView;
        LinearLayout view;
        AlertDialog.Builder builder;

        protected Dialog(Context context) {
            super(context);
            this.context = context;
            view = new LinearLayout(context);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(300, 80, 1);
            view.setLayoutParams(params);
            idEditView = new EditText(context);
            idEditView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            idEditView.setHint("objectId");
            formatEditView = new EditText(context);
            formatEditView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            formatEditView.setHint("format");

            view.addView(idEditView);
            view.addView(formatEditView);
            builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Light_Dialog);
            builder.setView(view);
        }


        public void build(final ICallback callback) {

            builder.setPositiveButton("OK", new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    String idText = idEditView.getText().toString();
                    String formatEdit = formatEditView.getText().toString();
                    final long id = idText.isEmpty() ? 0 : Long.parseLong(idText, 16);
                    final byte format = formatEdit.isEmpty() ? 2 : Byte.decode(formatEdit);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {

                            callback.operation(id, format);
                        }
                    }).start();
                    dialog.dismiss();
                }
            }).show();
        }
    }

    void showDialog(final ICallback callback) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                new Dialog(MainActivity.this).build(callback);
            }
        });
    }

    void showSimpleAlertDialog(final String message) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Service")
                        .setMessage(message)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                dialog.dismiss();
                            }

                        }).show();
            }
        });
    }
}
