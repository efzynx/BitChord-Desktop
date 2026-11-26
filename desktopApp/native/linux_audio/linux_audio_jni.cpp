#include <jni.h>
#include <dlfcn.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <vector>

namespace {

// Minimal ALSA PCM / Ctl / Mixer ABI definitions loaded dynamically from libasound.so.2 so builds
// have no compile-time dependency on alsa-lib development headers across Linux distros and CI.
struct snd_pcm_t;
struct snd_pcm_hw_params_t;
struct snd_ctl_t;
struct snd_ctl_card_info_t;
struct snd_pcm_info_t;
struct snd_mixer_t;
struct snd_mixer_elem_t;

using snd_pcm_uframes_t = unsigned long;
using snd_pcm_sframes_t = long;

constexpr int kStreamPlayback = 0;
constexpr int kAccessRwInterleaved = 3;
constexpr int kFormatS16Le = 2;
constexpr int kFormatS24Le = 6;
constexpr int kFormatS32Le = 10;
constexpr int kFormatS243Le = 32;

struct AlsaApi {
    void* handle = nullptr;
    bool loaded = false;

    int (*pcm_open)(snd_pcm_t**, const char*, int, int) = nullptr;
    int (*pcm_close)(snd_pcm_t*) = nullptr;
    int (*pcm_prepare)(snd_pcm_t*) = nullptr;
    int (*pcm_start)(snd_pcm_t*) = nullptr;
    int (*pcm_drop)(snd_pcm_t*) = nullptr;
    int (*pcm_drain)(snd_pcm_t*) = nullptr;
    int (*pcm_pause)(snd_pcm_t*, int) = nullptr;
    int (*pcm_resume)(snd_pcm_t*) = nullptr;
    int (*pcm_delay)(snd_pcm_t*, snd_pcm_sframes_t*) = nullptr;
    snd_pcm_sframes_t (*pcm_writei)(snd_pcm_t*, const void*, snd_pcm_uframes_t) = nullptr;
    int (*pcm_recover)(snd_pcm_t*, int, int) = nullptr;
    const char* (*strerror_fn)(int) = nullptr;

