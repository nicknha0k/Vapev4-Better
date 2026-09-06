#include "vape421_native.h"
#include "loader_bootstrap.h"

#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

static volatile LONG g_loaded_by_jni = 0;

#define VAPE421_PRODUCT_JAR_RESOURCE_ID 421

static int module_directory(wchar_t *output, size_t capacity) {
    DWORD length;
    wchar_t *separator;
    if (output == NULL || capacity == 0 || g_module == NULL) {
        return 0;
    }
    length = GetModuleFileNameW(g_module, output, (DWORD)capacity);
    if (length == 0 || length >= capacity) {
        return 0;
    }
    separator = wcsrchr(output, L'\\');
    if (separator == NULL) {
        return 0;
    }
    *separator = L'\0';
    return 1;
}

void vape_log(const wchar_t *format, ...) {
    wchar_t message[2048];
    wchar_t line[2304];
    wchar_t directory[MAX_PATH];
    wchar_t log_path[MAX_PATH];
    SYSTEMTIME now;
    FILE *file = NULL;
    va_list arguments;

    va_start(arguments, format);
    _vsnwprintf_s(message, sizeof(message) / sizeof(message[0]),
            _TRUNCATE, format, arguments);
    va_end(arguments);
    GetLocalTime(&now);
    _snwprintf_s(line, sizeof(line) / sizeof(line[0]), _TRUNCATE,
            L"[%04u-%02u-%02u %02u:%02u:%02u.%03u] %ls\r\n",
            now.wYear, now.wMonth, now.wDay, now.wHour, now.wMinute,
            now.wSecond, now.wMilliseconds, message);
    OutputDebugStringW(line);

    if (!module_directory(directory, sizeof(directory) / sizeof(directory[0]))) {
        return;
    }
    _snwprintf_s(log_path, sizeof(log_path) / sizeof(log_path[0]), _TRUNCATE,
            L"%ls\\vape421-native.log", directory);
    if (_wfopen_s(&file, log_path, L"a, ccs=UTF-8") == 0 && file != NULL) {
        fputws(line, file);
        fclose(file);
    }
}

void vape_log_pending_exception(JNIEnv *env, const wchar_t *context) {
    jthrowable throwable;
    jclass throwable_class;
    jmethodID to_string;
    jstring text;
    const jchar *characters;
    jsize length;
    wchar_t buffer[1024];
    if (env == NULL || !(*env)->ExceptionCheck(env)) {
        vape_log(L"%ls failed without a Java exception", context);
        return;
    }
    throwable = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    throwable_class = (*env)->FindClass(env, "java/lang/Throwable");
    to_string = throwable_class == NULL ? NULL : (*env)->GetMethodID(
            env, throwable_class, "toString", "()Ljava/lang/String;");
    text = to_string == NULL ? NULL : (jstring)(*env)->CallObjectMethod(
            env, throwable, to_string);
    if (text == NULL || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        vape_log(L"%ls raised an unreadable Java exception", context);
        return;
    }
    characters = (*env)->GetStringChars(env, text, NULL);
    length = (*env)->GetStringLength(env, text);
    if (characters != NULL) {
        size_t copied = (size_t)length < 1023 ? (size_t)length : 1023;
        memcpy(buffer, characters, copied * sizeof(wchar_t));
        buffer[copied] = L'\0';
        (*env)->ReleaseStringChars(env, text, characters);
        vape_log(L"%ls: %ls", context, buffer);
    }
}

static jstring new_wide_string(JNIEnv *env, const wchar_t *value) {
    if (value == NULL) {
        return NULL;
    }
    return (*env)->NewString(env, (const jchar *)value, (jsize)wcslen(value));
}

