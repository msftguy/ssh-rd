#include <stdio.h>

__declspec(dllimport) int tethered_boot(const char *ibssFile, const char *ibecFile, const char *kernelcacheFile, const char *ramdiskFile, const char *devicetreeFile);

int main()
{
	tethered_boot("iBSS.n88ap.RELEASE.dfu.p","iBEC.n88ap.RELEASE.dfu.p","kernelcache.release.n88.p","038-3713-001.dmg.ssh",0);
	return 0;
}