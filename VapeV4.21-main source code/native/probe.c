// probe.c — DLL de diagnóstico: lista classes carregadas e membros.
// Uso: Vape421Injector.exe <pid> probe.dll  →  gera probe.log ao lado da DLL.
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <jni.h>
#include <jvmti.h>
#include <stdio.h>
#include <string.h>

static HMODULE g_mod = NULL;

static void log_path(wchar_t *out, size_t cap) {
    wchar_t *sep;
    GetModuleFileNameW(g_mod, out, (DWORD)cap);
    sep = wcsrchr(out, L'\\');
    if (sep) wcscpy(sep + 1, L"probe.log");
}

static void log_text(const char *text) {
    wchar_t path[MAX_PATH];
    FILE *f = NULL;
    log_path(path, MAX_PATH);
    if (_wfopen_s(&f, path, L"a") == 0 && f) {
        fputs(text, f);
        fputc('\n', f);
        fclose(f);
    }
}

static void log_members(JNIEnv *env, jclass target, const char *sig) {
    char buf[1024];
    jclass class_class = (*env)->FindClass(env, "java/lang/Class");
    jmethodID get_methods, get_fields;
    jobjectArray marr = NULL, farr = NULL;
    jsize i, n;
    if (!class_class) { (*env)->ExceptionClear(env); return; }
    get_methods = (*env)->GetMethodID(env, class_class,
        "getDeclaredMethods", "()[Ljava/lang/reflect/Method;");
    get_fields = (*env)->GetMethodID(env, class_class,
        "getDeclaredFields", "()[Ljava/lang/reflect/Field;");
    if (!get_methods || !get_fields) { (*env)->ExceptionClear(env); return; }
    marr = (jobjectArray)(*env)->CallObjectMethod(env, target, get_methods);
    farr = (jobjectArray)(*env)->CallObjectMethod(env, target, get_fields);
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); return; }
    {
        jclass mclass = (*env)->FindClass(env, "java/lang/reflect/Method");
        jmethodID tostr = mclass ? (*env)->GetMethodID(env, mclass, "toString", "()Ljava/lang/String;") : NULL;
        n = marr ? (*env)->GetArrayLength(env, marr) : 0;
        _snprintf_s(buf, sizeof(buf), _TRUNCATE, "== %s methods=%d", sig, (int)n);
        log_text(buf);
        for (i = 0; i < n && tostr; i++) {
            jobject m = (*env)->GetObjectArrayElement(env, marr, i);
            jstring s = (jstring)(*env)->CallObjectMethod(env, m, tostr);
            const char *c = s ? (*env)->GetStringUTFChars(env, s, NULL) : NULL;
            if (c) {
                _snprintf_s(buf, sizeof(buf), _TRUNCATE, "  M %s", c);
                log_text(buf);
                (*env)->ReleaseStringUTFChars(env, s, c);
            }
        }
    }
    {
        jclass fclass = (*env)->FindClass(env, "java/lang/reflect/Field");
        jmethodID tostr = fclass ? (*env)->GetMethodID(env, fclass, "toString", "()Ljava/lang/String;") : NULL;
        n = farr ? (*env)->GetArrayLength(env, farr) : 0;
        _snprintf_s(buf, sizeof(buf), _TRUNCATE, "== %s fields=%d", sig, (int)n);
        log_text(buf);
        for (i = 0; i < n && tostr; i++) {
            jobject m = (*env)->GetObjectArrayElement(env, farr, i);
            jstring s = (jstring)(*env)->CallObjectMethod(env, m, tostr);
            const char *c = s ? (*env)->GetStringUTFChars(env, s, NULL) : NULL;
            if (c) {
                _snprintf_s(buf, sizeof(buf), _TRUNCATE, "  F %s", c);
                log_text(buf);
                (*env)->ReleaseStringUTFChars(env, s, c);
            }
        }
    }
}