static int materialize_embedded_product_jar(
        wchar_t *jar_path, size_t jar_capacity) {
    HRSRC resource;
    HGLOBAL loaded_resource;
    const unsigned char *bytes;
    DWORD size;
    wchar_t temp_root[MAX_PATH];
    wchar_t temp_directory[MAX_PATH];
    HANDLE file = INVALID_HANDLE_VALUE;
    DWORD offset = 0;
    int result = 0;

    resource = FindResourceW(g_module,
            MAKEINTRESOURCEW(VAPE421_PRODUCT_JAR_RESOURCE_ID),
            MAKEINTRESOURCEW(10));
    if (resource == NULL) {
        vape_log(L"embedded product JAR resource %d is missing",
                VAPE421_PRODUCT_JAR_RESOURCE_ID);
        return 0;
    }
    size = SizeofResource(g_module, resource);
    loaded_resource = LoadResource(g_module, resource);
    bytes = loaded_resource == NULL ? NULL
            : (const unsigned char *)LockResource(loaded_resource);
    if (bytes == NULL || size < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
        vape_log(L"embedded product JAR resource is invalid");
        return 0;
    }
    if (GetTempPathW((DWORD)(sizeof(temp_root) / sizeof(temp_root[0])),
            temp_root) == 0) {
        vape_log(L"GetTempPathW failed: %lu", GetLastError());
        return 0;
    }
    _snwprintf_s(temp_directory,
            sizeof(temp_directory) / sizeof(temp_directory[0]), _TRUNCATE,
            L"%lsVape421Recovery", temp_root);
    if (!CreateDirectoryW(temp_directory, NULL)
            && GetLastError() != ERROR_ALREADY_EXISTS) {
        vape_log(L"CreateDirectoryW failed: %lu", GetLastError());
        return 0;
    }
    if (_snwprintf_s(jar_path, jar_capacity, _TRUNCATE,
            L"%ls\\vape421-product-%lu.jar", temp_directory,
            GetCurrentProcessId()) < 0) {
        vape_log(L"temporary product JAR path is too long");
        return 0;
    }
    file = CreateFileW(jar_path, GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_DELETE, NULL, CREATE_ALWAYS,
            FILE_ATTRIBUTE_TEMPORARY, NULL);
    if (file == INVALID_HANDLE_VALUE) {
        vape_log(L"CreateFileW for embedded product JAR failed: %lu",
                GetLastError());
        return 0;
    }
    while (offset < size) {
        DWORD written = 0;
        DWORD remaining = size - offset;
        if (!WriteFile(file, bytes + offset, remaining, &written, NULL)
                || written == 0) {
            vape_log(L"WriteFile for embedded product JAR failed: %lu",
                    GetLastError());
            goto cleanup;
        }
        offset += written;
    }
    if (!FlushFileBuffers(file)) {
        vape_log(L"FlushFileBuffers for embedded product JAR failed: %lu",
                GetLastError());
        goto cleanup;
    }
    result = 1;

cleanup:
    CloseHandle(file);
    if (!result) {
        DeleteFileW(jar_path);
    } else {
        vape_log(L"materialized embedded product JAR: %ls (%lu bytes)",
                jar_path, size);
    }
    return result;
}

static jobject find_client_class_loader(JNIEnv *env) {
    jint thread_count = 0;
    jthread *threads = NULL;
    jobject result = NULL;
    jvmtiError error;
    jint index;
    if (g_jvmti == NULL) {
        return NULL;
    }
    error = (*g_jvmti)->GetAllThreads(g_jvmti, &thread_count, &threads);
    if (error != JVMTI_ERROR_NONE || threads == NULL) {
        vape_log(L"GetAllThreads failed: %d", error);
        return NULL;
    }
    for (index = 0; index < thread_count; ++index) {
        jvmtiThreadInfo info;
        memset(&info, 0, sizeof(info));
        if ((*g_jvmti)->GetThreadInfo(g_jvmti, threads[index], &info)
                == JVMTI_ERROR_NONE) {
            if (info.name != NULL
                    && (strcmp(info.name, "Client thread") == 0
                            || strcmp(info.name, "Render thread") == 0)
                    && info.context_class_loader != NULL) {
                result = (*env)->NewLocalRef(env, info.context_class_loader);
            }
            if (info.name != NULL) {
                (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)info.name);
            }
            if (info.thread_group != NULL) {
                (*env)->DeleteLocalRef(env, info.thread_group);
            }
            if (info.context_class_loader != NULL) {
                (*env)->DeleteLocalRef(env, info.context_class_loader);
            }
        }
        (*env)->DeleteLocalRef(env, threads[index]);
        if (result != NULL) {
            break;
        }
    }
    (*g_jvmti)->Deallocate(g_jvmti, (unsigned char *)threads);
    return result;
}

