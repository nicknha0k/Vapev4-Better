package gg.vape.runtime;

import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

public final class ForgePayloadClassLoaderProbe {
    private static final String EVENT_CLASS =
            "gg.vape.event.impl.EventRenderWorldPassExecutorDrain";

    private ForgePayloadClassLoaderProbe() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("expected injection JAR path");
        }

        URL payloadUrl = new File(arguments[0]).toURI().toURL();
        PackageRoutingClassLoader forgeLoader = new PackageRoutingClassLoader();
        ForgePayloadClassLoader payloadLoader = new ForgePayloadClassLoader(
                new URL[]{payloadUrl}, forgeLoader);
        Map<String, ClassLoader> initialRoutes = Collections.singletonMap(
                "existing.route", forgeLoader);
        Map<String, ClassLoader> routes =
                payloadLoader.buildPackageRoutingMap(initialRoutes);
        forgeLoader.setRoutes(routes);

        require(routes.get("existing.route") == forgeLoader,
                "existing Forge package route was not preserved");
        require(routes.get("gg.vape.event.impl") == payloadLoader,
                "event package was not routed to the payload loader");
        require(routes.get("func.skidline") == payloadLoader,
                "auxiliary payload package was not routed to the payload loader");
        require(!routes.containsKey("com.google.gson"),
                "shared dependency package was unexpectedly rerouted");

        Class<?> eventClass = Class.forName(EVENT_CLASS, true, forgeLoader);
        require(eventClass.getClassLoader() == payloadLoader,
                "Forge parent did not resolve the event from the payload loader");
        Object event = eventClass.getConstructor(Float.TYPE).newInstance(0.0f);
        require(event != null, "event construction failed");

        Class<?> bundledDependency = payloadLoader.loadClass("com.google.gson.Gson");
        require(bundledDependency.getClassLoader() == payloadLoader,
                "payload did not resolve a bundled dependency after a parent miss");

        try {
            forgeLoader.loadClass(EVENT_CLASS + "Missing");
            throw new AssertionError("missing payload class unexpectedly resolved");
        }
        catch (ClassNotFoundException expected) {
            // A clean miss proves that child-first payload routing does not recurse.
        }

        require(forgeLoader.loadClass("java.lang.String") == String.class,
                "unrelated parent class loading was changed by payload routing");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class PackageRoutingClassLoader extends ClassLoader {
        private Map<String, ClassLoader> routes = Collections.emptyMap();

        private PackageRoutingClassLoader() {
            super(null);
        }

        private void setRoutes(Map<String, ClassLoader> routes) {
            this.routes = routes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (this.getClassLoadingLock(name)) {
                Class<?> loaded = this.findLoadedClass(name);
                if (loaded == null) {
                    int separator = name.lastIndexOf('.');
                    String packageName = separator < 0 ? "" : name.substring(0, separator);
                    ClassLoader route = this.routes.get(packageName);
                    loaded = route == null ? super.loadClass(name, false)
                            : route.loadClass(name);
                }
                if (resolve) {
                    this.resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}