static int exact_detail(const char *sig) {
    if (!sig) return 0;
    if (strcmp(sig, "Lnet/minecraft/util/EnumFacing;") == 0) return 1;
    if (strcmp(sig, "Lnet/minecraft/entity/Entity;") == 0) return 1;
    if (strcmp(sig, "Lnet/minecraft/client/Minecraft;") == 0) return 1;
    if (strcmp(sig, "Lpk;") == 0) return 1;
    if (strcmp(sig, "Lpr;") == 0) return 1;
    if (strcmp(sig, "Lave;") == 0) return 1;
    return 0;
}

static int interesting(const char *sig) {
    if (!sig) return 0;
    if (exact_detail(sig)) return 2;
    if (strncmp(sig, "Lnet/minecraft", 14) == 0) return 1;
    return 0;
}

static DWORD WINAPI work(LPVOID p) {
    HMODULE jvm = NULL;
    JavaVM *vm = NULL;
    JNIEnv *env = NULL;
    jvmtiEnv *jvmti = NULL;
    jclass *classes = NULL;
    jint count = 0, i;
    int attempt;
    (void)p;
    Sleep(500);
    for (attempt = 0; attempt < 100 && !jvm; attempt++) {
        jvm = GetModuleHandleW(L"jvm.dll");
        if (!jvm) Sleep(100);
    }
    if (!jvm) { log_text("no jvm.dll"); return 1; }
    {
        typedef jint (JNICALL *fn)(JavaVM**, jsize, jsize*);
        fn g = (fn)GetProcAddress(jvm, "JNI_GetCreatedJavaVMs");
        jsize n = 0;
        if (!g || g(&vm, 1, &n) != JNI_OK || !vm) { log_text("no VM"); return 2; }
    }
    if ((*vm)->AttachCurrentThreadAsDaemon(vm, (void**)&env, NULL) != JNI_OK || !env) {
        log_text("attach failed"); return 3;
    }
    if ((*vm)->GetEnv(vm, (void**)&jvmti, JVMTI_VERSION_1_2) != JNI_OK || !jvmti) {
        log_text("no jvmti"); (*vm)->DetachCurrentThread(vm); return 4;
    }
    if ((*jvmti)->GetLoadedClasses(jvmti, &count, &classes) != JVMTI_ERROR_NONE) {
        log_text("GetLoadedClasses failed"); (*vm)->DetachCurrentThread(vm); return 5;
    }
    {
        char buf[128];
        _snprintf_s(buf, sizeof(buf), _TRUNCATE, "total loaded classes=%d", (int)count);
        log_text(buf);
    }
    {
        int mcp_count = 0;
        int mcp_logged = 0;
        for (i = 0; i < count; i++) {
            char *sig = NULL;
            if ((*jvmti)->GetClassSignature(jvmti, classes[i], &sig, NULL) == JVMTI_ERROR_NONE && sig) {
                int k = interesting(sig);
                if (k == 2) {
                    char buf[512];
                    _snprintf_s(buf, sizeof(buf), _TRUNCATE, "FOUND %s", sig);
                    log_text(buf);
                    log_members(env, classes[i], sig);
                } else if (k == 1) {
                    mcp_count++;
                    if (mcp_logged < 40) {
                        char buf[512];
                        _snprintf_s(buf, sizeof(buf), _TRUNCATE, "MCP %s", sig);
                        log_text(buf);
                        mcp_logged++;
                    }
                }
                (*jvmti)->Deallocate(jvmti, (unsigned char*)sig);
            }
        }
        {
            char buf[128];
            _snprintf_s(buf, sizeof(buf), _TRUNCATE, "total Lnet/minecraft* classes=%d", mcp_count);
            log_text(buf);
        }
    }
    (*jvmti)->Deallocate(jvmti, (unsigned char*)classes);
    log_text("probe done");
    (*vm)->DetachCurrentThread(vm);
    return 0;
}

BOOL WINAPI DllMain(HINSTANCE h, DWORD r, LPVOID x) {
    HANDLE t;
    (void)x;
    if (r == DLL_PROCESS_ATTACH) {
        g_mod = h;
        DisableThreadLibraryCalls(h);
        t = CreateThread(NULL, 0, work, NULL, 0, NULL);
        if (t) CloseHandle(t);
    }
    return TRUE;
}