/*
 * ModLauncher 10.2+ defines game classes in ModuleClassLoader, which cannot
 * see an ordinary child URLClassLoader. Add only the product packages to its
 * package routing table so transformed game classes and the bootstrap use the
 * same gg.vape class identities without replacing Forge's global fallback.
 *
 * Returns 1 when installed, 0 when the loader is not modular, and -1
 * when a compatible loader was detected but setup failed.
 */
static int install_modular_payload_loader(
        JNIEnv *env, jobject *loader, jclass url_loader_class,
        jclass url_class, jobject url) {
    jclass runtime_loader_class;
    jclass loader_class;
    jclass payload_loader_class;
    jclass target_event_class;
    jclass payload_event_class;
    jfieldID package_routes_field;
    jmethodID url_loader_init;
    jmethodID load_class;
    jmethodID payload_loader_init;
    jmethodID build_package_routes;
    jmethodID modular_loader_marker;
    jobjectArray urls;
    jobject bootstrap_loader;
    jobject payload_loader;
    jobject previous_package_routes;
    jobject package_routes;
    jstring payload_loader_name;
    jstring target_event_name;

    runtime_loader_class = (*env)->GetObjectClass(env, *loader);
    if (runtime_loader_class == NULL) {
        vape_log_pending_exception(env, L"resolve runtime ClassLoader type");
        return -1;
    }
    package_routes_field = (*env)->GetFieldID(env, runtime_loader_class,
            "packageToParentLoader", "Ljava/util/Map;");
    if (package_routes_field == NULL) {
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        modular_loader_marker = (*env)->GetMethodID(env, runtime_loader_class,
                "setFallbackClassLoader", "(Ljava/lang/ClassLoader;)V");
        if (modular_loader_marker != NULL) {
            vape_log(L"ModLauncher package routing field is unavailable");
            return -1;
        }
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
        }
        return 0;
    }

    loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (loader_class == NULL || url_loader_init == NULL
            || load_class == NULL || urls == NULL) {
        vape_log_pending_exception(env,
                L"resolve modular payload ClassLoader methods");
        return -1;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);

    /* Load the custom ClassLoader type before installing package routes. */
    bootstrap_loader = (*env)->NewObject(env, url_loader_class,
            url_loader_init, urls, *loader);
    payload_loader_name = (*env)->NewStringUTF(env,
            "gg.vape.runtime.ForgePayloadClassLoader");
    payload_loader_class = bootstrap_loader == NULL
            || payload_loader_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, bootstrap_loader,
                    load_class, payload_loader_name);
    if (payload_loader_class == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"load ForgePayloadClassLoader bootstrap class");
        return -1;
    }

    payload_loader_init = (*env)->GetMethodID(env, payload_loader_class,
            "<init>", "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    build_package_routes = (*env)->GetMethodID(env, payload_loader_class,
            "buildPackageRoutingMap", "(Ljava/util/Map;)Ljava/util/Map;");
    payload_loader = payload_loader_init == NULL ? NULL
            : (*env)->NewObject(env, payload_loader_class,
                    payload_loader_init, urls, *loader);
    if (build_package_routes == NULL || payload_loader == NULL
            || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create modular payload ClassLoader");
        return -1;
    }

    previous_package_routes = (*env)->GetObjectField(
            env, *loader, package_routes_field);
    package_routes = (jobject)(*env)->CallObjectMethod(env, payload_loader,
            build_package_routes, previous_package_routes);
    if (package_routes == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"build ModLauncher payload package routes");
        return -1;
    }
    (*env)->SetObjectField(env, *loader, package_routes_field, package_routes);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"install ModLauncher payload package routes");
        return -1;
    }

    /* Fail before any JVMTI redefine if the exact crashing reference is hidden. */
    target_event_name = (*env)->NewStringUTF(env,
            "gg.vape.event.impl.EventRenderWorldPassExecutorDrain");
    target_event_class = target_event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, *loader,
                    load_class, target_event_name);
    if (target_event_class == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env,
                L"resolve payload event from ModuleClassLoader");
        (*env)->SetObjectField(env, *loader, package_routes_field,
                previous_package_routes);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env,
                    L"restore ModLauncher package routes after visibility failure");
        }
        return -1;
    }
    payload_event_class = target_event_name == NULL ? NULL
            : (jclass)(*env)->CallObjectMethod(env, payload_loader,
                    load_class, target_event_name);
    if (payload_event_class == NULL || (*env)->ExceptionCheck(env)
            || !(*env)->IsSameObject(env, target_event_class,
                    payload_event_class)) {
        vape_log_pending_exception(env,
                L"verify modular payload visibility");
        (*env)->SetObjectField(env, *loader, package_routes_field,
                previous_package_routes);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env,
                    L"restore ModLauncher package routes after identity failure");
        }
        return -1;
    }

    (*env)->DeleteLocalRef(env, *loader);
    *loader = payload_loader;
    vape_log(L"installed payload package routes on ModLauncher ModuleClassLoader");
    return 1;
}