    int (*hw_params_malloc)(snd_pcm_hw_params_t**) = nullptr;
    void (*hw_params_free)(snd_pcm_hw_params_t*) = nullptr;
    int (*hw_params_any)(snd_pcm_t*, snd_pcm_hw_params_t*) = nullptr;
    int (*hw_params_set_access)(snd_pcm_t*, snd_pcm_hw_params_t*, int) = nullptr;
    int (*hw_params_test_format)(snd_pcm_t*, snd_pcm_hw_params_t*, int) = nullptr;
    int (*hw_params_set_format)(snd_pcm_t*, snd_pcm_hw_params_t*, int) = nullptr;
    int (*hw_params_set_channels)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int) = nullptr;
    int (*hw_params_set_rate_resample)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int) = nullptr;
    int (*hw_params_test_rate)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int, int) = nullptr;
    int (*hw_params_set_rate)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int, int) = nullptr;
    int (*hw_params_set_buffer_time_near)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int*, int*) = nullptr;
    int (*hw_params_set_period_time_near)(snd_pcm_t*, snd_pcm_hw_params_t*, unsigned int*, int*) = nullptr;
    int (*hw_params_get_buffer_size)(const snd_pcm_hw_params_t*, snd_pcm_uframes_t*) = nullptr;
    int (*hw_params)(snd_pcm_t*, snd_pcm_hw_params_t*) = nullptr;
    int (*hw_params_can_pause)(const snd_pcm_hw_params_t*) = nullptr;

    int (*card_next)(int*) = nullptr;
    int (*ctl_open)(snd_ctl_t**, const char*, int) = nullptr;
    int (*ctl_close)(snd_ctl_t*) = nullptr;
    int (*ctl_card_info_malloc)(snd_ctl_card_info_t**) = nullptr;
    void (*ctl_card_info_free)(snd_ctl_card_info_t*) = nullptr;
    int (*ctl_card_info)(snd_ctl_t*, snd_ctl_card_info_t*) = nullptr;
    const char* (*ctl_card_info_get_name)(const snd_ctl_card_info_t*) = nullptr;
    int (*ctl_pcm_next_device)(snd_ctl_t*, int*) = nullptr;
    int (*ctl_pcm_info)(snd_ctl_t*, snd_pcm_info_t*) = nullptr;
    int (*pcm_info_malloc)(snd_pcm_info_t**) = nullptr;
    void (*pcm_info_free)(snd_pcm_info_t*) = nullptr;
    void (*pcm_info_set_device)(snd_pcm_info_t*, unsigned int) = nullptr;
    void (*pcm_info_set_subdevice)(snd_pcm_info_t*, unsigned int) = nullptr;
    void (*pcm_info_set_stream)(snd_pcm_info_t*, int) = nullptr;
    const char* (*pcm_info_get_name)(const snd_pcm_info_t*) = nullptr;

    bool mixer_ready = false;
    int (*mixer_open)(snd_mixer_t**, int) = nullptr;
    int (*mixer_close)(snd_mixer_t*) = nullptr;
    int (*mixer_attach)(snd_mixer_t*, const char*) = nullptr;
    int (*mixer_selem_register)(snd_mixer_t*, void*, void**) = nullptr;
    int (*mixer_load)(snd_mixer_t*) = nullptr;
    snd_mixer_elem_t* (*mixer_first_elem)(snd_mixer_t*) = nullptr;
    snd_mixer_elem_t* (*mixer_elem_next)(snd_mixer_elem_t*) = nullptr;
    const char* (*mixer_selem_get_name)(snd_mixer_elem_t*) = nullptr;
    int (*mixer_selem_has_playback_volume)(snd_mixer_elem_t*) = nullptr;
    int (*mixer_selem_has_playback_switch)(snd_mixer_elem_t*) = nullptr;
    int (*mixer_selem_get_playback_switch)(snd_mixer_elem_t*, int, int*) = nullptr;
    int (*mixer_selem_set_playback_switch_all)(snd_mixer_elem_t*, int) = nullptr;
    int (*mixer_selem_get_playback_dB_range)(snd_mixer_elem_t*, long*, long*) = nullptr;
    int (*mixer_selem_get_playback_dB)(snd_mixer_elem_t*, int, long*) = nullptr;
    int (*mixer_selem_set_playback_dB_all)(snd_mixer_elem_t*, long, int) = nullptr;

    template <typename T>
    bool sym(T& fn, const char* name) {
        fn = reinterpret_cast<T>(dlsym(handle, name));
        return fn != nullptr;
    }

    bool init() {
        if (loaded) return handle != nullptr;
        loaded = true;
        handle = dlopen("libasound.so.2", RTLD_LAZY | RTLD_LOCAL);
        if (handle == nullptr) handle = dlopen("libasound.so", RTLD_LAZY | RTLD_LOCAL);
        if (handle == nullptr) return false;

        const bool ok =
            sym(pcm_open, "snd_pcm_open") &&
            sym(pcm_close, "snd_pcm_close") &&
            sym(pcm_prepare, "snd_pcm_prepare") &&
            sym(pcm_start, "snd_pcm_start") &&
            sym(pcm_drop, "snd_pcm_drop") &&
            sym(pcm_drain, "snd_pcm_drain") &&
            sym(pcm_pause, "snd_pcm_pause") &&
            sym(pcm_resume, "snd_pcm_resume") &&
            sym(pcm_delay, "snd_pcm_delay") &&
            sym(pcm_writei, "snd_pcm_writei") &&
            sym(pcm_recover, "snd_pcm_recover") &&
            sym(strerror_fn, "snd_strerror") &&
            sym(hw_params_malloc, "snd_pcm_hw_params_malloc") &&
            sym(hw_params_free, "snd_pcm_hw_params_free") &&
            sym(hw_params_any, "snd_pcm_hw_params_any") &&
            sym(hw_params_set_access, "snd_pcm_hw_params_set_access") &&
            sym(hw_params_test_format, "snd_pcm_hw_params_test_format") &&
            sym(hw_params_set_format, "snd_pcm_hw_params_set_format") &&
            sym(hw_params_set_channels, "snd_pcm_hw_params_set_channels") &&
            sym(hw_params_set_rate_resample, "snd_pcm_hw_params_set_rate_resample") &&
            sym(hw_params_test_rate, "snd_pcm_hw_params_test_rate") &&
            sym(hw_params_set_rate, "snd_pcm_hw_params_set_rate") &&
            sym(hw_params_set_buffer_time_near, "snd_pcm_hw_params_set_buffer_time_near") &&
            sym(hw_params_set_period_time_near, "snd_pcm_hw_params_set_period_time_near") &&
            sym(hw_params_get_buffer_size, "snd_pcm_hw_params_get_buffer_size") &&
            sym(hw_params, "snd_pcm_hw_params") &&
            sym(hw_params_can_pause, "snd_pcm_hw_params_can_pause") &&
            sym(card_next, "snd_card_next") &&
            sym(ctl_open, "snd_ctl_open") &&
            sym(ctl_close, "snd_ctl_close") &&
            sym(ctl_card_info_malloc, "snd_ctl_card_info_malloc") &&
            sym(ctl_card_info_free, "snd_ctl_card_info_free") &&
            sym(ctl_card_info, "snd_ctl_card_info") &&
            sym(ctl_card_info_get_name, "snd_ctl_card_info_get_name") &&
            sym(ctl_pcm_next_device, "snd_ctl_pcm_next_device") &&
            sym(ctl_pcm_info, "snd_ctl_pcm_info") &&
            sym(pcm_info_malloc, "snd_pcm_info_malloc") &&
            sym(pcm_info_free, "snd_pcm_info_free") &&
            sym(pcm_info_set_device, "snd_pcm_info_set_device") &&
            sym(pcm_info_set_subdevice, "snd_pcm_info_set_subdevice") &&
            sym(pcm_info_set_stream, "snd_pcm_info_set_stream") &&
            sym(pcm_info_get_name, "snd_pcm_info_get_name");

        if (!ok) {
            dlclose(handle);
            handle = nullptr;
            return false;
        }
        mixer_ready =
            sym(mixer_open, "snd_mixer_open") &&
            sym(mixer_close, "snd_mixer_close") &&
            sym(mixer_attach, "snd_mixer_attach") &&
            sym(mixer_selem_register, "snd_mixer_selem_register") &&
            sym(mixer_load, "snd_mixer_load") &&
            sym(mixer_first_elem, "snd_mixer_first_elem") &&
            sym(mixer_elem_next, "snd_mixer_elem_next") &&
            sym(mixer_selem_get_name, "snd_mixer_selem_get_name") &&
            sym(mixer_selem_has_playback_volume, "snd_mixer_selem_has_playback_volume") &&
            sym(mixer_selem_has_playback_switch, "snd_mixer_selem_has_playback_switch") &&
            sym(mixer_selem_get_playback_switch, "snd_mixer_selem_get_playback_switch") &&
            sym(mixer_selem_set_playback_switch_all, "snd_mixer_selem_set_playback_switch_all") &&
            sym(mixer_selem_get_playback_dB_range, "snd_mixer_selem_get_playback_dB_range") &&
            sym(mixer_selem_get_playback_dB, "snd_mixer_selem_get_playback_dB") &&
            sym(mixer_selem_set_playback_dB_all, "snd_mixer_selem_set_playback_dB_all");
        return true;
    }
};

