#include <jni.h>
#include <whisper.h>
#include <mutex>
#include <string>
#include <vector>
#include <algorithm>
#include <fstream>
#include <sched.h>
#include <atomic>
#include <chrono>
#include <memory>
#include <condition_variable>
#include <array>

static std::mutex guard;
struct Model {
    whisper_context *context;
    std::mutex mutex;
    std::condition_variable available;
    std::array<whisper_state *, 5> states{};
    std::array<bool, 5> busy{};
    explicit Model(whisper_context *ctx) : context(ctx) {}
    ~Model() {
        for (auto *state : states) if (state) whisper_free_state(state);
        whisper_free(context);
    }
};
static std::shared_ptr<Model> loaded_model;
static std::string loaded_path;
static std::atomic<long long> cancelled_through{0};
struct InferenceRequest {
    long long id;
    std::chrono::steady_clock::time_point deadline;
    bool cancelled() const { return id <= cancelled_through.load(); }
    bool expired() const { return std::chrono::steady_clock::now() >= deadline; }
};
static bool abort_inference(void *data) {
    auto *request = static_cast<InferenceRequest *>(data);
    return request->cancelled() || request->expired();
}
extern "C" JNIEXPORT void JNICALL
Java_me_arianb_usb_1hid_1client_dictation_WhisperEngine_cancel(JNIEnv *, jobject, jlong id) {
    auto previous = cancelled_through.load();
    while (previous < id && !cancelled_through.compare_exchange_weak(previous, id)) {}
}

static void fail(JNIEnv *env, const char *message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}

// Split the allowed CPU set between concurrent jobs. Restore worker affinity
// afterward so unrelated app work stays unaffected.
struct PerformanceCores {
    cpu_set_t previous{};
    bool changed = false;
    int count = 4;
    PerformanceCores(int concurrency, int slot) {
        if (sched_getaffinity(0, sizeof(previous), &previous) != 0) return;
        long fastest = 0;
        long frequencies[CPU_SETSIZE]{};
        for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
            if (!CPU_ISSET(cpu, &previous)) continue;
            std::ifstream in("/sys/devices/system/cpu/cpu" + std::to_string(cpu) + "/cpufreq/cpuinfo_max_freq");
            in >> frequencies[cpu];
            fastest = std::max(fastest, frequencies[cpu]);
        }
        if (fastest == 0) return;
        std::vector<int> cores;
        for (int cpu = 0; cpu < CPU_SETSIZE; ++cpu) {
            if (CPU_ISSET(cpu, &previous) && (concurrency > 1 || frequencies[cpu] == fastest)) cores.push_back(cpu);
        }
        std::stable_sort(cores.begin(), cores.end(), [&](int a, int b) { return frequencies[a] > frequencies[b]; });
        cpu_set_t selected; CPU_ZERO(&selected);
        count = 0;
        // Round-robin distributes fast and slow cores between independent states.
        for (size_t i = slot; i < cores.size(); i += concurrency) {
            CPU_SET(cores[i], &selected); count++;
        }
        if (count == 0) { count = 1; return; }
        changed = sched_setaffinity(0, sizeof(selected), &selected) == 0;
    }
    ~PerformanceCores() { if (changed) sched_setaffinity(0, sizeof(previous), &previous); }
};