static int add_jar_to_loader(
        JNIEnv *env, jobject *loader, const wchar_t *jar_path) {
    jclass url_loader_class;
    jclass url_class;
    jclass file_class;
    jclass uri_class;
    jmethodID add_url;
    jmethodID url_loader_init;
    jmethodID file_init;
    jmethodID to_uri;
    jmethodID to_url;
    jstring path;
    jobject file;
    jobject uri;
    jobject url;
    jobjectArray urls;
    jobject child_loader;
    jclass runtime_loader_class;
    jfieldID delegated_loader_field;
    jobject delegated_loader;
    int modular_result;

    url_loader_class = (*env)->FindClass(env, "java/net/URLClassLoader");
    url_class = (*env)->FindClass(env, "java/net/URL");
    if (url_loader_class == NULL || url_class == NULL) {
        vape_log_pending_exception(env, L"resolve URLClassLoader classes");
        return 0;
    }
    file_class = (*env)->FindClass(env, "java/io/File");
    uri_class = (*env)->FindClass(env, "java/net/URI");
    file_init = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "<init>", "(Ljava/lang/String;)V");
    to_uri = file_class == NULL ? NULL : (*env)->GetMethodID(
            env, file_class, "toURI", "()Ljava/net/URI;");
    to_url = uri_class == NULL ? NULL : (*env)->GetMethodID(
            env, uri_class, "toURL", "()Ljava/net/URL;");
    if (file_init == NULL || to_uri == NULL || to_url == NULL) {
        vape_log_pending_exception(env, L"resolve product JAR URL methods");
        return 0;
    }
    path = new_wide_string(env, jar_path);
    file = path == NULL ? NULL : (*env)->NewObject(env, file_class, file_init, path);
    uri = file == NULL ? NULL : (*env)->CallObjectMethod(env, file, to_uri);
    url = uri == NULL ? NULL : (*env)->CallObjectMethod(env, uri, to_url);
    if (url == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create product JAR URL");
        return 0;
    }
    if ((*env)->IsInstanceOf(env, *loader, url_loader_class)) {
        add_url = (*env)->GetMethodID(env, url_loader_class,
                "addURL", "(Ljava/net/URL;)V");
        if (add_url == NULL) {
            vape_log_pending_exception(env, L"resolve URLClassLoader.addURL");
            return 0;
        }
        (*env)->CallVoidMethod(env, *loader, add_url, url);
        if ((*env)->ExceptionCheck(env)) {
            vape_log_pending_exception(env, L"URLClassLoader.addURL");
            return 0;
        }
        return 1;
    }

    runtime_loader_class = (*env)->GetObjectClass(env, *loader);
    delegated_loader_field = runtime_loader_class == NULL ? NULL
            : (*env)->GetFieldID(env, runtime_loader_class,
                    "delegatedClassLoader",
                    "Lcpw/mods/modlauncher/TransformingClassLoader$DelegatedClassLoader;");
    if (delegated_loader_field != NULL) {
        delegated_loader = (*env)->GetObjectField(
                env, *loader, delegated_loader_field);
        if (delegated_loader != NULL
                && (*env)->IsInstanceOf(env, delegated_loader,
                        url_loader_class)) {
            add_url = (*env)->GetMethodID(env, url_loader_class,
                    "addURL", "(Ljava/net/URL;)V");
            if (add_url == NULL) {
                vape_log_pending_exception(env,
                        L"resolve delegated URLClassLoader.addURL");
                return 0;
            }
            (*env)->CallVoidMethod(env, delegated_loader, add_url, url);
            if ((*env)->ExceptionCheck(env)) {
                vape_log_pending_exception(env,
                        L"delegated URLClassLoader.addURL");
                return 0;
            }
            vape_log(L"appended product JAR to ModLauncher delegated ClassLoader");
            return 1;
        }
    } else if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
    }

    modular_result = install_modular_payload_loader(
            env, loader, url_loader_class, url_class, url);
    if (modular_result != 0) {
        return modular_result > 0;
    }

    url_loader_init = (*env)->GetMethodID(env, url_loader_class, "<init>",
            "([Ljava/net/URL;Ljava/lang/ClassLoader;)V");
    urls = (*env)->NewObjectArray(env, 1, url_class, NULL);
    if (url_loader_init == NULL || urls == NULL) {
        vape_log_pending_exception(env, L"resolve child URLClassLoader constructor");
        return 0;
    }
    (*env)->SetObjectArrayElement(env, urls, 0, url);
    child_loader = (*env)->NewObject(env, url_loader_class, url_loader_init,
            urls, *loader);
    if (child_loader == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"create child product URLClassLoader");
        return 0;
    }
    (*env)->DeleteLocalRef(env, *loader);
    *loader = child_loader;
    vape_log(L"created child URLClassLoader for non-URL Minecraft ClassLoader");
    return 1;
}

