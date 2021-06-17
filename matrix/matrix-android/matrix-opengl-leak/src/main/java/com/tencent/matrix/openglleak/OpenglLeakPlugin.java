package com.tencent.matrix.openglleak;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.tencent.matrix.openglleak.comm.FuncNameString;
import com.tencent.matrix.openglleak.detector.IOpenglIndexDetector;
import com.tencent.matrix.openglleak.hook.OpenGLHook;
import com.tencent.matrix.openglleak.hook.OpenGLInfo;
import com.tencent.matrix.openglleak.listener.LeakMonitor;
import com.tencent.matrix.openglleak.utils.EGLHelper;
import com.tencent.matrix.openglleak.utils.OpenGLLeakMonitorLog;
import com.tencent.matrix.plugin.Plugin;
import com.tencent.matrix.plugin.PluginListener;

import com.tencent.matrix.openglleak.detector.OpenglIndexDetectorService;

import java.util.Map;

public class OpenglLeakPlugin extends Plugin {

    private static final String TAG = "matrix.OpenglLeakPlugin";

    private Context context;

    public OpenglLeakPlugin(Context c) {
        context = c;
    }

    @Override
    public void init(Application app, PluginListener listener) {
        super.init(app, listener);
    }

    @Override
    public void start() {
        super.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                _start();
            }
        }).start();
    }

    public void _start() {
        Intent service = new Intent(context, OpenglIndexDetectorService.class);
        context.bindService(service, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                OpenGLLeakMonitorLog.i(TAG, "onServiceConnected");
                IOpenglIndexDetector ipc = IOpenglIndexDetector.Stub.asInterface(iBinder);

                try {
                    // 通过实验进程获取 index
                    Map<String, Integer> map = ipc.seekIndex();
                    if (map == null) {
                        OpenGLLeakMonitorLog.e(TAG, "indexMap null");
                        return;
                    }
                    OpenGLLeakMonitorLog.i(TAG, "indexMap:" + map);

                    // 初始化
                    EGLHelper.initOpenGL();
                    OpenGLHook.getInstance().init();
                    OpenGLLeakMonitorLog.i(TAG, "init env");

                    // hook
                    OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_TEXTURES, map.get(FuncNameString.GL_GEN_TEXTURES));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_TEXTURES, map.get(FuncNameString.GL_DELETE_TEXTURES));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_BUFFERS, map.get(FuncNameString.GL_GEN_BUFFERS));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_BUFFERS, map.get(FuncNameString.GL_DELETE_BUFFERS));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_FRAMEBUFFERS, map.get(FuncNameString.GL_GEN_FRAMEBUFFERS));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_FRAMEBUFFERS, map.get(FuncNameString.GL_DELETE_FRAMEBUFFERS));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_GEN_RENDERBUFFERS, map.get(FuncNameString.GL_GEN_RENDERBUFFERS));
                    OpenGLHook.getInstance().hook(FuncNameString.GL_DELETE_RENDERBUFFERS, map.get(FuncNameString.GL_DELETE_RENDERBUFFERS));
                    OpenGLLeakMonitorLog.i(TAG, "hook finish");

                    // 泄漏监控
                    LeakMonitor.getInstance().start((Application) context.getApplicationContext());
                    LeakMonitor.getInstance().setListener(new LeakMonitor.LeakListener() {
                        @Override
                        public void onMaybeLeak(OpenGLInfo info) {
                            Log.i("opdeng", "onMaybeLeak:" + info);
                        }

                        @Override
                        public void onLeak(OpenGLInfo info) {
                            Log.i("opdeng", "onLeak:" + info);
                        }
                    });
                } catch (RemoteException e) {
                    e.printStackTrace();
                } finally {
                    // 销毁实验进程
                    try {
                        OpenGLLeakMonitorLog.i(TAG, "destory test process");
                        ipc.destory();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                context.unbindService(this);
            }
        }, context.BIND_AUTO_CREATE);
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public String getTag() {
        return "OpenglLeak";
    }

}