AlsaApi g_alsa;
std::mutex g_mutex;
std::mutex g_mixer_mutex;
snd_pcm_t* g_pcm = nullptr;
snd_mixer_t* g_mixer = nullptr;
snd_mixer_elem_t* g_mixer_elem = nullptr;
std::string g_mixer_name;
double g_mixer_min_db = 0.0;
double g_mixer_max_db = 0.0;
bool g_mixer_has_switch = false;
int g_rate = 0;
int g_channels = 0;
int g_container_bytes = 0;
int g_valid_bits = 0;
int g_alsa_format = 0;
bool g_can_pause = false;
bool g_paused = false;
snd_pcm_uframes_t g_buffer_frames = 0;
uint64_t g_written_frames = 0;
uint64_t g_last_played = 0;
std::string g_device_id;
std::string g_device_name;
std::string g_last_error;

void set_error(const char* prefix, int err) {
    g_last_error = prefix;
    if (err < 0 && g_alsa.strerror_fn != nullptr) {
        g_last_error += ": ";
        g_last_error += g_alsa.strerror_fn(err);
    }
}

void close_mixer_locked() {
    std::lock_guard<std::mutex> mix_guard(g_mixer_mutex);
    if (g_mixer != nullptr && g_alsa.mixer_close != nullptr) {
        g_alsa.mixer_close(g_mixer);
    }
    g_mixer = nullptr;
    g_mixer_elem = nullptr;
    g_mixer_name.clear();
    g_mixer_min_db = 0.0;
    g_mixer_max_db = 0.0;
    g_mixer_has_switch = false;
}