static jclass load_bridge_class(JNIEnv *env, jobject loader) {
    jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
    jmethodID load_class = loader_class == NULL ? NULL : (*env)->GetMethodID(
            env, loader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    jstring name = (*env)->NewStringUTF(env, "gg.vape.runtime.NativeBridge");
    jclass result;
    if (load_class == NULL || name == NULL) {
        return NULL;
    }
    result = (jclass)(*env)->CallObjectMethod(env, loader, load_class, name);
    if (result == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"load gg.vape.runtime.NativeBridge");
        return NULL;
    }
    return result;
}

static int set_current_context_class_loader(JNIEnv *env, jobject loader) {
    jclass thread_class = (*env)->FindClass(env, "java/lang/Thread");
    jmethodID current_thread;
    jmethodID set_context_loader;
    jobject thread;
    if (thread_class == NULL) {
        vape_log_pending_exception(env, L"resolve java.lang.Thread");
        return 0;
    }
    current_thread = (*env)->GetStaticMethodID(env, thread_class,
            "currentThread", "()Ljava/lang/Thread;");
    set_context_loader = (*env)->GetMethodID(env, thread_class,
            "setContextClassLoader", "(Ljava/lang/ClassLoader;)V");
    if (current_thread == NULL || set_context_loader == NULL) {
        vape_log_pending_exception(env, L"resolve Thread context ClassLoader methods");
        return 0;
    }
    thread = (*env)->CallStaticObjectMethod(env, thread_class, current_thread);
    if (thread == NULL || (*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.currentThread");
        return 0;
    }
    (*env)->CallVoidMethod(env, thread, set_context_loader, loader);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"Thread.setContextClassLoader");
        return 0;
    }
    return 1;
}

