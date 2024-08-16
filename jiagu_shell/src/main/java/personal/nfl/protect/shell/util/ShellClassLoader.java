package personal.nfl.protect.shell.util;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;

public class ShellClassLoader extends DexClassLoader {

    public int cookie;
    public ClassLoader classLoader;

    public ShellClassLoader(String dexPath, String optimizedDirectory, String librarySearchPath, ClassLoader parent, byte[] dexBytes) {
        super(dexPath, optimizedDirectory, librarySearchPath, parent);
        this.classLoader = parent;
        cookie = ShellNativeMethod.loadDexFile(dexBytes, dexBytes.length);
        LogUtil.info("cookie:" + cookie);
    }

    private String[] getClassNameList(int flag) {
        return (String[]) RefInvoke.invokeStaticMethod(DexFile.class.getName(), "getClassNameList", new Class[]{int.class}, new Object[]{flag});
    }

    private Class defineClass(String name, ClassLoader cl, int cookie) {
        return (Class) RefInvoke.invokeStaticMethod(DexFile.class.getName(), "defineClassNative", new Class[]{String.class, ClassLoader.class, int.class, DexFile.class}, new Object[]{name, cl, cookie, this});
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        int i;
        Class<?> cls = null;
        String[] classnameList = getClassNameList(cookie);
        for (i = 0; i < classnameList.length; i++) {
            if (name.equals(classnameList[i])) {
                cls = defineClass(classnameList[i].replace('.', '/'), classLoader, cookie);
            } else {
                defineClass(classnameList[i].replace('.', '/'), classLoader, cookie);
            }
        }
        if (cls == null) {
            cls = super.findClass(name);
        }
        return cls;
    }

    @Override
    protected Class<?> loadClass(String classname, boolean resolve) throws ClassNotFoundException {
        try {
            return defineClass(classname, classLoader, cookie);
        } catch (Exception e) {
            LogUtil.error(e.getLocalizedMessage());
            return super.loadClass(classname, resolve);
        }
    }
}