std::string lowercase_ascii(const char* raw) {
    std::string out;
    if (raw == nullptr) return out;
    for (const char* p = raw; *p != '\0'; ++p) {
        out.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(*p))));
    }
    return out;
}

std::string mixer_card_from_hw(const std::string& hw_dev) {
    const size_t colon = hw_dev.find(':');
    if (colon == std::string::npos) return "";
    size_t start = colon + 1;
    size_t end = start;
    while (end < hw_dev.size() && std::isdigit(static_cast<unsigned char>(hw_dev[end]))) {
        ++end;
    }
    if (end == start) return "";
    return "hw:" + hw_dev.substr(start, end - start);
}

int score_mixer_element(snd_mixer_elem_t* elem, const std::string& lower_name, bool unmuted) {
    if (lower_name.empty()) return -1;
    if (lower_name.find("mic") != std::string::npos ||
        lower_name.find("capture") != std::string::npos ||
        lower_name.find("boost") != std::string::npos ||
        lower_name.find("beep") != std::string::npos ||
        lower_name.find("loopback") != std::string::npos ||
        lower_name.find("iec958") != std::string::npos) {
        return -1;
    }
    long min_hdb = 0;
    long max_hdb = 0;
    if (g_alsa.mixer_selem_get_playback_dB_range(elem, &min_hdb, &max_hdb) != 0 || max_hdb <= min_hdb) {
        return -1;
    }
    if (lower_name == "master") return 200 + (unmuted ? 20 : 0);
    int score = 110;
    if (lower_name.find("dac") != std::string::npos) {
        score = 180;
    } else if (lower_name.find("headphone") != std::string::npos || lower_name.find("headset") != std::string::npos) {
        score = 170;
    } else if (lower_name == "pcm" || lower_name.find("pcm") != std::string::npos) {
        score = 160;
    } else if (lower_name.find("speaker") != std::string::npos || lower_name.find("front") != std::string::npos || lower_name.find("line") != std::string::npos) {
        score = 140;
    }
    return score + (unmuted ? 25 : 0);
}

void open_mixer_for_device(const std::string& hw_dev) {
    close_mixer_locked();
    if (!g_alsa.mixer_ready) return;
    const std::string card = mixer_card_from_hw(hw_dev);
    if (card.empty()) return;

    std::lock_guard<std::mutex> mix_guard(g_mixer_mutex);
    snd_mixer_t* mixer = nullptr;
    if (g_alsa.mixer_open(&mixer, 0) < 0 || mixer == nullptr) return;
    if (g_alsa.mixer_attach(mixer, card.c_str()) < 0 ||
        g_alsa.mixer_selem_register(mixer, nullptr, nullptr) < 0 ||
        g_alsa.mixer_load(mixer) < 0) {
        g_alsa.mixer_close(mixer);
        return;
    }

    snd_mixer_elem_t* best = nullptr;
    int best_score = -1;
    std::string best_name;
    double best_min_db = 0.0;
    double best_max_db = 0.0;
    bool best_has_sw = false;

    for (snd_mixer_elem_t* elem = g_alsa.mixer_first_elem(mixer);
         elem != nullptr;
         elem = g_alsa.mixer_elem_next(elem)) {
        if (!g_alsa.mixer_selem_has_playback_volume(elem)) continue;
        const char* raw_name = g_alsa.mixer_selem_get_name(elem);
        const std::string lower = lowercase_ascii(raw_name);
        const bool has_sw = g_alsa.mixer_selem_has_playback_switch(elem) != 0;
        int sw_val = 1;
        if (has_sw) g_alsa.mixer_selem_get_playback_switch(elem, 0, &sw_val);
        const int score = score_mixer_element(elem, lower, sw_val != 0);
        if (score > best_score) {
            long lo = 0;
            long hi = 0;
            g_alsa.mixer_selem_get_playback_dB_range(elem, &lo, &hi);
            best = elem;
            best_score = score;
            best_name = raw_name != nullptr ? raw_name : "";
            best_min_db = static_cast<double>(lo) / 100.0;
            best_max_db = static_cast<double>(hi) / 100.0;
            best_has_sw = has_sw;
        }
    }

    if (best == nullptr) {
        g_alsa.mixer_close(mixer);
        return;
    }

    g_mixer = mixer;
    g_mixer_elem = best;
    g_mixer_name = best_name;
    g_mixer_min_db = best_min_db;
    g_mixer_max_db = best_max_db;
    g_mixer_has_switch = best_has_sw;
}

