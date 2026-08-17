#include <jni.h>
#include <string>

#include "s2_export_api.h"

static s2::Pipeline* g_pipeline = nullptr;
static s2::GenerateParams* g_params = nullptr;

static void throwRuntimeException(JNIEnv* env, const char* message) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, message);
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aris_assistant_S2Native_nativeInitialize(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath,
        jstring tokenizerPath) {

    if (modelPath == nullptr || tokenizerPath == nullptr) {
        throwRuntimeException(env, "Model or tokenizer path is null");
        return JNI_FALSE;
    }

    const char* model = env->GetStringUTFChars(modelPath, nullptr);
    const char* tokenizer = env->GetStringUTFChars(tokenizerPath, nullptr);

    if (g_pipeline != nullptr) {
        ReleaseS2Pipeline(g_pipeline);
        g_pipeline = nullptr;
    }

    if (g_params != nullptr) {
        ReleaseS2GenerateParams(g_params);
        g_params = nullptr;
    }

    g_pipeline = AllocS2Pipeline();

    if (g_pipeline == nullptr) {
        env->ReleaseStringUTFChars(modelPath, model);
        env->ReleaseStringUTFChars(tokenizerPath, tokenizer);
        throwRuntimeException(env, "AllocS2Pipeline failed");
        return JNI_FALSE;
    }

    // CPU backend.
    // gpu_device = 0
    // backend_type = 0
    // n_gpu_layers = 0
    // codec_follow_backend = 0
    const int result = InitializeS2PipelineFromFiles(
            g_pipeline,
            model,
            tokenizer,
            0,
            0,
            0,
            0
    );

    env->ReleaseStringUTFChars(modelPath, model);
    env->ReleaseStringUTFChars(tokenizerPath, tokenizer);

    if (result != 0) {
        ReleaseS2Pipeline(g_pipeline);
        g_pipeline = nullptr;

        throwRuntimeException(
                env,
                "InitializeS2PipelineFromFiles failed"
        );

        return JNI_FALSE;
    }

    g_params = AllocS2GenerateParams();

    if (g_params == nullptr) {
        ReleaseS2Pipeline(g_pipeline);
        g_pipeline = nullptr;

        throwRuntimeException(env, "AllocS2GenerateParams failed");
        return JNI_FALSE;
    }

    const int paramsResult = InitializeS2GenerateParams(
            g_params,
            -1,     // max_new_tokens
            -1.0f,  // temperature
            -1.0f,  // top_p
            -1,     // top_k
            -1,     // min_tokens_before_end
            4,      // n_threads
            1       // verbose
    );

    if (paramsResult != 0) {
        ReleaseS2GenerateParams(g_params);
        g_params = nullptr;

        ReleaseS2Pipeline(g_pipeline);
        g_pipeline = nullptr;

        throwRuntimeException(
                env,
                "InitializeS2GenerateParams failed"
        );

        return JNI_FALSE;
    }

    return JNI_TRUE;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aris_assistant_S2Native_nativeSynthesize(
        JNIEnv* env,
        jobject /* thiz */,
        jstring text,
        jstring outputPath) {

    if (g_pipeline == nullptr || g_params == nullptr) {
        throwRuntimeException(
                env,
                "S2 pipeline is not initialized"
        );
        return JNI_FALSE;
    }

    if (text == nullptr || outputPath == nullptr) {
        throwRuntimeException(
                env,
                "Text or output path is null"
        );
        return JNI_FALSE;
    }

    const char* textChars =
            env->GetStringUTFChars(text, nullptr);

    const char* outputChars =
            env->GetStringUTFChars(outputPath, nullptr);

    std::vector<float>* audioBuffer =
            AllocS2AudioBuffer(0);

    if (audioBuffer == nullptr) {
        env->ReleaseStringUTFChars(text, textChars);
        env->ReleaseStringUTFChars(outputPath, outputChars);

        throwRuntimeException(
                env,
                "AllocS2AudioBuffer failed"
        );

        return JNI_FALSE;
    }

    int32_t outputLength = 0;

    const int result = S2Synthesize(
            g_pipeline,
            g_params,
            audioBuffer,
            nullptr,        // reference prompt codes
            nullptr,        // reference TPrompt
            nullptr,        // reference audio path
            nullptr,        // reference transcript
            textChars,
            outputChars,
            &outputLength
    );

    env->ReleaseStringUTFChars(text, textChars);
    env->ReleaseStringUTFChars(outputPath, outputChars);

    ReleaseS2AudioBuffer(audioBuffer);

    if (result != 0) {
        throwRuntimeException(
                env,
                "S2Synthesize failed"
        );

        return JNI_FALSE;
    }

    return JNI_TRUE;
}


extern "C"
JNIEXPORT void JNICALL
Java_com_aris_assistant_S2Native_nativeRelease(
        JNIEnv* /* env */,
jobject /* thiz */) {

if (g_params != nullptr) {
ReleaseS2GenerateParams(g_params);
g_params = nullptr;
}

if (g_pipeline != nullptr) {
ReleaseS2Pipeline(g_pipeline);
g_pipeline = nullptr;
}
}