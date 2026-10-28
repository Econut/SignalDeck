/*
 * SignalDeck - Copyright (C) 2026 IIMacGyverII
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Free software under the GNU General Public License v3 or later, with the additional terms in COPYRIGHT
 * (keep this notice and the author credit, mark modified versions, no rights to the SignalDeck name or icon).
 *
 * sdtrace: the device report's serial sniffer. Run through su, it attaches to every thread of the stock walkie-talkie
 * app with ptrace and prints what the app does on serial ports: the open, the line settings it sets (TCSETS), and the
 * bytes it writes and reads, with times. It only watches: no system call is changed, and it detaches when time is up.
 *
 *   sdtrace -n <process name> | -p <pid>  [-t <ms>] [-f <path prefix>] [-w <ms to wait for the process>] [-m <data lines>]
 *
 * -n waits for the process to appear (start the app after sdtrace), so the app's start-up is seen from its first call.
 * Output lines: "<ms> <tid> open <path> -> fd N", "<ms> <tid> set fd N 9600 8N1 ...", "<ms> <tid> W fd N n: hex |text|".
 */
#define _GNU_SOURCE
#include <dirent.h>
#include <elf.h>
#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ptrace.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#ifndef PTRACE_SEIZE
#define PTRACE_SEIZE 0x4206
#define PTRACE_INTERRUPT 0x4207
#define PTRACE_LISTEN 0x4208
#endif
#ifndef PTRACE_EVENT_STOP
#define PTRACE_EVENT_STOP 128
#endif

static struct timespec t0;
static double ms(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC, &t); return (t.tv_sec - t0.tv_sec) * 1e3 + (t.tv_nsec - t0.tv_nsec) / 1e6; }

/* system call numbers: 32-bit ARM EABI and the 64-bit generic table */
enum { K_OTHER, K_OPEN, K_CLOSE, K_READ, K_WRITE, K_IOCTL, K_DUP };
static int kind32(long nr) {
    switch (nr) {
    case 5: case 322: return K_OPEN;            /* open, openat */
    case 6: return K_CLOSE;
    case 3: case 180: return K_READ;            /* read, pread64 */
    case 4: case 181: return K_WRITE;           /* write, pwrite64 */
    case 54: return K_IOCTL;
    case 41: case 63: case 358: return K_DUP;   /* dup, dup2, dup3 */
    }
    return K_OTHER;
}
static int kind64(long nr) {
    switch (nr) {
    case 56: return K_OPEN;
    case 57: return K_CLOSE;
    case 63: case 67: return K_READ;
    case 64: case 68: return K_WRITE;
    case 29: return K_IOCTL;
    case 23: case 24: return K_DUP;
    }
    return K_OTHER;
}

typedef struct { int tid, used, insys, is64; long nr; unsigned long a[4]; } Task;
#define MAXT 512
static Task tasks[MAXT];
static Task *task(int tid, int create) {
    for (int i = 0; i < MAXT; i++) if (tasks[i].used && tasks[i].tid == tid) return &tasks[i];
    if (!create) return NULL;
    for (int i = 0; i < MAXT; i++) if (!tasks[i].used) { memset(&tasks[i], 0, sizeof(Task)); tasks[i].used = 1; tasks[i].tid = tid; return &tasks[i]; }
    return NULL;
}

/* fd -> 0 unknown, 1 a matching port, 2 something else; one process, so one table */
#define MAXFD 4096
static unsigned char fdk[MAXFD];
static int pid;
static const char *prefix = "/dev/tty";
static int fdMatches(int fd, char *path, size_t n) {
    if (fd < 0 || fd >= MAXFD) return 0;
    if (fdk[fd] == 0 || path) {
        char l[64]; snprintf(l, sizeof l, "/proc/%d/fd/%d", pid, fd);
        char buf[256]; ssize_t r = readlink(l, buf, sizeof buf - 1);
        if (r < 0) return 0;
        buf[r] = 0;
        fdk[fd] = strncmp(buf, prefix, strlen(prefix)) == 0 ? 1 : 2;
        if (path) snprintf(path, n, "%s", buf);
    }
    return fdk[fd] == 1;
}

static int readMem(int tid, unsigned long addr, void *dst, size_t n) {
    struct iovec l = { dst, n }, r = { (void *) addr, n };
    if (process_vm_readv(tid, &l, 1, &r, 1, 0) == (ssize_t) n) return 0;
    for (size_t i = 0; i < n; i += sizeof(long)) {           /* fallback: word by word */
        errno = 0; long w = ptrace(PTRACE_PEEKDATA, tid, (void *) (addr + i), NULL);
        if (errno) return -1;
        memcpy((char *) dst + i, &w, n - i < sizeof(long) ? n - i : sizeof(long));
    }
    return 0;
}

