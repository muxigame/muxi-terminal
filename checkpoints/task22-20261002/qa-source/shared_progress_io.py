"""Read QA progress while allowing Windows atomic replacement; no game controls."""
import ctypes,os,msvcrt,time
from pathlib import Path
def _read_once(path,encoding='utf-8'):
 k=ctypes.WinDLL('kernel32',use_last_error=True)
 k.CreateFileW.argtypes=[ctypes.c_wchar_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p,ctypes.c_ulong,ctypes.c_ulong,ctypes.c_void_p]
 k.CreateFileW.restype=ctypes.c_void_p;k.CloseHandle.argtypes=[ctypes.c_void_p]
 handle=k.CreateFileW(str(Path(path).resolve()),0x80000000,7,None,3,0x80,None)
 if handle==ctypes.c_void_p(-1).value:raise ctypes.WinError(ctypes.get_last_error())
 try:fd=msvcrt.open_osfhandle(handle,os.O_RDONLY|os.O_BINARY)
 except BaseException:k.CloseHandle(handle);raise
 with os.fdopen(fd,'r',encoding=encoding) as stream:return stream.read()

def read_shared_text(path,encoding='utf-8'):
 for attempt in range(20):
  try:return _read_once(path,encoding)
  except OSError as error:
   if getattr(error,'winerror',None) not in (2,3,5,32) or attempt==19:raise
   time.sleep(.005)
