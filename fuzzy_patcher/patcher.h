#ifndef PATCHER_H
#define PATCHER_H

#ifdef __cplusplus
extern "C" {
#endif

extern int g_patcher_fuzzLevel;
#ifdef __cplusplus
extern bool g_patcher_verbose;
#else
extern unsigned char g_patcher_verbose;
#endif

void init_diff_byte_rating();
void diffFiles(const char* origPath, const char* patchedPath, const char* deltaPath);
int patchFiles(const char* origPath, const char* patchedPath, const char* deltaPath);

#ifdef __cplusplus
}
#endif

#endif //PATCHER_H