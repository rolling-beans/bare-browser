#!/usr/bin/env python3
"""Read-only DEX class/method inventory for PrefService in an official Bare APK.

Uses Python stdlib only: never executes code from the APK and never dumps private
preferences. A DEX class may appear in any of the APK's classes*.dex files.
"""
from __future__ import annotations
import re
import struct
import sys
import zipfile

CLASS = "Lorg/chromium/components/prefs/PrefService;"
TARGETS = {
    CLASS,
    "Lorg/chromium/chrome/browser/autofill/AutofillClientProviderUtils;",
}


def u4(data: bytes, off: int) -> int:
    return struct.unpack_from("<I", data, off)[0]


def uleb(data: bytes, off: int) -> tuple[int, int]:
    value = 0
    for shift in range(0, 35, 7):
        b = data[off]
        off += 1
        value |= (b & 0x7F) << shift
        if not (b & 0x80):
            return value, off
    raise ValueError("Invalid ULEB128")


def inspect_dex(data: bytes, file_name: str) -> int:
    if data[:4] != b"dex\n":
        return 0
    strings_size, strings_off = u4(data, 56), u4(data, 60)
    types_size, types_off = u4(data, 64), u4(data, 68)
    proto_size, proto_off = u4(data, 72), u4(data, 76)
    method_size, method_off = u4(data, 88), u4(data, 92)
    classes_size, classes_off = u4(data, 96), u4(data, 100)
    # Reading strings lazily avoids decoding thousands of irrelevant values.
    string_cache: dict[int, str] = {}
    def string(index: int) -> str:
        if index not in string_cache:
            off = u4(data, strings_off + 4 * index)
            _, off = uleb(data, off)
            end = data.index(0, off)
            string_cache[index] = data[off:end].decode("utf-8", errors="replace")
        return string_cache[index]
    def descriptor(type_idx: int) -> str:
        return string(u4(data, types_off + 4 * type_idx))
    class_idx_to_name = {
        i: descriptor(i) for i in range(types_size)
        if descriptor(i) in TARGETS
    }
    if not class_idx_to_name:
        return 0
    num_found = 0
    for c in range(classes_size):
        off = classes_off + c * 32
        class_idx = u4(data, off)
        if class_idx not in class_idx_to_name:
            continue
        num_found += 1
        class_data_off = u4(data, off + 24)
        print(f"CLASS {class_idx_to_name[class_idx]} IN {file_name}", flush=True)
        if not class_data_off:
            print("  (no encoded class data)")
            continue
        pos = class_data_off
        counts = []
        for _ in range(4):
            x, pos = uleb(data, pos)
            counts.append(x)
        for num in counts[:2]:
            for _ in range(num):
                _, pos = uleb(data, pos)
                _, pos = uleb(data, pos)
        for kind, num in zip(("direct", "virtual"), counts[2:]):
            method_idx = 0
            for _ in range(num):
                delta, pos = uleb(data, pos)
                method_idx += delta
                _, pos = uleb(data, pos)
                code_off, pos = uleb(data, pos)
                if method_idx >= method_size:
                    raise ValueError("Method index outside DEX table")
                meth_off = method_off + method_idx * 8
                declaring, proto_idx, name_idx = struct.unpack_from("<HHI", data, meth_off)
                p = proto_off + proto_idx * 12
                return_type = descriptor(u4(data, p + 4))
                param_off = u4(data, p + 8)
                args = []
                if param_off:
                    n = u4(data, param_off)
                    args = [descriptor(struct.unpack_from("<H", data, param_off + 4 + 2 * i)[0]) for i in range(n)]
                print(f"  {kind}: {string(name_idx)}({', '.join(args)}) -> {return_type} code_off={code_off}")
    return num_found


def main(apk: str) -> None:
    found = 0
    with zipfile.ZipFile(apk) as archive:
        dex_names = sorted(
            n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n)
        )
        print(f"DEX count: {len(dex_names)}", flush=True)
        for name in dex_names:
            found += inspect_dex(archive.read(name), name)
    if not found:
        sys.exit("TARGET_CLASS_NOT_FOUND")
    print(f"Found {found} target class definitions")


if __name__ == "__main__":
    main(sys.argv[1])
