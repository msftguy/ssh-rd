#ifdef WIN32
#pragma once

#include <WinSock2.h>
#include <stdio.h>

static int varInThisModule;

__inline int itmd_load(char* errorBuf, size_t cbErrorBuf) {
	WCHAR wbuf[MAX_PATH];
	DWORD cbBuf = sizeof(wbuf);
	HRESULT error = S_OK;
	HMODULE hThisModule = NULL;

	//for necrophiles - XP support
	typedef LSTATUS
		(APIENTRY
		*RegGetValue_t) (
			__in HKEY    hkey,
			__in_opt LPCWSTR  lpSubKey,
			__in_opt LPCWSTR  lpValue,
			__in_opt DWORD    dwFlags,
			__out_opt LPDWORD pdwType,
			__out_bcount_part_opt(*pcbData,*pcbData) PVOID   pvData,
			__inout_opt LPDWORD pcbData
			);

	RegGetValue_t rgv = (RegGetValue_t)GetProcAddress(LoadLibraryW(L"advapi32"), "RegGetValueW");
	if  (rgv == NULL) {
		rgv = (RegGetValue_t)GetProcAddress(LoadLibraryW(L"Shlwapi"), "SHRegGetValueW");
	}
	if  (rgv == NULL) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Your OS is too fucking old!; ABORTING");
		return 1;
	}
	//End XP support 

	error = rgv(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Apple Inc.\\Apple Application Support", L"InstallDir", RRF_RT_REG_SZ|RRF_ZEROONFAILURE, NULL, (LPBYTE)wbuf, &cbBuf);
	if (ERROR_SUCCESS != error) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not locate 'Apple Application Support' folder path in registry; ABORTING");
		return 2;
	}

	SetDllDirectoryW(wbuf);

	if (!LoadLibraryW(L"ASL")) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "WARNING: Could not load ASL from '%ws'", wbuf);
	}

	if (!LoadLibraryW(L"CoreFoundation")) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not load CoreFoundation from '%ws'; ABORTING", wbuf);
		return 3;
	}

	cbBuf = sizeof(wbuf);
	error = rgv(HKEY_LOCAL_MACHINE, L"SOFTWARE\\Apple Inc.\\Apple Mobile Device Support\\Shared", L"iTunesMobileDeviceDLL", RRF_RT_REG_SZ|RRF_ZEROONFAILURE, NULL, (LPBYTE)wbuf, &cbBuf);
	if (ERROR_SUCCESS != error) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not read the 'iTunesMobileDeviceDLL' key from registry; ABORTING");
		return 4;
	}
	if (!LoadLibraryExW(wbuf, NULL, LOAD_WITH_ALTERED_SEARCH_PATH)) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not load iTunesMobileDevice from %ws; ABORTING", wbuf);
		return 5;
	}

	if (!GetModuleHandleExW(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, (LPCWSTR)&varInThisModule, &hThisModule)) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "GetModuleHandleExW() failed; ABORTING");
		return 6;
	}
	cbBuf = GetModuleFileNameW(hThisModule, wbuf, sizeof(wbuf));
	if (cbBuf == 0) {
		_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "GetModuleFileNameW() failed; ABORTING");
		return 7;
	}
	{
		wchar_t* pLastSlash = wcsrchr(wbuf, L'\\');
		if (pLastSlash == NULL) {
			_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not locate DLL folder in '%ws' path; ABORTING", wbuf);
			return 8;
		}
		*pLastSlash = L'\0';
		wcscat_s(wbuf, MAX_PATH, L"\\mux_redux.dll");
		if (!LoadLibraryW(wbuf)) {
			_snprintf_s(errorBuf, cbErrorBuf, _TRUNCATE, "Could not load mux_redux.dll using '%ws' path; ABORTING", wbuf);
			return 9;
		}
	}

	return 0;
}
#else

int itmd_load(char* errorBuf, size_t cbErrorBuf)
{
	return 0;
}

#endif //WIN32