static long maxLines = 400, lines, quietBytes;            /* a PCM-over-UART phone would flood the report: list the first lines, count the rest */
static void dump(int tid, const char *dir, int fd, unsigned long addr, long n) {
    if (n <= 0) return;
    if (++lines > maxLines) { quietBytes += n; return; }
    unsigned char b[512]; long k = n > (long) sizeof b ? (long) sizeof b : n;
    if (readMem(tid, addr, b, (size_t) k)) { printf("%.1f %d %s fd %d %ld: (unreadable)\n", ms(), tid, dir, fd, n); return; }
    printf("%.1f %d %s fd %d %ld:", ms(), tid, dir, fd, n);
    for (long i = 0; i < k; i++) printf(" %02x", b[i]);
    printf(" |");
    for (long i = 0; i < k; i++) putchar(b[i] >= 0x20 && b[i] < 0x7f ? b[i] : '.');
    printf("|%s\n", k < n ? " (first 512 bytes)" : "");
}

static const char *baud(unsigned c) {
    static const struct { unsigned v; const char *s; } t[] = {
        { 0000015, "9600" }, { 0000016, "19200" }, { 0000017, "38400" }, { 0010001, "57600" }, { 0010002, "115200" },
        { 0010003, "230400" }, { 0010004, "460800" }, { 0010007, "921600" }, { 0000014, "4800" }, { 0000013, "2400" } };
    for (size_t i = 0; i < sizeof t / sizeof t[0]; i++) if ((c & 0010017) == t[i].v) return t[i].s;
    return "?";
}

static long polls;                                         /* FIONREAD: the W6's app asks every 150 ms; counted, not listed */
static void ioctlOut(int tid, int fd, unsigned long cmd, unsigned long arg, long ret) {
    unsigned c = (unsigned) cmd;
    if (c == 0x541b) { polls++; return; }
    if (c >= 0x5401 && c <= 0x5404) {                       /* TCGETS, TCSETS, TCSETSW, TCSETSF */
        uint32_t t[4];
        if (!readMem(tid, arg, t, sizeof t)) {
            unsigned cf = t[2];
            printf("%.1f %d %s fd %d %s %c%c%c%s%s iflag=%#x oflag=%#x cflag=%#x lflag=%#x -> %ld\n", ms(), tid,
                   c == 0x5401 ? "get" : "set", fd, baud(cf), "5678"[(cf >> 4) & 3], (cf & 0400) ? ((cf & 01000) ? 'O' : 'E') : 'N',
                   (cf & 0100) ? '2' : '1', (cf & 020000000000u) ? " rtscts" : "", (t[3] & 2) ? " icanon" : " raw",
                   t[0], t[1], cf, t[3], ret);
            return;
        }
    }
    printf("%.1f %d ioctl fd %d %#x -> %ld\n", ms(), tid, fd, c, ret);
}

/* registers: 18 words = a 32-bit task (r0..r15, cpsr, orig_r0), else 64-bit (x0..x30, sp, pc, pstate) */
static int regs(int tid, unsigned long *nr, unsigned long a[4], long *ret, int *is64) {
    uint64_t r64[34]; struct iovec io = { r64, sizeof r64 };
    if (ptrace(PTRACE_GETREGSET, tid, (void *) NT_PRSTATUS, &io) < 0) return -1;
    if (io.iov_len == 18 * 4) {
        uint32_t *r = (uint32_t *) r64;
        *nr = r[7]; for (int i = 0; i < 4; i++) a[i] = r[i]; *ret = (int32_t) r[0]; *is64 = 0;
    } else {
        *nr = r64[8]; for (int i = 0; i < 4; i++) a[i] = r64[i]; *ret = (long) r64[0]; *is64 = 1;
    }
    return 0;
}

static void onSyscall(int tid) {
    Task *t = task(tid, 1); if (!t) return;
    unsigned long nr, a[4]; long ret; int is64;
    if (regs(tid, &nr, a, &ret, &is64)) return;
    if (!t->insys) {                                        /* entry: keep the arguments (r0 becomes the result) */
        t->insys = 1; t->nr = (long) nr; t->is64 = is64; memcpy(t->a, a, sizeof t->a);
        int k = is64 ? kind64(t->nr) : kind32(t->nr);
        if (k == K_CLOSE && (int) a[0] >= 0 && (int) a[0] < MAXFD) {
            if (fdk[(int) a[0]] == 1) printf("%.1f %d close fd %d\n", ms(), tid, (int) a[0]);
            fdk[(int) a[0]] = 0;
        }
        return;
    }
    t->insys = 0;
    int k = t->is64 ? kind64(t->nr) : kind32(t->nr);
    int fd = (int) t->a[0];
    switch (k) {
    case K_OPEN: case K_DUP:
        if (ret >= 0 && ret < MAXFD) {
            fdk[ret] = 0; char path[256] = "";
            if (fdMatches((int) ret, path, sizeof path)) printf("%.1f %d %s %s -> fd %ld\n", ms(), tid, k == K_OPEN ? "open" : "dup", path, ret);
        }
        break;
    case K_WRITE: if (fdMatches(fd, NULL, 0)) dump(tid, "W", fd, t->a[1], ret); break;
    case K_READ: if (fdMatches(fd, NULL, 0)) dump(tid, "R", fd, t->a[1], ret); break;
    case K_IOCTL: if (fdMatches(fd, NULL, 0)) ioctlOut(tid, fd, t->a[1], t->a[2], ret); break;
    }
}

