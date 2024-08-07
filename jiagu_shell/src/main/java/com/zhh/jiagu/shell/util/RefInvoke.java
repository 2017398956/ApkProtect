package com.zhh.jiagu.shell.util;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RefInvoke {

    /**
     * 调用静态方法
     *
     * @param class_name  所在 class 名称
     * @param method_name 方法名
     * @param pareType    方法参数类型
     * @param pareValues  方法参数值
     * @return 方法返回值
     */
    public static Object invokeStaticMethod(String class_name, String method_name, Class[] pareType, Object[] pareValues) {
        try {
            Class<?> obj_class = Class.forName(class_name);
            Method method = obj_class.getDeclaredMethod(method_name, pareType);
            method.setAccessible(true);
            return method.invoke(null, pareValues);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
        return null;
    }

    public static Object invokeMethod(String class_name, String method_name, Object obj, Class[] pareType, Object[] pareValues) {

        try {
            Class<?> obj_class = Class.forName(class_name);
            //获取类中的所有方法，但不包括继承父类的方法
            Method method = obj_class.getDeclaredMethod(method_name, pareType);
            method.setAccessible(true);
            return method.invoke(obj, pareValues);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
        return null;

    }

    public static Object getField(String class_name, Object obj, String fieldName) {
        try {
            Class<?> obj_class = Class.forName(class_name);
            Field field = obj_class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
        return null;
    }

    public static Object getStaticField(String class_name, String filedName) {
        try {
            Class<?> obj_class = Class.forName(class_name);
            Field field = obj_class.getDeclaredField(filedName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
        return null;
    }

    public static void setField(String classname, String filedName, Object obj, Object fieldValue) {
        try {
            Class<?> obj_class = Class.forName(classname);
            Field field = obj_class.getDeclaredField(filedName);
            field.setAccessible(true);
            field.set(obj, fieldValue);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
    }

    public static void setStaticField(String class_name, String fieldName, Object fieldValue) {
        try {
            Class<?> obj_class = Class.forName(class_name);
            Field field = obj_class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, fieldValue);
        } catch (Exception e) {
            LogUtil.error(Log.getStackTraceString(e));
        }
    }

}