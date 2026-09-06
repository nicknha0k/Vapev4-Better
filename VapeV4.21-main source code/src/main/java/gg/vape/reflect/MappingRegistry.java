package gg.vape.reflect;

import gg.vape.wrapper.impl.ForgeVersion;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class MappingRegistry {
    public static final Map<String, Set<String>> METHODS = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> METHODS_REVERSED = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> FIELDS = new HashMap<String, Set<String>>();
    public static final Map<String, Set<String>> FIELDS_REVERSED = new HashMap<String, Set<String>>();
    private static final String RESOURCE_DIRECTORY = getResourceDirectory();
    private static final String METHODS_RESOURCE = RESOURCE_DIRECTORY + "/methods.csv";
    private static final String FIELDS_RESOURCE = RESOURCE_DIRECTORY + "/fields.csv";

    private MappingRegistry() {
    }

    private static String getResourceDirectory() {
        switch (ForgeVersion.c()) {
            case 13:
                return "mappings/forge1710";
            case 15:
                return "mappings/forge189";
            case 23:
                return "mappings/forge1122";
            case 35:
                return "mappings/forge1164";
            case 36:
                return "mappings/forge1165";
            default:
                return "mappings/forge189";
        }
    }

    public static void parseCsv(InputStream stream, Map<String, Set<String>> namesToSrg, Map<String, Set<String>> srgToNames) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream));){
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                String srgName = columns[0];
                String readableName = columns[1];
                namesToSrg.computeIfAbsent(readableName, ignored -> new HashSet()).add(srgName);
                srgToNames.computeIfAbsent(srgName, ignored -> new HashSet()).add(readableName);
            }
        }
    }

    public static boolean matches(Method method, String requestedName) {
        return MappingRegistry.matches(method.getName(), requestedName, METHODS, METHODS_REVERSED);
    }

    public static boolean matches(Field field, String requestedName) {
        return MappingRegistry.matches(field.getName(), requestedName, FIELDS, FIELDS_REVERSED);
    }

    private static boolean matches(String actualName, String requestedName, Map<String, Set<String>> namesToSrg, Map<String, Set<String>> srgToNames) {
        return actualName.equals(requestedName) || namesToSrg.getOrDefault(requestedName, Collections.emptySet()).contains(actualName) || srgToNames.getOrDefault(requestedName, Collections.emptySet()).contains(actualName);
    }

    private static InputStream openRequiredResource(String resourcePath) throws IOException {
        InputStream stream = MappingRegistry.class.getResourceAsStream('/' + resourcePath);
        if (stream == null) {
            throw new FileNotFoundException("Missing mapping resource: " + resourcePath);
        }
        return stream;
    }

    static {
        try {
            MappingRegistry.parseCsv(MappingRegistry.openRequiredResource(METHODS_RESOURCE), METHODS, METHODS_REVERSED);
            MappingRegistry.parseCsv(MappingRegistry.openRequiredResource(FIELDS_RESOURCE), FIELDS, FIELDS_REVERSED);
        }
        catch (Throwable failure) {
            failure.printStackTrace();
            throw new RuntimeException();
        }
    }
}