static int findPid(const char *name) {
    DIR *d = opendir("/proc"); if (!d) return 0;
    struct dirent *e; int found = 0;
    while (!found && (e = readdir(d))) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        char p[64], c[256] = ""; snprintf(p, sizeof p, "/proc/%s/cmdline", e->d_name);
        FILE *f = fopen(p, "r"); if (!f) continue;
        size_t n = fread(c, 1, sizeof c - 1, f); fclose(f); c[n] = 0;
        if (strcmp(c, name) == 0) found = atoi(e->d_name);
    }
    closedir(d);
    return found;
}

static int attachAll(void) {
    char p[64]; snprintf(p, sizeof p, "/proc/%d/task", pid);
    DIR *d = opendir(p); if (!d) return 0;
    struct dirent *e; int n = 0;
    while ((e = readdir(d))) {
        int tid = atoi(e->d_name); if (tid <= 0 || task(tid, 0)) continue;
        if (ptrace(PTRACE_SEIZE, tid, NULL, (void *) (long) (PTRACE_O_TRACESYSGOOD | PTRACE_O_TRACECLONE)) == 0) {
            task(tid, 1); ptrace(PTRACE_INTERRUPT, tid, NULL, NULL); n++;
        }
    }
    closedir(d);
    return n;
}

int main(int argc, char **argv) {
    const char *name = NULL; long limit = 15000, wait = 20000;
    for (int i = 1; i + 1 < argc; i += 2) {
        if (!strcmp(argv[i], "-n")) name = argv[i + 1];
        else if (!strcmp(argv[i], "-p")) pid = atoi(argv[i + 1]);
        else if (!strcmp(argv[i], "-t")) limit = atol(argv[i + 1]);
        else if (!strcmp(argv[i], "-f")) prefix = argv[i + 1];
        else if (!strcmp(argv[i], "-w")) wait = atol(argv[i + 1]);
        else if (!strcmp(argv[i], "-m")) maxLines = atol(argv[i + 1]);
    }
    setvbuf(stdout, NULL, _IOLBF, 0);
    clock_gettime(CLOCK_MONOTONIC, &t0);
    if (name) { while (!(pid = findPid(name)) && ms() < wait) usleep(1000); }
    if (pid <= 0) { printf("sdtrace: process %s not found\n", name ? name : "?"); return 1; }
    printf("sdtrace: pid %d, watching %s for %ld ms\n", pid, prefix, limit);
    int n = attachAll();
    if (n == 0) { printf("sdtrace: attach failed: %s\n", strerror(errno)); return 1; }
    printf("%.1f sdtrace: attached to %d threads\n", ms(), n);
    double end = ms() + limit; int stopping = 0;
    for (;;) {
        if (!stopping && ms() >= end) {                     /* time is up: stop every thread, then detach it */
            stopping = 1;
            for (int i = 0; i < MAXT; i++) if (tasks[i].used) ptrace(PTRACE_INTERRUPT, tasks[i].tid, NULL, NULL);
        }
        int st; int tid = waitpid(-1, &st, __WALL);
        if (tid < 0) break;                                 /* no tracee left */
        if (WIFEXITED(st) || WIFSIGNALED(st)) { Task *t = task(tid, 0); if (t) t->used = 0; if (tid == pid) printf("%.1f sdtrace: the app exited\n", ms()); continue; }
        if (!WIFSTOPPED(st)) continue;
        int sig = WSTOPSIG(st), ev = st >> 16;
        Task *t = task(tid, 1);
        if (stopping) {
            if (ptrace(PTRACE_DETACH, tid, NULL, (void *) (long) (sig == (SIGTRAP | 0x80) || ev ? 0 : sig)) == 0 && t) t->used = 0;
            int left = 0; for (int i = 0; i < MAXT; i++) left += tasks[i].used;
            if (!left) break;
            continue;
        }
        if (sig == (SIGTRAP | 0x80)) { onSyscall(tid); ptrace(PTRACE_SYSCALL, tid, NULL, NULL); }
        else if (ev == PTRACE_EVENT_STOP) {
            if (sig == SIGSTOP || sig == SIGTSTP || sig == SIGTTIN || sig == SIGTTOU) ptrace(PTRACE_LISTEN, tid, NULL, NULL);   /* group stop */
            else ptrace(PTRACE_SYSCALL, tid, NULL, NULL);
        }
        else if (ev) ptrace(PTRACE_SYSCALL, tid, NULL, NULL);    /* clone / exec events: the new thread is traced already */
        else ptrace(PTRACE_SYSCALL, tid, NULL, (void *) (long) sig);   /* a signal for the app: deliver it */
    }
    if (lines > maxLines) printf("%.1f sdtrace: %ld more reads/writes (%ld bytes) not listed (limit -m %ld)\n", ms(), lines - maxLines, quietBytes, maxLines);
    printf("%.1f sdtrace: detached; %ld waiting-byte polls (FIONREAD) not listed\n", ms(), polls);
    return 0;
}
