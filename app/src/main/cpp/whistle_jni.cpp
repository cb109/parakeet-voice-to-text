#include <jni.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "needle.h"

namespace {

// The engine is process-global and not thread-safe.
std::mutex g_lock;
bool g_loaded = false;

void throwRuntime(JNIEnv* env, const std::string& message) {
    env->ThrowNew(env->FindClass("java/lang/RuntimeException"), message.c_str());
}

std::string lastError(const char* fallback) {
    const char* error = needle_last_error();
    return (error && *error) ? error : fallback;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_example_whistle_Whistle_nativeLoad(JNIEnv* env, jobject, jstring jpath) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (g_loaded) return;

    const char* path = env->GetStringUTFChars(jpath, nullptr);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    env->ReleaseStringUTFChars(jpath, path);
    if (fd < 0) {
        throwRuntime(env, "Cannot open model file");
        return;
    }
    struct stat st {};
    if (fstat(fd, &st) != 0 || st.st_size <= 0) {
        close(fd);
        throwRuntime(env, "Cannot stat model file");
        return;
    }
    // The engine reads the .cact in place, so the mapping is kept for the life of the process.
    void* data = mmap(nullptr, st.st_size, PROT_READ | PROT_WRITE, MAP_PRIVATE, fd, 0);
    close(fd);
    if (data == MAP_FAILED) {
        throwRuntime(env, "Cannot map model file");
        return;
    }
    if (needle_load(static_cast<const unsigned char*>(data), static_cast<unsigned long long>(st.st_size)) < 0) {
        std::string error = lastError("needle_load failed");
        munmap(data, st.st_size);
        throwRuntime(env, error);
        return;
    }
    g_loaded = true;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_whistle_Whistle_nativeTranscribe(JNIEnv* env, jobject, jfloatArray jpcm, jint offset, jint length) {
    std::lock_guard<std::mutex> lock(g_lock);
    if (!g_loaded) {
        throwRuntime(env, "Model not loaded");
        return nullptr;
    }

    std::vector<float> pcm(length);
    env->GetFloatArrayRegion(jpcm, offset, length, pcm.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<char> out(256 * 1024, 0);
    int rc = needle_transcribe(pcm.data(), length, nullptr, nullptr, 0, out.data(), static_cast<int>(out.size()));
    if (rc < 0) {
        throwRuntime(env, lastError("needle_transcribe failed"));
        return nullptr;
    }

    // Returned as bytes: NewStringUTF expects modified UTF-8, the engine emits standard UTF-8.
    jsize n = static_cast<jsize>(strnlen(out.data(), out.size()));
    jbyteArray result = env->NewByteArray(n);
    env->SetByteArrayRegion(result, 0, n, reinterpret_cast<const jbyte*>(out.data()));
    return result;
}
