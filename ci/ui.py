#!/usr/bin/env python3
"""Tiny UI driver for the emulator smoke test: tap on-screen elements by their text."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
    xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
    start = xml.find("<?xml")
    if start < 0:
        return []
    try:
        root = ET.fromstring(xml[start:].strip())
    except ET.ParseError:
        return []
    return list(root.iter("node"))


def find(pattern):
    rx = re.compile(pattern, re.IGNORECASE)
    for n in nodes():
        label = n.get("text") or n.get("content-desc") or ""
        if label and rx.fullmatch(label.strip()):
            return n
    return None


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap(pattern, timeout=20.0, optional=False):
    deadline = time.time() + timeout
    while time.time() < deadline:
        n = find(pattern)
        if n is not None:
            x, y = center(n)
            adb("shell", "input", "tap", str(x), str(y))
            print(f"tapped '{pattern}' at {x},{y}")
            time.sleep(1.5)
            return True
        time.sleep(1)
    if optional:
        print(f"(optional) '{pattern}' not found")
        return False
    labels = [(n.get("text") or n.get("content-desc")) for n in nodes()]
    print(f"NOT FOUND: '{pattern}'. On screen: {[l for l in labels if l]}")
    sys.exit(1)


def wait_for(pattern, timeout=30.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if find(pattern) is not None:
            print(f"found '{pattern}'")
            return
        time.sleep(1)
    labels = [(n.get("text") or n.get("content-desc")) for n in nodes()]
    print(f"NOT FOUND: '{pattern}'. On screen: {[l for l in labels if l]}")
    sys.exit(1)


if __name__ == "__main__":
    cmd, pattern = sys.argv[1], sys.argv[2]
    if cmd == "tap":
        tap(pattern)
    elif cmd == "tap-optional":
        tap(pattern, timeout=6, optional=True)
    elif cmd == "wait":
        wait_for(pattern)