static int pin_native_module(void) {
    HMODULE pinned = NULL;
    if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS
                    | GET_MODULE_HANDLE_EX_FLAG_PIN,
            (LPCWSTR)(const void *)&g_module, &pinned)) {
        vape_log(L"GetModuleHandleExW(PIN) failed: %lu", GetLastError());
        return 0;
    }
    return 1;
}

/*
 * Launchers without Forge (e.g. Badlion) defeat gmv() version detection and
 * environment overrides may not propagate through the launcher process tree.
 * Guarantee the documented 1.8.9 fallback (minor 15) unless the user already
 * defined vape421.forgeMinor. Non-fatal: the Java side validates the value.
 */
static void ensure_forge_minor_fallback(JNIEnv *env) {
    jclass system_class;
    jmethodID get_property;
    jmethodID set_property;
    jstring key;
    jstring current;
    jstring fallback;

    system_class = (*env)->FindClass(env, "java/lang/System");
    if (system_class == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    get_property = (*env)->GetStaticMethodID(env, system_class,
            "getProperty", "(Ljava/lang/String;)Ljava/lang/String;");
    set_property = (*env)->GetStaticMethodID(env, system_class,
            "setProperty",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (get_property == NULL || set_property == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    key = (*env)->NewStringUTF(env, "vape421.forgeMinor");
    if (key == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    current = (jstring)(*env)->CallStaticObjectMethod(
            env, system_class, get_property, key);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return;
    }
    if (current != NULL) {
        return;
    }
    fallback = (*env)->NewStringUTF(env, "15");
    if (fallback == NULL) {
        (*env)->ExceptionClear(env);
        return;
    }
    (*env)->CallStaticObjectMethod(env, system_class, set_property,
            key, fallback);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return;
    }
    vape_log(L"vape421.forgeMinor was unset; defaulted to 15 (MC 1.8.9)");
}

static int call_bridge_start(JNIEnv *env, jclass bridge_class) {
    jmethodID start = (*env)->GetStaticMethodID(env, bridge_class, "start", "()V");
    if (start == NULL) {
        vape_log_pending_exception(env, L"resolve NativeBridge.start");
        return 0;
    }
    (*env)->CallStaticVoidMethod(env, bridge_class, start);
    if ((*env)->ExceptionCheck(env)) {
        vape_log_pending_exception(env, L"NativeBridge.start");
        return 0;
    }
    return 1;
}

static DWORD WINAPI bootstrap_thread(LPVOID parameter) {
    HMODULE jvm_module;
    FARPROC created_vms_address;
    typedef jint (JNICALL *get_created_vms_fn)(JavaVM **, jsize, jsize *);
    get_created_vms_fn get_created_vms;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jsize vm_count = 0;
    jobject loader = NULL;
    jclass bridge_class = NULL;
    wchar_t jar_path[MAX_PATH];
    int attached = 0;
    int registered = 0;
    int completed = 0;
    int attempt;
    HMODULE worker_module = (HMODULE)parameter;
    DWORD exit_code = 1;

    if (!vape_loader_bootstrap_initialize()) {
        vape_log(L"Loader token bootstrap is invalid");
        exit_code = 6;
        goto cleanup;
    }
    Sleep(150);
    if (InterlockedCompareExchange(&g_loaded_by_jni, 0, 0) != 0) {
        return 0;
    }
    for (attempt = 0; attempt < 600; ++attempt) {
        jvm_module = GetModuleHandleW(L"jvm.dll");
        if (jvm_module != NULL) break;
        Sleep(100);
    }
    if (jvm_module == NULL) {
        vape_log(L"jvm.dll is not loaded");
        exit_code = 2;
        goto cleanup;
    }
    created_vms_address = GetProcAddress(jvm_module, "JNI_GetCreatedJavaVMs");
    if (created_vms_address == NULL) {
        vape_log(L"JNI_GetCreatedJavaVMs export is unavailable");
        exit_code = 3;
        goto cleanup;
    }
    get_created_vms = (get_created_vms_fn)created_vms_address;
    for (attempt = 0; attempt < 600; ++attempt) {
        if (get_created_vms(&vm, 1, &vm_count) == JNI_OK
                && vm != NULL && vm_count >= 1) {
            break;
        }
        vm = NULL;
        vm_count = 0;
        Sleep(100);
    }
    if (vm == NULL || vm_count < 1) {
        vape_log(L"JNI_GetCreatedJavaVMs returned no VM");
        exit_code = 4;
        goto cleanup;
    }
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, NULL) != JNI_OK
            || env == NULL) {
        vape_log(L"AttachCurrentThreadAsDaemon failed");
        exit_code = 5;
        goto cleanup;
    }
    attached = 1;
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        goto cleanup;
    }
    if (!materialize_embedded_product_jar(jar_path,
            sizeof(jar_path) / sizeof(jar_path[0]))) {
        goto cleanup;
    }
    for (attempt = 0; attempt < 600 && loader == NULL; ++attempt) {
        loader = find_client_class_loader(env);
        if (loader == NULL) {
            Sleep(100);
        }
    }
    if (loader == NULL) {
        vape_log(L"Minecraft client/render thread was not found within 60 seconds");
        goto cleanup;
    }
    if (!add_jar_to_loader(env, &loader, jar_path)) {
        goto cleanup;
    }
    if (!set_current_context_class_loader(env, loader)) {
        goto cleanup;
    }
    bridge_class = load_bridge_class(env, loader);
    if (bridge_class == NULL) {
        goto cleanup;
    }
    if (vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        goto cleanup;
    }
    registered = 1;
    if (!pin_native_module()) {
        goto cleanup;
    }
    vape_log(L"NativeBridge linked from %ls", jar_path);
    ensure_forge_minor_fallback(env);
    if (!call_bridge_start(env, bridge_class)) {
        goto cleanup;
    }
    vape_loader_report_completed();
    vape_log(L"NativeBridge.start completed; injection is active");
    completed = 1;
    exit_code = 0;

