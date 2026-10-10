// JNI bridge that embeds byedpi (https://github.com/hufrea/byedpi) as an
// in-process SOCKS proxy. byedpi normally runs as a CLI (`ciadpi`); we reuse its
// argument parser and event loop directly.
//
// Lifecycle:
//   nativeStart(args)  -> parse_args + init + run  (blocks in the event loop)
//   nativeStop()       -> shutdown(server_fd) breaks the loop, run() returns
//
// byedpi keeps all configuration in the global `params`. To support starting the
// proxy more than once in a single process (VPN reconnect), we snapshot the
// pristine `params` on first use and restore it before every start.

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <pthread.h>
#include <sys/socket.h>

#include "byedpi/params.h"
#include "byedpi/proxy.h"

// Provided by byedpi (main.c / proxy.c).
extern int parse_args(int argc, char **argv);
extern int init(void);
extern void clear_params(char *line, char **argv);
extern void dump_all_cache(void);
static struct params params_backup;
static int backup_saved = 0;
static pthread_mutex_t state_mutex = PTHREAD_MUTEX_INITIALIZER;
static int start_prepared = 0;
static int native_running = 0;
static int stop_requested = 0;
static int active_server_fd = -1;

JNIEXPORT jboolean JNICALL
Java_io_github_yulbax_erkin_engine_ByeDpi_nativePrepareStart(
        JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&state_mutex);
    if (native_running || start_prepared) {
        pthread_mutex_unlock(&state_mutex);
        return JNI_FALSE;
    }
    start_prepared = 1;
    stop_requested = 0;
    active_server_fd = -1;
    pthread_mutex_unlock(&state_mutex);
    return JNI_TRUE;
}

int erkin_publish_server_fd(int fd) {
    pthread_mutex_lock(&state_mutex);
    if (stop_requested || !native_running) {
        pthread_mutex_unlock(&state_mutex);
        return 0;
    }
    active_server_fd = fd;
    pthread_mutex_unlock(&state_mutex);
    return 1;
}

void erkin_clear_server_fd(int fd) {
    pthread_mutex_lock(&state_mutex);
    if (active_server_fd == fd) active_server_fd = -1;
    pthread_mutex_unlock(&state_mutex);
}

JNIEXPORT jint JNICALL
Java_io_github_yulbax_erkin_engine_ByeDpi_nativeStart(
        JNIEnv *env, jobject thiz, jobjectArray jargs) {

    pthread_mutex_lock(&state_mutex);
    if (!start_prepared || native_running) {
        pthread_mutex_unlock(&state_mutex);
        return -2;
    }
    start_prepared = 0;
    native_running = 1;
    int cancelled = stop_requested;
    pthread_mutex_unlock(&state_mutex);
    if (cancelled) {
        pthread_mutex_lock(&state_mutex);
        native_running = 0;
        pthread_mutex_unlock(&state_mutex);
        return 0;
    }

    // Snapshot the untouched defaults once; restore them on every later start so
    // a previous run's parsed state (groups, mempool pointers) does not leak.
    if (!backup_saved) {
        memcpy(&params_backup, &params, sizeof(params));
        backup_saved = 1;
    } else {
        // clear_params() frees the need_free *elements* but not the array buffer
        // itself; the restore below overwrites the pointer, so free it first to
        // avoid orphaning one need_free array per restart. free(NULL) is safe if
        // the previous run registered no cleanups.
        free(params.need_free);
        memcpy(&params, &params_backup, sizeof(params));
    }

    // Reset getopt so parse_args restarts cleanly on a subsequent invocation.
    optind = 1;
    optreset = 1;

    jsize argc = (*env)->GetArrayLength(env, jargs);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    if (!argv) {
        pthread_mutex_lock(&state_mutex);
        native_running = 0;
        pthread_mutex_unlock(&state_mutex);
        return -1;
    }
    for (jsize i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, jargs, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }

    jint result = 0;
    int status = parse_args((int) argc, argv);
    if (status) {
        result = status - 1;
    } else if (init() < 0) {
        result = -1;
    } else {
        // Blocks until nativeStop() shuts down server_fd.
        result = run(&params.laddr);
        dump_all_cache();
    }

    clear_params(NULL, NULL);
    for (jsize i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);

    pthread_mutex_lock(&state_mutex);
    active_server_fd = -1;
    native_running = 0;
    pthread_mutex_unlock(&state_mutex);
    return result;
}

JNIEXPORT void JNICALL
Java_io_github_yulbax_erkin_engine_ByeDpi_nativeStop(
        JNIEnv *env, jobject thiz) {
    pthread_mutex_lock(&state_mutex);
    stop_requested = 1;
    start_prepared = 0;
    if (active_server_fd >= 0) shutdown(active_server_fd, SHUT_RDWR);
    pthread_mutex_unlock(&state_mutex);
}
