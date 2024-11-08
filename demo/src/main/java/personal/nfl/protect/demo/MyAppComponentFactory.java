package personal.nfl.protect.demo;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.CoreComponentFactory;

public class MyAppComponentFactory extends CoreComponentFactory {

    @NonNull
    @Override
    public Application instantiateApplication(@NonNull ClassLoader cl, @NonNull String className) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Log.d("MyAppComponentFactory", "className:" + className);
        return super.instantiateApplication(cl, className);
    }
}
