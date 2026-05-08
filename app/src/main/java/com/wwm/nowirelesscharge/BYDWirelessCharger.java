package com.wwm.nowirelesscharge;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BYDWirelessCharger {
    private static final String TAG = "NoWirelessCharge";

    static public void turnOn(Context context)  { toggle(1, context); }
    static public void turnOff(Context context) { toggle(2, context); }

    static private void toggle(int state, Context context) {
        try {
            allowHiddenApi();

            Class<?> deviceClass = Class.forName("android.hardware.bydauto.charging.BYDAutoChargingDevice");
            Object device = getOrCreateDevice(deviceClass, context);
            int mDeviceType = getDeviceTypeId(deviceClass, device);
            int chargingSwitchSet = getWirelessChargeSwitchSetFeatureId();

            Class<?> absAutoClass = Class.forName("android.hardware.bydauto.AbsBYDAutoDevice");
            Method setMethod    = absAutoClass.getDeclaredMethod("set", int.class, int.class, int.class);
            setMethod.setAccessible(true);
            setMethod.invoke(device, mDeviceType, chargingSwitchSet, state);

            Log.i(TAG, "ok: wireless charging switch state=" + state + " (1=on 2=off)");
        } catch (Throwable t) {
            Log.e(TAG, "failed: state=" + state + " — 升级系统后常见原因为 framework 内类名/方法签名变更，请把本条完整异常发出来或对照新车机 framework.jar 反编译修改反射路径", t);
        }
    }

    /** 新版本固件一般为 getDeviceType；旧 ROM 曾为 getDevicetype。 */
    static private Object getOrCreateDevice(Class<?> deviceClass, Context context) throws Exception {
        try {
            Method m = deviceClass.getMethod("getInstance", Context.class);
            return m.invoke(null, context);
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getMethod("getInstance");
            return m.invoke(null);
        }
    }

    static private int getDeviceTypeId(Class<?> deviceClass, Object device) throws Exception {
        Method m;
        try {
            m = deviceClass.getMethod("getDeviceType");
        } catch (NoSuchMethodException e) {
            m = deviceClass.getMethod("getDevicetype");
        }
        return (int) m.invoke(device);
    }

    /**
     * Di3 13.1.x 起常量类常见为 charging 包内的 BYDAutoChargingDeviceFeatureIds；
     * 旧版为 BYDAutoFeatureIds。
     */
    static private int getWirelessChargeSwitchSetFeatureId() throws Exception {
        String[][] candidates = new String[][]{
                {"android.hardware.bydauto.charging.BYDAutoChargingDeviceFeatureIds", "CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET"},
                {"android.hardware.bydauto.BYDAutoFeatureIds", "CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET"},
        };
        Exception last = null;
        for (String[] pair : candidates) {
            try {
                Class<?> c = Class.forName(pair[0]);
                Field f = c.getDeclaredField(pair[1]);
                f.setAccessible(true);
                return (int) f.get(null);
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("no feature id resolver");
    }

    /** 允许访问隐藏 API；失败时同样会进入 toggle 的 catch。 */
    static private void allowHiddenApi() throws Exception {
        Method forName                = Class.class.getDeclaredMethod("forName", String.class);
        Method getDeclaredMethod      = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
        Class  vmRuntimeClass         = (Class) forName.invoke(null, "dalvik.system.VMRuntime");
        Method getRuntime             = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
        Object vmRuntime              = getRuntime.invoke(null);
        Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "setHiddenApiExemptions", new Class[] {String[].class});
        setHiddenApiExemptions.invoke(vmRuntime, (Object) new String[]{"L"});
    }
}