extern "C" JNIEXPORT jstring JNICALL
Java_me_arianb_usb_1hid_1client_dictation_WhisperEngine_transcribeNative(
        JNIEnv *env, jobject, jstring model, jfloatArray audio, jint threads, jlong id, jint concurrency) {
    const int limit = std::clamp(static_cast<int>(concurrency), 1, 5);
    InferenceRequest request{id, std::chrono::steady_clock::now() + std::chrono::seconds(
        std::clamp((10 + static_cast<int>(env->GetArrayLength(audio) / 8000)) * limit, 15, 300))};
    if (request.cancelled()) return env->NewStringUTF("");
    const char *path_chars = env->GetStringUTFChars(model, nullptr);
    std::string path(path_chars);
    env->ReleaseStringUTFChars(model, path_chars);
    std::shared_ptr<Model> weights;
    {
        std::lock_guard<std::mutex> lock(guard);
        if (!loaded_model || path != loaded_path) {
            auto config = whisper_context_default_params();
            config.use_gpu = false;
            config.flash_attn = true;
            auto *ctx = whisper_init_from_file_with_params_no_state(path.c_str(), config);
            if (!ctx) { fail(env, "Could not load the Whisper base model. Download it again."); return nullptr; }
            loaded_model = std::make_shared<Model>(ctx);
            loaded_path = path;
        }
        weights = loaded_model;
    }
    struct StateLease {
        std::shared_ptr<Model> weights;
        int slot = -1;
        ~StateLease() {
            if (slot < 0) return;
            std::lock_guard<std::mutex> lock(weights->mutex);
            weights->busy[slot] = false;
            weights->available.notify_all();
        }
    } lease{weights};
    {
        std::unique_lock<std::mutex> lock(weights->mutex);
        for (int i = limit; i < 5; ++i) {
            if (!weights->busy[i] && weights->states[i]) {
                whisper_free_state(weights->states[i]);
                weights->states[i] = nullptr;
            }
        }
        while (lease.slot < 0) {
            if (request.cancelled()) return env->NewStringUTF("");
            if (request.expired()) { fail(env, "Timed out waiting for Whisper resources."); return nullptr; }
            // Reuse existing states first; allocate extra inference buffers only
            // when concurrent work actually needs them. Model weights stay shared.
            for (int i = 0; i < limit; ++i) {
                if (!weights->busy[i] && weights->states[i]) { lease.slot = i; break; }
            }
            if (lease.slot < 0) {
                for (int i = 0; i < limit; ++i) {
                    if (!weights->busy[i] && !weights->states[i]) {
                        weights->states[i] = whisper_init_state(weights->context);
                        if (weights->states[i]) { lease.slot = i; break; }
                    }
                }
            }
            if (lease.slot < 0) {
                bool any_state = false;
                for (auto *state : weights->states) any_state |= state != nullptr;
                if (!any_state) { fail(env, "Could not allocate Whisper inference state."); return nullptr; }
                weights->available.wait_for(lock, std::chrono::milliseconds(25));
            }
        }
        weights->busy[lease.slot] = true;
    }
    auto *context = weights->context;
    auto *state = weights->states[lease.slot];
    PerformanceCores affinity(limit, lease.slot);
    std::vector<float> samples(env->GetArrayLength(audio));
    env->GetFloatArrayRegion(audio, 0, samples.size(), samples.data());
    if (env->ExceptionCheck()) return nullptr;
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = std::clamp(static_cast<int>(threads), 1, std::min(4, affinity.count));
    params.language = "en";
    params.translate = false;
    params.no_context = true;
    // Short dictations need not encode a full 30 seconds of padded silence.
    params.audio_ctx = std::min(1500, std::max(256, static_cast<int>((samples.size() + 319) / 320) + 64));
    params.no_timestamps = true;
    params.single_segment = true;
    // One deterministic pass. Temperature fallback can run several full decodes
    // on noisy recordings and produce repeated/hallucinated phrases.
    params.temperature = 0;
    params.temperature_inc = 0;
    params.greedy.best_of = 1;
    params.max_tokens = std::clamp(static_cast<int>(std::min<size_t>(samples.size(), 16000 * 30) / 1333) + 16, 32, 448);
    params.abort_callback = abort_inference;
    params.abort_callback_user_data = &request;
    params.encoder_begin_callback = [](whisper_context *, whisper_state *, void *data) { return !abort_inference(data); };
    params.encoder_begin_callback_user_data = &request;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    const int result = whisper_full_with_state(context, state, params, samples.data(), samples.size());
    if (request.cancelled()) return env->NewStringUTF("");
    if (request.expired()) { fail(env, "Transcription timed out. Hold the button to record again."); return nullptr; }
    if (result != 0) {
        fail(env, "Whisper inference failed."); return nullptr;
    }
    std::string text;
    for (int i = 0; i < whisper_full_n_segments_from_state(state); ++i) {
        if (whisper_full_get_segment_no_speech_prob_from_state(state, i) < 0.6f)
            text += whisper_full_get_segment_text_from_state(state, i);
    }
    // Decode real UTF-8 through Java rather than JNI's modified UTF-8.
    auto bytes = env->NewByteArray(text.size());
    env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
    auto cls = env->FindClass("java/lang/String");
    auto constructor = env->GetMethodID(cls, "<init>", "([BLjava/lang/String;)V");
    auto charset = env->NewStringUTF("UTF-8");
    return static_cast<jstring>(env->NewObject(cls, constructor, bytes, charset));
}

extern "C" JNIEXPORT void JNICALL
Java_me_arianb_usb_1hid_1client_dictation_WhisperEngine_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    // Active leases retain weights until their own cancellation finishes.
    loaded_model.reset();
    loaded_path.clear();
}