cleanup:
    if (!completed) {
        char failure[96];
        _snprintf_s(failure, sizeof(failure), _TRUNCATE,
                "Native bootstrap failed with code %lu", exit_code);
        vape_loader_report_failure(failure);
    }
    if (attached) {
        (*vm)->DetachCurrentThread(vm);
    }
    if (!registered && worker_module != NULL) {
        vape_log(L"bootstrap failed before native registration; unloading DLL");
        FreeLibraryAndExitThread(worker_module, exit_code);
    }
    if (!completed) {
        vape_log(L"bootstrap did not complete; DLL remains loaded because natives may be registered");
    }
    return exit_code;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jclass bridge_class;
    (void)reserved;
    InterlockedExchange(&g_loaded_by_jni, 1);
    if (!vape_loader_bootstrap_initialize()) {
        return JNI_ERR;
    }
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK
            || env == NULL) {
        return JNI_ERR;
    }
    if (vape_initialize_jvmti(vm) != JNI_OK) {
        return JNI_ERR;
    }
    bridge_class = (*env)->FindClass(env, "gg/vape/runtime/NativeBridge");
    if (bridge_class == NULL
            || vape_register_native_bridge(env, bridge_class) != JNI_OK) {
        vape_log_pending_exception(env, L"JNI_OnLoad NativeBridge registration");
        return JNI_ERR;
    }
    vape_log(L"JNI_OnLoad completed");
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    (void)reserved;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_8) == JNI_OK) {
        vape_release_native_bridge(env);
    }
    vape_loader_bootstrap_clear();
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    HANDLE thread;
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = instance;
        DisableThreadLibraryCalls(instance);
        thread = CreateThread(NULL, 0, bootstrap_thread, instance, 0, NULL);
        if (thread != NULL) {
            CloseHandle(thread);
        }
    } else if (reason == DLL_PROCESS_DETACH) {
        vape_loader_bootstrap_clear();
    }
    return TRUE;
}
