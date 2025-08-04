#include <stdio.h>
#include <stdbool.h>
#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif //_WIN32

#include "legacyfixnative.h"

#ifdef _WIN32
WNDPROC g_pOriginalWndProc = NULL;

int g_nDeltaX = 0;
int g_nDeltaY = 0;

LRESULT CALLBACK WndProc_Hook( HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam )
{
	if ( msg == WM_INPUT )
	{
		UINT cbSize = 0;
		GetRawInputData( ( HRAWINPUT )lParam, RID_INPUT, NULL, &cbSize, sizeof( RAWINPUTHEADER ) );

		BYTE *pData = ( BYTE * )_alloca( cbSize );
		if ( GetRawInputData( ( HRAWINPUT )lParam, RID_INPUT, pData, &cbSize, sizeof( RAWINPUTHEADER ) ) == cbSize )
		{
			RAWINPUT *pRaw = ( RAWINPUT * )pData;
			if ( pRaw->header.dwType == RIM_TYPEMOUSE )
			{
				g_nDeltaX += pRaw->data.mouse.lLastX;
				g_nDeltaY += pRaw->data.mouse.lLastY;
			}
		}
	}

	return CallWindowProc( g_pOriginalWndProc, hWnd, msg, wParam, lParam );
}
#endif //_WIN32

DLLEXPORT void InstallRawInputHook( void )
{
#ifdef _WIN32
	DWORD dwJavaPid = GetCurrentProcessId();
	HWND hWnd = FindWindow( NULL, NULL );
	while ( hWnd )
	{
		DWORD dwWindowPid;
		GetWindowThreadProcessId( hWnd, &dwWindowPid );
		if ( dwWindowPid == dwJavaPid )
		{
			RAWINPUTDEVICE rid;
			rid.usUsagePage = 0x01;
			rid.usUsage = 0x02;
			rid.dwFlags = RIDEV_INPUTSINK;
			rid.hwndTarget = hWnd;

			if ( RegisterRawInputDevices( &rid, 1, sizeof( rid ) ) )
			{
				g_pOriginalWndProc = ( WNDPROC )( LONG_PTR )GetWindowLongPtr( hWnd, GWLP_WNDPROC );
				SetWindowLongPtr( hWnd, GWLP_WNDPROC, ( LONG_PTR )WndProc_Hook );
			}

			break;
		}
		hWnd = GetNextWindow( hWnd, GW_HWNDNEXT );
	}
#endif //_WIN32
}

DLLEXPORT int GetRawDeltaX( void )
{
#ifndef _WIN32
	return 0;
#else
	int nTemp = g_nDeltaX;
	g_nDeltaX = 0;
	return nTemp;
#endif //!_WIN32
}

DLLEXPORT int GetRawDeltaY( void )
{
#ifndef _WIN32
	return 0;
#else
	int nTemp = -g_nDeltaY;
	g_nDeltaY = 0;
	return nTemp;
#endif //!_WIN32
}

DLLEXPORT bool BIsWndProcHooked( void )
{
#ifndef _WIN32
	return false;
#else
	return g_pOriginalWndProc != NULL;
#endif //!_WIN32
}