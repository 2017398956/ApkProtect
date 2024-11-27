package personal.nfl.protect.shell.util;

import java.util.Map;
import java.util.Set;

public class ARouterHelper {

    public static final String SDK_NAME = "ARouter";
    public static final String TAG = SDK_NAME + "::";
    public static final String SEPARATOR = "$$";
    public static final String SUFFIX_ROOT = "Root";
    public static final String SUFFIX_INTERCEPTORS = "Interceptors";
    public static final String SUFFIX_PROVIDERS = "Providers";
    public static final String SUFFIX_AUTOWIRED = SEPARATOR + SDK_NAME + SEPARATOR + "Autowired";
    public static final String DOT = ".";
    public static final String ROUTE_ROOT_PAKCAGE = "com.alibaba.android.arouter.routes";

    public static final String AROUTER_SP_CACHE_KEY = "SP_AROUTER_CACHE";
    public static final String AROUTER_SP_KEY_MAP = "ROUTER_MAP";

    public static final String LAST_VERSION_NAME = "LAST_VERSION_NAME";
    public static final String LAST_VERSION_CODE = "LAST_VERSION_CODE";

    public static void fixARouter(ClassLoader classLoader, Set<String> classNames) {
        try {
            if (null == classNames || classNames.isEmpty()) {
                LogUtil.debug("ARouter class names is empty.");
                return;
            }
            Class<?> warehouseClass = classLoader.loadClass("com.alibaba.android.arouter.core.Warehouse");
            Object groupsIndexObj = RefInvoke.getStaticField(warehouseClass, "groupsIndex");
            if (null != groupsIndexObj) {
                // Class<? extends IRouteGroup>
                Map<String, Class<?>> groupsIndex = (Map<String, Class<?>>) groupsIndexObj;
                // RouteMeta
                Map<String, Object> providersIndex = (Map<String, Object>) RefInvoke.getStaticField(warehouseClass, "providersIndex");
                // Class<? extends IInterceptor>>
                Map<String, Class<?>> interceptorsIndex = (Map<String, Class<?>>) RefInvoke.getStaticField(warehouseClass, "interceptorsIndex");
                Object routeTemp;
                Class<?> routeClass;
                for (String className : classNames) {
                    className = ROUTE_ROOT_PAKCAGE + DOT + className;
                    routeClass = classLoader.loadClass(className);
                    routeTemp = routeClass.getConstructor().newInstance();
                    if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                        // This one of root elements, load root.
                        RefInvoke.invokeMethod(routeClass, "loadInto", routeTemp, new Class[]{Map.class}, new Object[]{groupsIndex});
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_INTERCEPTORS)) {
                        // Load interceptorMeta
                        RefInvoke.invokeMethod(routeClass, "loadInto", routeTemp, new Class[]{Map.class}, new Object[]{interceptorsIndex});
                    } else if (className.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_PROVIDERS)) {
                        // Load providerIndex
                        RefInvoke.invokeMethod(routeClass, "loadInto", routeTemp, new Class[]{Map.class}, new Object[]{providersIndex});
                    }
                }
            }
        } catch (Exception e) {
            // LogUtil.error(e.getLocalizedMessage());
        }
    }
}