void close_locked() {
    close_mixer_locked();
    if (g_pcm != nullptr) {
        g_alsa.pcm_drop(g_pcm);
        g_alsa.pcm_close(g_pcm);
        g_pcm = nullptr;
    }
    g_rate = 0;
    g_channels = 0;
    g_container_bytes = 0;
    g_valid_bits = 0;
    g_alsa_format = 0;
    g_can_pause = false;
    g_paused = false;
    g_buffer_frames = 0;
    g_written_frames = 0;
    g_last_played = 0;
    g_device_id.clear();
    g_device_name.clear();
}

int format_for_bits(int valid_bits, int container_bits) {
    if (valid_bits == 16 && container_bits == 16) return kFormatS16Le;
    if (valid_bits == 24 && container_bits == 24) return kFormatS243Le;
    if (valid_bits == 24 && container_bits == 32) return kFormatS24Le;
    if (valid_bits == 32 && container_bits == 32) return kFormatS32Le;
    return -1;
}

uint64_t played_frames_locked() {
    if (g_pcm == nullptr) return g_last_played;
    snd_pcm_sframes_t delay = 0;
    if (g_alsa.pcm_delay(g_pcm, &delay) == 0 && delay >= 0) {
        const uint64_t queued = std::min<uint64_t>(static_cast<uint64_t>(delay), g_written_frames);
        const uint64_t played = g_written_frames - queued;
        if (played > g_last_played) g_last_played = played;
    }
    return g_last_played;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeAvailable(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    return g_alsa.init() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeDevices(JNIEnv* env, jclass) {
    std::vector<std::string> items;
    {
        std::lock_guard<std::mutex> guard(g_mutex);
        if (g_alsa.init()) {
            snd_ctl_card_info_t* card_info = nullptr;
            snd_pcm_info_t* pcm_info = nullptr;
            g_alsa.ctl_card_info_malloc(&card_info);
            g_alsa.pcm_info_malloc(&pcm_info);

            int card = -1;
            while (g_alsa.card_next(&card) == 0 && card >= 0) {
                const std::string ctl_name = "hw:" + std::to_string(card);
                snd_ctl_t* ctl = nullptr;
                if (g_alsa.ctl_open(&ctl, ctl_name.c_str(), 0) < 0 || ctl == nullptr) continue;

                std::string card_name = "Card " + std::to_string(card);
                if (card_info != nullptr && g_alsa.ctl_card_info(ctl, card_info) == 0) {
                    if (const char* name = g_alsa.ctl_card_info_get_name(card_info)) {
                        if (name[0] != '\0') card_name = name;
                    }
                }

                int dev = -1;
                while (g_alsa.ctl_pcm_next_device(ctl, &dev) == 0 && dev >= 0) {
                    if (pcm_info == nullptr) continue;
                    g_alsa.pcm_info_set_device(pcm_info, static_cast<unsigned int>(dev));
                    g_alsa.pcm_info_set_subdevice(pcm_info, 0);
                    g_alsa.pcm_info_set_stream(pcm_info, kStreamPlayback);
                    if (g_alsa.ctl_pcm_info(ctl, pcm_info) < 0) continue;

                    const char* dev_name = g_alsa.pcm_info_get_name(pcm_info);
                    const std::string hw_id = "hw:" + std::to_string(card) + "," + std::to_string(dev);
                    const std::string label = (dev_name != nullptr && dev_name[0] != '\0')
                        ? (card_name + " — " + dev_name)
                        : card_name;
                    items.push_back(hw_id);
                    items.push_back(label);
                }
                g_alsa.ctl_close(ctl);
            }

            if (pcm_info != nullptr) g_alsa.pcm_info_free(pcm_info);
            if (card_info != nullptr) g_alsa.ctl_card_info_free(card_info);
        }
    }
    jclass str_cls = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(items.size()), str_cls, nullptr);
    for (size_t i = 0; i < items.size(); ++i) {
        jstring s = env->NewStringUTF(items[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    return result;
}

JNIEXPORT jintArray JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeProbeSupportedRates(
    JNIEnv* env,
    jclass,
    jstring hw_device,
    jint channels,
    jintArray candidate_rates) {
    std::lock_guard<std::mutex> guard(g_mutex);
    std::vector<jint> supported;
    if (!g_alsa.init()) return env->NewIntArray(0);

    const char* raw_dev = env->GetStringUTFChars(hw_device, nullptr);
    std::string dev_id = raw_dev ? raw_dev : "";
    if (raw_dev) env->ReleaseStringUTFChars(hw_device, raw_dev);

    jsize len = env->GetArrayLength(candidate_rates);
    jint* rates = env->GetIntArrayElements(candidate_rates, nullptr);

    snd_pcm_t* pcm = nullptr;
    int err = g_alsa.pcm_open(&pcm, dev_id.c_str(), kStreamPlayback, 0);
    if (err >= 0 && pcm != nullptr) {
        snd_pcm_hw_params_t* params = nullptr;
        g_alsa.hw_params_malloc(&params);
        if (params != nullptr && g_alsa.hw_params_any(pcm, params) >= 0) {
            g_alsa.hw_params_set_rate_resample(pcm, params, 0);
            if (g_alsa.hw_params_set_access(pcm, params, kAccessRwInterleaved) >= 0 &&
                g_alsa.hw_params_set_channels(pcm, params, static_cast<unsigned int>(channels)) >= 0) {
                for (jsize i = 0; i < len; ++i) {
                    if (rates[i] > 0 &&
                        g_alsa.hw_params_test_rate(pcm, params, static_cast<unsigned int>(rates[i]), 0) == 0) {
                        supported.push_back(rates[i]);
                    }
                }
            }
        }
        if (params != nullptr) g_alsa.hw_params_free(params);
        g_alsa.pcm_close(pcm);
    }
    if (rates != nullptr) env->ReleaseIntArrayElements(candidate_rates, rates, JNI_ABORT);

    jintArray out = env->NewIntArray(static_cast<jsize>(supported.size()));
    if (!supported.empty()) env->SetIntArrayRegion(out, 0, static_cast<jsize>(supported.size()), supported.data());
    return out;
}

JNIEXPORT jintArray JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeOpen(
    JNIEnv* env,
    jclass,
    jstring hw_device,
    jstring friendly_name,
    jint sample_rate,
    jint channels,
    jint valid_bits,
    jint container_bits,
    jboolean try_msb_s32_for_24) {
    std::lock_guard<std::mutex> guard(g_mutex);
    close_locked();
    g_last_error.clear();

    if (!g_alsa.init()) {
        g_last_error = "libasound.so.2 is unavailable";
        return env->NewIntArray(0);
    }

    const char* raw_dev = env->GetStringUTFChars(hw_device, nullptr);
    std::string dev_id = raw_dev ? raw_dev : "";
    if (raw_dev) env->ReleaseStringUTFChars(hw_device, raw_dev);

    const char* raw_name = env->GetStringUTFChars(friendly_name, nullptr);
    std::string name = raw_name ? raw_name : dev_id;
    if (raw_name) env->ReleaseStringUTFChars(friendly_name, raw_name);

    const int alsa_fmt = (try_msb_s32_for_24 == JNI_TRUE && valid_bits == 24 && container_bits == 32)
        ? kFormatS32Le
        : format_for_bits(valid_bits, container_bits);
    if (alsa_fmt < 0) {
        g_last_error = "Unsupported PCM bit configuration";
        return env->NewIntArray(0);
    }

    snd_pcm_t* pcm = nullptr;
    int err = g_alsa.pcm_open(&pcm, dev_id.c_str(), kStreamPlayback, 0);
    if (err < 0 || pcm == nullptr) {
        set_error(err == -EBUSY ? "ALSA device busy" : "ALSA open failed", err);
        return env->NewIntArray(0);
    }

    snd_pcm_hw_params_t* params = nullptr;
    g_alsa.hw_params_malloc(&params);
    if (params == nullptr) {
        g_alsa.pcm_close(pcm);
        g_last_error = "Out of memory allocating ALSA hw_params";
        return env->NewIntArray(0);
    }

    bool configured = false;
    snd_pcm_uframes_t buffer_frames = 0;
    int can_pause = 0;

    do {
        err = g_alsa.hw_params_any(pcm, params);
        if (err < 0) { set_error("ALSA hw_params_any", err); break; }

        // Bit-perfect invariant: never permit ALSA plugin/software resampling on the direct path.
        err = g_alsa.hw_params_set_rate_resample(pcm, params, 0);
        if (err < 0) { set_error("ALSA disable resample", err); break; }

        err = g_alsa.hw_params_set_access(pcm, params, kAccessRwInterleaved);
        if (err < 0) { set_error("ALSA set_access", err); break; }

        err = g_alsa.hw_params_set_format(pcm, params, alsa_fmt);
        if (err < 0) { set_error("ALSA set_format", err); break; }

        err = g_alsa.hw_params_set_channels(pcm, params, static_cast<unsigned int>(channels));
        if (err < 0) { set_error("ALSA set_channels", err); break; }

        err = g_alsa.hw_params_set_rate(pcm, params, static_cast<unsigned int>(sample_rate), 0);
        if (err < 0) { set_error("ALSA set_rate", err); break; }

        unsigned int buffer_us = 160000;
        unsigned int period_us = 20000;
        int dir = 0;
        g_alsa.hw_params_set_buffer_time_near(pcm, params, &buffer_us, &dir);
        dir = 0;
        g_alsa.hw_params_set_period_time_near(pcm, params, &period_us, &dir);

        err = g_alsa.hw_params(pcm, params);
        if (err < 0) { set_error("ALSA apply hw_params", err); break; }

        g_alsa.hw_params_get_buffer_size(params, &buffer_frames);
        can_pause = g_alsa.hw_params_can_pause(params) == 1 ? 1 : 0;
        err = g_alsa.pcm_prepare(pcm);
        if (err < 0) { set_error("ALSA prepare", err); break; }

        configured = true;
    } while (false);

    g_alsa.hw_params_free(params);
    if (!configured) {
        g_alsa.pcm_close(pcm);
        return env->NewIntArray(0);
    }

    g_pcm = pcm;
    g_rate = sample_rate;
    g_channels = channels;
    g_container_bytes = container_bits / 8;
    g_valid_bits = valid_bits;
    g_alsa_format = alsa_fmt;
    g_can_pause = can_pause != 0;
    g_paused = false;
    g_buffer_frames = buffer_frames;
    g_written_frames = 0;
    g_last_played = 0;
    g_device_id = dev_id;
    g_device_name = name;
    open_mixer_for_device(dev_id);

    const jint result[3] = {
        static_cast<jint>(buffer_frames * static_cast<snd_pcm_uframes_t>(channels) * static_cast<snd_pcm_uframes_t>(g_container_bytes)),
        static_cast<jint>(alsa_fmt == kFormatS32Le ? 1 : 0),  // 1 when 24-bit is MSB-aligned in 32-bit S32_LE
        static_cast<jint>(buffer_frames),
    };
    jintArray out = env->NewIntArray(3);
    env->SetIntArrayRegion(out, 0, 3, result);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeWritePcm(
    JNIEnv* env,
    jclass,
    jbyteArray bytes,
    jint length) {
    if (bytes == nullptr || length <= 0) return 0;
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_pcm == nullptr || g_channels <= 0 || g_container_bytes <= 0) return -1;

    const jsize available = env->GetArrayLength(bytes);
    const jint safe_len = std::min(length, available);
    const int frame_bytes = g_channels * g_container_bytes;
    if (frame_bytes <= 0) return -1;

    jboolean copied = JNI_FALSE;
    jbyte* raw = env->GetByteArrayElements(bytes, &copied);
    if (raw == nullptr) return -1;

    const snd_pcm_uframes_t total_frames = static_cast<snd_pcm_uframes_t>(safe_len / frame_bytes);
    snd_pcm_uframes_t offset_frames = 0;
    int write_error = 0;

    while (offset_frames < total_frames) {
        const void* ptr = raw + (offset_frames * static_cast<snd_pcm_uframes_t>(frame_bytes));
        const snd_pcm_uframes_t remaining = total_frames - offset_frames;
        snd_pcm_sframes_t written = g_alsa.pcm_writei(g_pcm, ptr, remaining);
        if (written < 0) {
            const int rec = g_alsa.pcm_recover(g_pcm, static_cast<int>(written), 1);
            if (rec < 0) {
                set_error("ALSA write", static_cast<int>(written));
                write_error = rec;
                break;
            }
            continue;
        }
        offset_frames += static_cast<snd_pcm_uframes_t>(written);
        g_written_frames += static_cast<uint64_t>(written);
    }

    env->ReleaseByteArrayElements(bytes, raw, JNI_ABORT);
    if (write_error < 0 && offset_frames == 0) return -1;
    return static_cast<jint>(offset_frames * static_cast<snd_pcm_uframes_t>(frame_bytes));
}

JNIEXPORT jlong JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeFramesPlayed(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    return static_cast<jlong>(played_frames_locked());
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativePause(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_pcm == nullptr || g_paused) return;
    if (g_can_pause && g_alsa.pcm_pause(g_pcm, 1) == 0) {
        g_paused = true;
    } else {
        g_alsa.pcm_drop(g_pcm);
        g_alsa.pcm_prepare(g_pcm);
        g_written_frames = g_last_played;
        g_paused = true;
    }
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeResume(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_pcm == nullptr || !g_paused) return;
    if (g_can_pause) {
        if (g_alsa.pcm_pause(g_pcm, 0) < 0) g_alsa.pcm_prepare(g_pcm);
    }
    g_paused = false;
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeFlush(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_pcm == nullptr) return;
    g_alsa.pcm_drop(g_pcm);
    g_alsa.pcm_prepare(g_pcm);
    g_last_played = played_frames_locked();
    g_written_frames = g_last_played;
    g_paused = false;
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeDrain(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    if (g_pcm != nullptr) g_alsa.pcm_drain(g_pcm);
}

JNIEXPORT void JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeClose(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    close_locked();
}

JNIEXPORT jstring JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeLastError(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> guard(g_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeHardwareVolumeName(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> mix_guard(g_mixer_mutex);
    return env->NewStringUTF(g_mixer_name.c_str());
}

JNIEXPORT jdoubleArray JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeHardwareVolumeRange(JNIEnv* env, jclass) {
    std::lock_guard<std::mutex> mix_guard(g_mixer_mutex);
    if (!g_alsa.mixer_ready || g_mixer_elem == nullptr || g_mixer_max_db <= g_mixer_min_db) {
        return env->NewDoubleArray(0);
    }
    long cur_hdb = 0;
    if (g_alsa.mixer_selem_get_playback_dB(g_mixer_elem, 0, &cur_hdb) != 0) {
        return env->NewDoubleArray(0);
    }
    int sw = 1;
    if (g_mixer_has_switch) {
        g_alsa.mixer_selem_get_playback_switch(g_mixer_elem, 0, &sw);
    }
    const jdouble values[4] = {
        g_mixer_min_db,
        g_mixer_max_db,
        static_cast<double>(cur_hdb) / 100.0,
        sw == 0 ? 1.0 : 0.0,
    };
    jdoubleArray out = env->NewDoubleArray(4);
    env->SetDoubleArrayRegion(out, 0, 4, values);
    return out;
}

JNIEXPORT jdouble JNICALL
Java_com_music_bitchord_desktop_DesktopLinuxAudio_nativeSetHardwareVolumeDb(
    JNIEnv*,
    jclass,
    jdouble db,
    jboolean mute) {
    std::lock_guard<std::mutex> mix_guard(g_mixer_mutex);
    if (!g_alsa.mixer_ready || g_mixer_elem == nullptr) {
        return std::numeric_limits<double>::quiet_NaN();
    }
    if (g_mixer_has_switch) {
        g_alsa.mixer_selem_set_playback_switch_all(g_mixer_elem, mute == JNI_TRUE ? 0 : 1);
    }
    if (mute != JNI_TRUE) {
        const double clamped = std::clamp(static_cast<double>(db), g_mixer_min_db, g_mixer_max_db);
        const long target_hdb = static_cast<long>(std::llround(clamped * 100.0));
        if (g_alsa.mixer_selem_set_playback_dB_all(g_mixer_elem, target_hdb, -1) < 0) {
            return std::numeric_limits<double>::quiet_NaN();
        }
    }
    long actual_hdb = 0;
    if (g_alsa.mixer_selem_get_playback_dB(g_mixer_elem, 0, &actual_hdb) != 0) {
        return std::numeric_limits<double>::quiet_NaN();
    }
    return static_cast<jdouble>(static_cast<double>(actual_hdb) / 100.0);
}

}  // extern "C"
