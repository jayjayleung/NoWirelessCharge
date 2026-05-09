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
        // Hidden API exemption should be best-effort only.
        tryEnableHiddenApiExemption();

        // Preferred path: same method used by SystemUI quick setting item.
        if (invokeDirectSwitchSetter(state, context, "android.hardware.bydauto.charging.BYDAutoChargingDevice")) {
            return;
        }
        if (invokeDirectSwitchSetter(state, context, "android.hardware.bydauto.BYDAutoChargingDevice")) {
            return;
        }

        // Fallback path: legacy generic set(deviceType, featureId, state).
        if (invokeLegacySet(state, context, "android.hardware.bydauto.charging.BYDAutoChargingDevice")) {
            return;
        }
        if (invokeLegacySet(state, context, "android.hardware.bydauto.BYDAutoChargingDevice")) {
            return;
        }

        Log.e(TAG, "Failed to toggle wireless charging. state=" + state);
    }

    static private void tryEnableHiddenApiExemption() {
        try {
            Method forName = Class.class.getDeclaredMethod("forName", String.class);
            Method getDeclaredMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
            Class vmRuntimeClass = (Class) forName.invoke(null, "dalvik.system.VMRuntime");
            Method getRuntime = (Method) getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null);
            Object vmRuntime = getRuntime.invoke(null);
            Method setHiddenApiExemptions = (Method) getDeclaredMethod.invoke(
                    vmRuntimeClass, "setHiddenApiExemptions", new Class[]{String[].class});
            setHiddenApiExemptions.invoke(vmRuntime, new String[][]{new String[]{"L"}});
        } catch (Throwable ignored) {
            // Newer systems may block this; do not stop normal reflection flow.
        }
    }

    static private boolean invokeDirectSwitchSetter(int state, Context context, String className) {
        try {
            Class deviceClass = Class.forName(className);
            Method getInstance = deviceClass.getMethod("getInstance", Context.class);
            Object device = getInstance.invoke(null, context);
            Method setWirelessChargingSwitchState = deviceClass.getMethod("setWirelessChargingSwitchState", int.class);
            Object ret = setWirelessChargingSwitchState.invoke(device, state);

            if (ret instanceof Integer) {
                int result = (Integer) ret;
                Log.i(TAG, "Direct setter result=" + result + ", class=" + className + ", state=" + state);
            } else {
                Log.i(TAG, "Direct setter invoked, class=" + className + ", state=" + state);
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static private boolean invokeLegacySet(int state, Context context, String className) {
        try {
            Class deviceClass = Class.forName(className);
            Method getInstance = deviceClass.getMethod("getInstance", Context.class);
            Object device = getInstance.invoke(null, context);

            Method getDeviceTypeMethod = deviceClass.getMethod("getDevicetype");
            int mDeviceType = (int) getDeviceTypeMethod.invoke(device);

            Class featureIdsClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
            Field chargingSwitchSetField = featureIdsClass.getDeclaredField("CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET");
            int chargingSwitchSet = (int) chargingSwitchSetField.get(null);

            Class absAutoClass = Class.forName("android.hardware.bydauto.AbsBYDAutoDevice");
            Method setMethod = absAutoClass.getDeclaredMethod("set", int.class, int.class, int.class);
            setMethod.setAccessible(true);
            Object ret = setMethod.invoke(device, mDeviceType, chargingSwitchSet, state);

            Log.i(TAG, "Legacy set result=" + ret + ", class=" + className + ", state=" + state);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
