package com.wwm.nowirelesscharge;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class BYDWirelessCharger {
    private static final String TAG = "NoWirelessCharge";

    /** Matches SystemUI WireChargeItem / framework BYDAutoChargingDevice. */
    private static final String CHARGING_DEVICE_CLASS =
            "android.hardware.bydauto.charging.BYDAutoChargingDevice";

    static public void turnOn(Context context)  { toggle(1, context); }
    static public void turnOff(Context context) { toggle(2, context); }

    static private void toggle(int state, Context context) {
        tryEnableHiddenApiExemption();

        Context ctx = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;

        Throwable[] firstFailure = new Throwable[1];

        // Prefer AbsBYDAutoDevice#set (whitelist) over public blacklist wrapper.
        if (invokeLegacySet(state, ctx, firstFailure)) {
            return;
        }
        if (invokeDirectSwitchSetter(state, ctx, firstFailure)) {
            return;
        }

        String msg = "Failed to toggle wireless charging. state=" + state
                + " (1=on, 2=off — same as BYD CHARGE_WIRELESS_CHARGING_*)";
        if (firstFailure[0] != null) {
            Log.e(TAG, msg, firstFailure[0]);
        } else {
            Log.e(TAG, msg);
        }
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

    static private Method resolveGetInstance(Class<?> deviceClass) throws NoSuchMethodException {
        try {
            Method m = deviceClass.getMethod("getInstance", Context.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getDeclaredMethod("getInstance", Context.class);
            m.setAccessible(true);
            return m;
        }
    }

    static private Method resolveSetWirelessChargingSwitchState(Class<?> deviceClass)
            throws NoSuchMethodException {
        try {
            Method m = deviceClass.getDeclaredMethod("setWirelessChargingSwitchState", int.class);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getMethod("setWirelessChargingSwitchState", int.class);
            m.setAccessible(true);
            return m;
        }
    }

    static private boolean invokeDirectSwitchSetter(int state, Context context, Throwable[] firstFailure) {
        try {
            Class<?> deviceClass = Class.forName(CHARGING_DEVICE_CLASS);
            Method getInstance = resolveGetInstance(deviceClass);
            Object device = getInstance.invoke(null, context);
            Method setWirelessChargingSwitchState = resolveSetWirelessChargingSwitchState(deviceClass);
            Object ret = setWirelessChargingSwitchState.invoke(device, state);

            if (ret instanceof Integer) {
                int result = (Integer) ret;
                Log.i(TAG, "Direct setter result=" + result + ", state=" + state);
            } else {
                Log.i(TAG, "Direct setter invoked, state=" + state);
            }
            return true;
        } catch (Throwable t) {
            if (firstFailure[0] == null) {
                firstFailure[0] = unwrap(t);
            }
            return false;
        }
    }

    static private Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException) {
            Throwable c = t.getCause();
            return c != null ? c : t;
        }
        return t;
    }

    static private Method resolveGetDevicetype(Class<?> deviceClass) throws NoSuchMethodException {
        try {
            return deviceClass.getMethod("getDevicetype");
        } catch (NoSuchMethodException e) {
            Method m = deviceClass.getDeclaredMethod("getDevicetype");
            m.setAccessible(true);
            return m;
        }
    }

    static private boolean invokeLegacySet(int state, Context context, Throwable[] firstFailure) {
        try {
            Class<?> deviceClass = Class.forName(CHARGING_DEVICE_CLASS);
            Method getInstance = resolveGetInstance(deviceClass);
            Object device = getInstance.invoke(null, context);

            Method getDeviceTypeMethod = resolveGetDevicetype(deviceClass);
            int mDeviceType = (int) getDeviceTypeMethod.invoke(device);

            Class<?> featureIdsClass = Class.forName("android.hardware.bydauto.BYDAutoFeatureIds");
            Field chargingSwitchSetField =
                    featureIdsClass.getField("CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET");
            int chargingSwitchSet = chargingSwitchSetField.getInt(null);

            Class<?> absAutoClass = Class.forName("android.hardware.bydauto.AbsBYDAutoDevice");
            Method setMethod = absAutoClass.getDeclaredMethod("set", int.class, int.class, int.class);
            setMethod.setAccessible(true);
            Object ret = setMethod.invoke(device, mDeviceType, chargingSwitchSet, state);

            Log.i(TAG, "Legacy set result=" + ret + ", state=" + state);
            return true;
        } catch (Throwable t) {
            if (firstFailure[0] == null) {
                firstFailure[0] = unwrap(t);
            }
            return false;
        }
    }
}
