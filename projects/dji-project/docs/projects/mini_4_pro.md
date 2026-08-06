---
uuid: 019d82df-ca42-7324-8c10-063ef0f0ce4d
exported_at: 2026-08-06T00:40:21.725616Z
---

# Projekt: DJI Mini 4 Pro

## Opis

Hacks


**Utworzony:** 2026-04-12T18:06:37.635634+00:00

## Dokumenty

### params_v148_set.py

```
#!/usr/bin/env python3
"""
duml/params_v148_set.py - dekoder ramek SET dla protokolu Drone Hacks v1.48.

Reverse engineered z capture_v148_full69.pcap (Mini 4 Pro FW 01.00.1100).
Komunikacja DroneHacks (src=0x03) <-> FC (dst=0x0A).

PROTOKOL TRANSAKCJI SET:
    DH -> FC :  cmd 0xDF          payload=00         (pre-set probe)
    DH -> FC :  cmd 0xE3          payload=set_req    (wlasciwy SET)
    FC -> DH :  cmd 0xDF (resp)   payload=01000000   (pre-set ACK)
    FC -> DH :  cmd 0xE3 (resp)   payload=set_resp   (SET ACK + echo)

LAYOUT PAYLOAD 0xE3:
    Request (10/8/7B):  [status:4B=0] [idx:2B] [value:Nbytes]
    Response:           [status:4B]   [idx:2B] [value:Nbytes]
    Status response:    0x00000001 = OK
                        inne wartosci = blad (do dalszego RE)
"""
from __future__ import annotations

import struct
from dataclasses import dataclass, field
from typing import Optional


# Komendy (cmd_set=0x03 FLIGHT_CONTROLLER)
CMD_SET_PARAM_BY_INDEX = 0xE3
CMD_PRE_SET_PROBE = 0xDF

# Status response
STATUS_OK = 0x00000001


@dataclass
class SetRequest:
    """Ramka SET_PARAM_VALUE_BY_INDEX (request od klienta)."""
    index: int                  # uint16 indeks parametru
    raw_value: bytes            # surowa wartosc (1, 2 lub 4 bajty)
    size: int                   # rozmiar wartosci w bajtach
    raw_payload: bytes = field(default=b'')

    @property
    def value_uint(self) -> int:
        """Interpretacja wartosci jako unsigned integer."""
        return int.from_bytes(self.raw_value, 'little', signed=False)

    @property
    def value_int(self) -> int:
        """Interpretacja wartosci jako signed integer."""
        return int.from_bytes(self.raw_value, 'little', signed=True)

    @property
    def value_float(self) -> Optional[float]:
        """Interpretacja jako float (tylko gdy size=4)."""
        if self.size != 4:
            return None
        return struct.unpack('<f', self.raw_value)[0]


@dataclass
class SetResponse:
    """Ramka SET_PARAM_VALUE_BY_INDEX (response od FC)."""
    status: int                 # uint32 status (1 = OK)
    index: int                  # uint16 indeks parametru
    raw_value: bytes            # echo wartosci
    size: int
    raw_payload: bytes = field(default=b'')

    @property
    def is_ok(self) -> bool:
        return self.status == STATUS_OK

    @property
    def value_uint(self) -> int:
        return int.from_bytes(self.raw_value, 'little', signed=False)

    @property
    def value_float(self) -> Optional[float]:
        if self.size != 4:
            return None
        return struct.unpack('<f', self.raw_value)[0]


def parse_set_request(payload: bytes) -> Optional[SetRequest]:
    """Sparsuj payload ramki 0xE3 z flag=0x80 (request).

    Zwraca SetRequest lub None gdy payload za krotki/niepoprawny.
    """
    if len(payload) < 7:
        return None

    status = struct.unpack('<I', payload[0:4])[0]
    if status != 0:
        # Request powinien miec status=0
        return None

    index = struct.unpack('<H', payload[4:6])[0]
    raw_value = bytes(payload[6:])
    size = len(raw_value)

    if size not in (1, 2, 4, 8):
        return None

    return SetRequest(index=index, raw_value=raw_value,
                      size=size, raw_payload=bytes(payload))


def parse_set_response(payload: bytes) -> Optional[SetResponse]:
    """Sparsuj payload ramki 0xE3 z flag=0x40 (response)."""
    if len(payload) < 7:
        return None

    status = struct.unpack('<I', payload[0:4])[0]
    index = struct.unpack('<H', payload[4:6])[0]
    raw_value = bytes(payload[6:])
    size = len(raw_value)

    if size not in (1, 2, 4, 8):
        return None

    return SetResponse(status=status, index=index, raw_value=raw_value,
                       size=size, raw_payload=bytes(payload))


def build_set_request(index: int, value: int | float, size: int,
                      is_float: bool = False) -> bytes:
    """Zbuduj payload requestu SET dla parametru.

    Args:
        index: indeks parametru (0..65535)
        value: nowa wartosc
        size: rozmiar w bajtach (1, 2, 4)
        is_float: True jezeli wartosc to float (tylko dla size=4)

    Returns:
        bytes zawiera 4B status (0) + 2B idx + N B wartosci
    """
    if not (0 <= index <= 0xFFFF):
        raise ValueError(f"Indeks poza zakresem uint16: {index}")
    if size not in (1, 2, 4):
        raise ValueError(f"Niewspierany size: {size}")

    if is_float and size == 4:
        value_bytes = struct.pack('<f', value)
    else:
        value_bytes = int(value).to_bytes(size, 'little', signed=(value < 0))

    return b'\x00\x00\x00\x00' + struct.pack('<H', index) + value_bytes


# Test gdy uruchomione bezposrednio
if __name__ == "__main__":
    # Test parsowania ramek z capture_v148_full69
    test_cases = [
        # (hex_payload, idx, expected_size, expected_float_value)
        ("0000000050040000f041", 1104, 4, 30.0),
        ("0000000056040000a040", 1110, 4, 5.0),
        ("0000000057040000a0c0", 1111, 4, -5.0),
        ("00000000b50401",       1205, 1, None),  # UINT8 = 1
        ("00000000b50400",       1205, 1, None),  # UINT8 = 0
    ]

    print("Test parsowania SetRequest:")
    for hex_str, exp_idx, exp_size, exp_float in test_cases:
        payload = bytes.fromhex(hex_str)
        req = parse_set_request(payload)
        assert req is not None, f"Failed to parse {hex_str}"
        assert req.index == exp_idx, f"idx {req.index} != {exp_idx}"
        assert req.size == exp_size, f"size {req.size} != {exp_size}"
        if exp_float is not None:
            assert abs(req.value_float - exp_float) < 0.001
            print(f"  idx={req.index:>4}  size={req.size}  "
                  f"float={req.value_float:>8.2f}  OK")
        else:
            print(f"  idx={req.index:>4}  size={req.size}  "
                  f"uint={req.value_uint:>3}  OK")

    print("\nTest budowania SetRequest:")
    # Odbuduj z bazy
    built = build_set_request(1104, 30.0, 4, is_float=True)
    expected = bytes.fromhex("0000000050040000f041")
    assert built == expected, f"{built.hex()} != {expected.hex()}"
    print(f"  build(1104, 30.0, FLOAT) -> {built.hex()}  OK")

    built = build_set_request(1205, 1, 1, is_float=False)
    expected = bytes.fromhex("00000000b50401")
    assert built == expected
    print(f"  build(1205, 1, UINT8) -> {built.hex()}  OK")

    print("\nWszystkie testy PASS!")

```

### duml_client.py

```
#!/usr/bin/env python3
"""
duml_client.py - kompletny klient DUML dla DJI Mini 4 Pro FW 01.00.1100.

Funkcjonalnie rownowazny Drone Hacks v1.48:
  - Heartbeat 250ms
  - Init sequence (0xDF + 0xE0 x2)
  - Odpowiada na pytania FC z lokalnej bazy 804 parametrow
  - SET parametrow przez 0xE3
  - Audit log JSONL kazdej operacji

Architektura:
  Watek MAIN          - CLI / orkiestracja
  Watek HEARTBEAT     - co 250ms wysyla 0x43 OSD do dst=0x0A i 0xAA
  Watek RX            - czyta EP_IN 0x85, parsuje ramki, wkalda na queue
  Watek RESPONDER     - bierze ramki FC->DH (cmd 0xE1/0xE2 flags=0x40)
                        i odpowiada z bazy
  Audit log           - JSONL z timestampem, src, dst, cmd, payload, decoded

Uzycie:
  python duml_client.py monitor          # tylko podsluchuj
  python duml_client.py serve            # pelny serwer (heartbeat + responses)
  python duml_client.py set <idx> <val>  # pojedynczy SET
  python duml_client.py list             # lista parametrow
  python duml_client.py search <pattern> # szukaj po nazwie
"""
from __future__ import annotations

import argparse
import json
import os
import queue
import struct
import sys
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional

import ctypes.util
import ctypes
_OUR_DLL = r'C:\Python313-64\libusb0.dll'
if os.path.isfile(_OUR_DLL):
    _orig_find = ctypes.util.find_library
    ctypes.util.find_library = (lambda n:
        _OUR_DLL if (n and ('libusb0' in n.lower() or n.lower() in ('usb-0.1.dll', 'usb.dll')))
        else _orig_find(n))

import usb.core
import usb.util
import usb.backend.libusb0


# === KONSTANTY DUML ===

DJI_VID = 0x2CA3
MINI4_PID = 0x0020
DUML_INTERFACE_NUM = 4
EP_OUT = 0x04
EP_IN = 0x85

SRC_DH = 0x03         # nasza tozsamosc (DroneHacks-like)
DST_FC = 0x0A         # Flight Controller
DST_FC_BCAST = 0xAA   # broadcast do FC

CMD_SET_OSD = (0x03, 0x43)        # heartbeat OSD General Data
CMD_SET_PROBE = (0x03, 0xDF)      # pre-init probe
CMD_SET_INIT = (0x03, 0xE0)       # init sequence
CMD_SET_PARAM_INFO = (0x03, 0xE1) # parameter info request/response
CMD_SET_PARAM_VAL = (0x03, 0xE2)  # parameter value request/response
CMD_SET_PARAM_SET = (0x03, 0xE3)  # SET parameter

FLAG_REQUEST = 0x40       # FC -> DH (FC zadaje pytanie)
FLAG_RESPONSE = 0x80      # DH -> FC (DH odpowiada lub inicjuje)
FLAG_BROADCAST = 0x00     # heartbeat


# Statyczny payload heartbeat (z capture #69)
HEARTBEAT_PAYLOAD = bytes.fromhex(
    "000000000000000000000000000000000000000000000000"
    "ffff00009a058600007080000000000031010000000f8a20"
    "5d00008900000000000000000000000000180000000700"
)
INIT_E0_PAYLOAD_1 = bytes.fromhex("000000006eb1b2b999050000")
INIT_E0_PAYLOAD_2 = bytes.fromhex("0900")


# === CRC ===

def crc8_dji(data, init=0x77):
    crc = init
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8C if crc & 1 else crc >> 1
    return crc & 0xFF


def crc16_kermit(data, init=0x3692):
    crc = init
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8408 if crc & 1 else crc >> 1
    return crc & 0xFFFF


# === FRAME ===

@dataclass
class DumlFrame:
    src: int
    dst: int
    seq: int
    flags: int
    cmd_set: int
    cmd_id: int
    payload: bytes
    raw: bytes = field(default=b'')

    def __repr__(self):
        return (f"<Frame {self.src:02X}->{self.dst:02X} "
                f"seq={self.seq:04X} flags={self.flags:02X} "
                f"cs={self.cmd_set:02X} ci={self.cmd_id:02X} "
                f"plen={len(self.payload)}>")


def build_frame(src, dst, seq, flags, cmd_set, cmd_id, payload):
    total_len = 13 + len(payload)
    version = 1
    len_lo = total_len & 0xFF
    len_hi_ver = ((total_len >> 8) & 0x03) | (version << 2)
    header3 = bytes([0x55, len_lo, len_hi_ver])
    crc8 = crc8_dji(header3)
    body = (header3 + bytes([crc8]) + bytes([src, dst]) +
            struct.pack('<H', seq) +
            bytes([flags, cmd_set, cmd_id]) + payload)
    crc16 = crc16_kermit(body)
    return body + struct.pack('<H', crc16)


def parse_frame(buf, start=0):
    """Zwraca (offset, length, frame) lub (None, 0, None)."""
    i = start
    while i < len(buf) - 12:
        if buf[i] == 0x55:
            length = buf[i+1] | ((buf[i+2] & 0x03) << 8)
            if 13 <= length <= 1023 and i + length <= len(buf):
                if crc8_dji(buf[i:i+3]) == buf[i+3]:
                    crc16_actual = crc16_kermit(buf[i:i+length-2])
                    crc16_expected = struct.unpack('<H', buf[i+length-2:i+length])[0]
                    if crc16_actual == crc16_expected:
                        return i, length, DumlFrame(
                            src=buf[i+4], dst=buf[i+5],
                            seq=struct.unpack('<H', buf[i+6:i+8])[0],
                            flags=buf[i+8],
                            cmd_set=buf[i+9], cmd_id=buf[i+10],
                            payload=bytes(buf[i+11:i+length-2]),
                            raw=bytes(buf[i:i+length]),
                        )
        i += 1
    return None, 0, None


# === BAZA PARAMETROW ===

class ParamDB:
    """Lokalna baza 800+ parametrow z capture #69."""

    def __init__(self, db_path):
        with open(db_path, 'r', encoding='utf-8') as fp:
            data = json.load(fp)
        self.firmware = data['firmware']
        self.params_by_idx = {p['index']: p for p in data['parameters']}
        self.params_by_name = {p['name']: p for p in data['parameters']}

    def __len__(self):
        return len(self.params_by_idx)

    def get_by_index(self, idx):
        return self.params_by_idx.get(idx)

    def get_by_name(self, name):
        return self.params_by_name.get(name)

    def search(self, pattern):
        pattern = pattern.lower()
        return [p for p in self.params_by_idx.values()
                if pattern in p['name'].lower()]


def encode_value(value, type_, size):
    """Zakoduj wartosc do bytes wedlug type/size."""
    if type_ == 8 and size == 4:
        return struct.pack('<f', float(value))
    if type_ == 9 and size == 8:
        return struct.pack('<d', float(value))
    # int* (type 0/1/2/4/5)
    return int(value).to_bytes(size, 'little', signed=False)


def decode_value(raw_bytes, type_, size):
    """Zdekoduj raw bytes do wartosci wedlug type/size."""
    if type_ == 8 and size == 4:
        return struct.unpack('<f', raw_bytes[:4])[0]
    if type_ == 9 and size == 8:
        return struct.unpack('<d', raw_bytes[:8])[0]
    return int.from_bytes(raw_bytes[:size], 'little', signed=False)


def build_param_info_response(param):
    """Zbuduj payload dla CMD_SET_PARAM_INFO response (DH->FC, flags=0x80).

    Format: [status:4B=0][idx:2B][type:2B][size:2B]
            [cur:4B][min:4B][max:4B][name:N][\\x00]
    """
    cur = bytes.fromhex(param['current_raw'])
    mn = bytes.fromhex(param['min_raw'])
    mx = bytes.fromhex(param['max_raw'])

    # Padding do 4B
    cur = (cur + b'\x00' * 4)[:4]
    mn = (mn + b'\x00' * 4)[:4]
    mx = (mx + b'\x00' * 4)[:4]

    name_bytes = param['name'].encode('ascii') + b'\x00'

    return (struct.pack('<I', 0) +              # status
            struct.pack('<H', param['index']) + # idx
            struct.pack('<H', param['type']) +  # type
            struct.pack('<H', param['size']) +  # size
            cur + mn + mx +
            name_bytes)


def build_param_value_response(param):
    """Zbuduj payload dla CMD_SET_PARAM_VAL response (DH->FC).

    Format: [status:4B=0][idx:2B][value:size_bytes]
    """
    cur = bytes.fromhex(param['current_raw'])
    cur = (cur + b'\x00' * 4)[:param['size']]

    return (struct.pack('<I', 0) +
            struct.pack('<H', param['index']) +
            cur)


# === KLIENT ===

class DumlClient:
    def __init__(self, db_path, audit_path=None, verbose=False):
        self.db = ParamDB(db_path)
        self.verbose = verbose
        self.audit_path = audit_path
        self.audit_lock = threading.Lock()

        self.dev = None
        self.seq = 0xA000
        self.seq_lock = threading.Lock()

        self.running = False
        self.threads = []

        # Queue dla parsowanych ramek z RX
        self.rx_queue = queue.Queue(maxsize=10000)
        self.frame_listeners = []  # callbacks(frame)

        # Statystyki
        self.stats = {
            'tx_count': 0,
            'rx_count': 0,
            'frames_parsed': 0,
            'param_info_responded': 0,
            'param_value_responded': 0,
        }

    # --- AUDIT LOG ---

    def audit(self, event_type, **details):
        """Zapisz event do audit JSONL."""
        if not self.audit_path:
            return
        entry = {
            'ts': datetime.now().isoformat(),
            'event': event_type,
            **details,
        }
        with self.audit_lock:
            with open(self.audit_path, 'a', encoding='utf-8') as fp:
                fp.write(json.dumps(entry, default=str) + '\n')

    # --- USB CONNECT ---

    def connect(self):
        backend = usb.backend.libusb0.get_backend()
        if backend is None:
            raise RuntimeError("Backend libusb0 nieaktywny - sprawdz libusb0.dll")

        # Znajdz MI_04
        for dev in usb.core.find(find_all=True, idVendor=DJI_VID,
                                  idProduct=MINI4_PID, backend=backend):
            try:
                cfg = dev.get_active_configuration()
                for iface in cfg:
                    if iface.bInterfaceNumber == DUML_INTERFACE_NUM:
                        self.dev = dev
                        break
                if self.dev:
                    break
            except Exception:
                pass

        if not self.dev:
            raise RuntimeError("Nie znaleziono Mini 4 Pro MI_04 - czy dron podlaczony?")

        try:
            self.dev.set_configuration(1)
        except usb.core.USBError:
            pass

        usb.util.claim_interface(self.dev, DUML_INTERFACE_NUM)
        self._log(f"[USB] Podlaczono do MI_04 (bus={self.dev.bus} addr={self.dev.address})")
        self.audit('connect', bus=self.dev.bus, addr=self.dev.address)

    def disconnect(self):
        if self.dev:
            try:
                usb.util.release_interface(self.dev, DUML_INTERFACE_NUM)
            except Exception:
                pass
            try:
                usb.util.dispose_resources(self.dev)
            except Exception:
                pass
            self._log("[USB] Rozlaczono")
            self.audit('disconnect')
            self.dev = None

    # --- TX/RX ---

    def _next_seq(self):
        with self.seq_lock:
            s = self.seq
            self.seq = (self.seq + 1) & 0xFFFF
            return s

    def _send_raw(self, frame_bytes):
        try:
            self.dev.write(EP_OUT, frame_bytes, timeout=500)
            self.stats['tx_count'] += 1
            return True
        except usb.core.USBError as e:
            self._log(f"[TX BLAD] {e}")
            return False

    def send(self, dst, flags, cmd_set, cmd_id, payload, seq=None):
        if seq is None:
            seq = self._next_seq()
        frame = build_frame(SRC_DH, dst, seq, flags, cmd_set, cmd_id, payload)
        if self.verbose:
            self._log(f"[TX] dst=0x{dst:02X} seq=0x{seq:04X} flags=0x{flags:02X} "
                      f"cmd=0x{cmd_set:02X}/0x{cmd_id:02X} plen={len(payload)}")
        return seq if self._send_raw(frame) else None

    # --- WATKI ---

    def _heartbeat_loop(self):
        toggle = 0
        while self.running:
            dst = DST_FC if toggle == 0 else DST_FC_BCAST
            self.send(dst, FLAG_BROADCAST, *CMD_SET_OSD, HEARTBEAT_PAYLOAD)
            toggle ^= 1
            time.sleep(0.25)

    def _rx_loop(self):
        buf = bytearray()
        while self.running:
            try:
                data = self.dev.read(EP_IN, 1024, timeout=200)
                if data:
                    buf.extend(data)
                    self.stats['rx_count'] += len(data)

                    while True:
                        pos, length, f = parse_frame(bytes(buf))
                        if f is None:
                            break
                        self.stats['frames_parsed'] += 1
                        try:
                            self.rx_queue.put_nowait(f)
                        except queue.Full:
                            pass
                        del buf[:pos + length]

                    if len(buf) > 8192:
                        del buf[:6000]
            except usb.core.USBError as e:
                if 'timeout' not in str(e).lower():
                    pass

    def _responder_loop(self):
        """Slucha kolejki ramek, odpowiada na pytania FC."""
        while self.running:
            try:
                f = self.rx_queue.get(timeout=0.2)
            except queue.Empty:
                continue

            # Wywolaj listenery (np. dla monitora)
            for cb in self.frame_listeners:
                try:
                    cb(f)
                except Exception as e:
                    self._log(f"[LISTENER ERR] {e}")

            # Odpowiadaj na pytania FC->DH
            if f.src == DST_FC and f.dst == SRC_DH and f.flags == FLAG_REQUEST:
                self._handle_fc_request(f)

    def _handle_fc_request(self, f):
        if (f.cmd_set, f.cmd_id) == CMD_SET_PARAM_INFO:
            # FC pyta o parametr po idx
            if len(f.payload) >= 4:
                idx = struct.unpack('<H', f.payload[2:4])[0]
                param = self.db.get_by_index(idx)
                if param:
                    payload = build_param_info_response(param)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_INFO,
                              payload, seq=f.seq)
                    self.stats['param_info_responded'] += 1
                    if self.verbose:
                        self._log(f"  [INFO] idx={idx} '{param['name']}' -> resp")
                else:
                    # Pusta odpowiedz (jak DH dla nieznanych)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_INFO,
                              struct.pack('<H', idx), seq=f.seq)

        elif (f.cmd_set, f.cmd_id) == CMD_SET_PARAM_VAL:
            # FC pyta o wartosc parametru
            if len(f.payload) >= 6:
                idx = struct.unpack('<H', f.payload[4:6])[0]
                param = self.db.get_by_index(idx)
                if param:
                    payload = build_param_value_response(param)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_VAL,
                              payload, seq=f.seq)
                    self.stats['param_value_responded'] += 1
                else:
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_VAL,
                              struct.pack('<H', idx), seq=f.seq)

    # --- SERWER (full DH-equivalent) ---

    def start_full_session(self):
        """Uruchom heartbeat + RX + responder = jak Drone Hacks."""
        self.running = True

        t_rx = threading.Thread(target=self._rx_loop, daemon=True, name='rx')
        t_rx.start()
        self.threads.append(t_rx)

        t_resp = threading.Thread(target=self._responder_loop, daemon=True, name='responder')
        t_resp.start()
        self.threads.append(t_resp)

        t_hb = threading.Thread(target=self._heartbeat_loop, daemon=True, name='heartbeat')
        t_hb.start()
        self.threads.append(t_hb)

        self._log(f"[CLIENT] Sesja uruchomiona (heartbeat + RX + responder)")
        self._log(f"[CLIENT] Baza: {len(self.db)} parametrow (FW {self.db.firmware})")

    def stop(self):
        self.running = False
        for t in self.threads:
            t.join(timeout=1)
        self.threads = []

    def send_init_sequence(self):
        """Wyslij init: 0xDF + 0xE0 + 0xE0 (jak DH na starcie)."""
        self._log("[INIT] Wysylam init sequence...")
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PROBE, b'\x00')
        time.sleep(0.05)
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_INIT, INIT_E0_PAYLOAD_1)
        time.sleep(0.05)
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_INIT, INIT_E0_PAYLOAD_2)
        self.audit('init_sent')

    # --- SET PARAMETER ---

    def set_param(self, idx_or_name, value, dry_run=False):
        """Wyslij SET (cmd 0xE3) dla parametru."""
        if isinstance(idx_or_name, int):
            param = self.db.get_by_index(idx_or_name)
        else:
            param = self.db.get_by_name(idx_or_name)

        if not param:
            raise ValueError(f"Parametr nie znaleziony: {idx_or_name}")

        idx = param['index']
        type_ = param['type']
        size = param['size']

        # Sprawdz zakres
        if type_ == 8:
            mn = struct.unpack('<f', bytes.fromhex(param['min_raw']))[0]
            mx = struct.unpack('<f', bytes.fromhex(param['max_raw']))[0]
        else:
            mn = int.from_bytes(bytes.fromhex(param['min_raw'])[:size], 'little')
            mx = int.from_bytes(bytes.fromhex(param['max_raw'])[:size], 'little')

        if value < mn or value > mx:
            raise ValueError(f"Wartosc {value} poza zakresem [{mn}, {mx}] "
                             f"dla {param['name']}")

        # Buduj payload SET (z params_v148_set.py):
        # [status:4B=0][idx:2B][value:size]
        value_bytes = encode_value(value, type_, size)
        payload = struct.pack('<I', 0) + struct.pack('<H', idx) + value_bytes

        self._log(f"[SET] {param['name']} (idx={idx}) <- {value}")
        self._log(f"      type={type_} size={size} payload={payload.hex()}")

        if dry_run:
            self._log("      [DRY RUN - bez wyslania]")
            return

        # Najpierw 0xDF probe
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PROBE, b'\x00')
        time.sleep(0.05)

        # Wlasciwy SET 0xE3
        seq = self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_SET, payload)

        self.audit('set_param', idx=idx, name=param['name'],
                   value=value, payload=payload.hex(), seq=seq)

    # --- HELPERS ---

    def _log(self, msg):
        if self.verbose or 'BLAD' in msg or 'ERR' in msg:
            print(msg)


# === CLI ===

def cmd_list(args, db):
    """Wypisz wszystkie parametry."""
    params = sorted(db.params_by_idx.values(), key=lambda p: p['index'])
    print(f"# Baza: {db.firmware}, parametrow: {len(params)}\n")
    print(f"{'idx':>5}  {'type':>4}  {'size':>4}  {'name':<40}  current")
    print("-" * 80)
    for p in params:
        cur = decode_value(bytes.fromhex(p['current_raw']), p['type'], p['size'])
        print(f"{p['index']:>5}  {p['type']:>4}  {p['size']:>4}  "
              f"{p['name']:<40}  {cur}")


def cmd_search(args, db):
    """Szukaj parametrow po fragmencie nazwy."""
    matches = db.search(args.pattern)
    print(f"# Znaleziono {len(matches)} parametrow dla '{args.pattern}'\n")
    print(f"{'idx':>5}  {'type':>4}  {'size':>4}  {'name':<40}  current")
    print("-" * 80)
    for p in sorted(matches, key=lambda p: p['index']):
        cur = decode_value(bytes.fromhex(p['current_raw']), p['type'], p['size'])
        print(f"{p['index']:>5}  {p['type']:>4}  {p['size']:>4}  "
              f"{p['name']:<40}  {cur}")


def cmd_monitor(args, db):
    """Pasywny monitor - tylko sluchaj."""
    client = DumlClient(args.db, audit_path=args.audit, verbose=True)
    client.connect()
    try:
        # Tylko RX
        client.running = True
        rx_thread = threading.Thread(target=client._rx_loop, daemon=True)
        rx_thread.start()

        print(f"[MONITOR] Slucham {args.duration}s...")
        from collections import Counter
        cnt = Counter()
        end = time.time() + args.duration
        while time.time() < end:
            try:
                f = client.rx_queue.get(timeout=0.5)
                cnt[(f.src, f.dst, f.cmd_set, f.cmd_id)] += 1
            except queue.Empty:
                pass

        print(f"\n[MONITOR] Statystyki:")
        for (src, dst, cs, ci), c in cnt.most_common(20):
            print(f"  0x{src:02X}->0x{dst:02X}  cmd 0x{cs:02X}/0x{ci:02X}  count={c}")
    finally:
        client.running = False
        client.disconnect()


def cmd_serve(args, db):
    """Pelna sesja DH-equivalent."""
    client = DumlClient(args.db, audit_path=args.audit, verbose=True)
    client.connect()
    try:
        client.start_full_session()
        time.sleep(2)  # warm-up
        client.send_init_sequence()

        print(f"\n[SERVE] Sesja aktywna. Ctrl+C zeby zakonczyc.")
        print(f"        (heartbeat + odpowiedzi na pytania FC)")
        try:
            while True:
                time.sleep(2)
                s = client.stats
                print(f"  TX={s['tx_count']:>5}  RX={s['rx_count']:>7}B  "
                      f"frames={s['frames_parsed']:>5}  "
                      f"info_resp={s['param_info_responded']}  "
                      f"value_resp={s['param_value_responded']}")
        except KeyboardInterrupt:
            print("\n[SERVE] Konczenie...")
    finally:
        client.stop()
        client.disconnect()


def cmd_set(args, db):
    """SET pojedynczego parametru."""
    client = DumlClient(args.db, audit_path=args.audit, verbose=True)
    client.connect()
    try:
        # Krotka sesja: heartbeat 2s + init + SET
        client.start_full_session()
        time.sleep(2)
        client.send_init_sequence()
        time.sleep(0.5)

        # Konwersja value
        try:
            value = float(args.value)
            if value == int(value):
                value = int(value)
        except ValueError:
            value = args.value

        client.set_param(args.param, value, dry_run=args.dry_run)
        time.sleep(1.0)  # poczekaj na ACK
    finally:
        client.stop()
        client.disconnect()


def main():
    parser = argparse.ArgumentParser(
        description='DUML Client dla DJI Mini 4 Pro FW 01.00.1100',
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--db', default=r'C:\Users\klaud\OneDrive\Pulpit\dji_duml_research\param_db_v1100.json',
                        help='Sciezka do bazy parametrow JSON')
    parser.add_argument('--audit', default='audit.jsonl',
                        help='Sciezka do audit log JSONL')

    sub = parser.add_subparsers(dest='cmd', required=True)

    sp_list = sub.add_parser('list', help='Wypisz wszystkie parametry')
    sp_search = sub.add_parser('search', help='Szukaj parametrow po fragmencie nazwy')
    sp_search.add_argument('pattern')

    sp_mon = sub.add_parser('monitor', help='Pasywny monitor ramek')
    sp_mon.add_argument('--duration', type=float, default=10.0)

    sp_serve = sub.add_parser('serve', help='Pelna sesja DH-equivalent')

    sp_set = sub.add_parser('set', help='Ustaw wartosc parametru')
    sp_set.add_argument('param', help='Indeks lub nazwa parametru')
    sp_set.add_argument('value', help='Nowa wartosc')
    sp_set.add_argument('--dry-run', action='store_true')

    args = parser.parse_args()

    # Wczytaj baze (potrzebna dla list/search bez polaczenia)
    db = ParamDB(args.db)

    if args.cmd == 'list':
        cmd_list(args, db)
    elif args.cmd == 'search':
        cmd_search(args, db)
    elif args.cmd == 'monitor':
        cmd_monitor(args, db)
    elif args.cmd == 'serve':
        cmd_serve(args, db)
    elif args.cmd == 'set':
        try:
            args.param = int(args.param)
        except ValueError:
            pass  # zostaw jako string (nazwa)
        cmd_set(args, db)


if __name__ == '__main__':
    main()

```

### duml_client.py

```
#!/usr/bin/env python3
"""
duml_client.py - kompletny klient DUML dla DJI Mini 4 Pro FW 01.00.1100.

Funkcjonalnie rownowazny Drone Hacks v1.48:
  - Heartbeat 250ms
  - Init sequence (0xDF + 0xE0 x2)
  - Odpowiada na pytania FC z lokalnej bazy 804 parametrow
  - SET parametrow przez 0xE3
  - Audit log JSONL kazdej operacji

Architektura:
  Watek MAIN          - CLI / orkiestracja
  Watek HEARTBEAT     - co 250ms wysyla 0x43 OSD do dst=0x0A i 0xAA
  Watek RX            - czyta EP_IN 0x85, parsuje ramki, wkalda na queue
  Watek RESPONDER     - bierze ramki FC->DH (cmd 0xE1/0xE2 flags=0x40)
                        i odpowiada z bazy
  Audit log           - JSONL z timestampem, src, dst, cmd, payload, decoded

Uzycie:
  python duml_client.py monitor          # tylko podsluchuj
  python duml_client.py serve            # pelny serwer (heartbeat + responses)
  python duml_client.py set <idx> <val>  # pojedynczy SET
  python duml_client.py list             # lista parametrow
  python duml_client.py search <pattern> # szukaj po nazwie
"""
from __future__ import annotations

import argparse
import json
import os
import queue
import struct
import sys
import threading
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Optional

import ctypes.util
import ctypes
_OUR_DLL = r'C:\Python313-64\libusb0.dll'
if os.path.isfile(_OUR_DLL):
    _orig_find = ctypes.util.find_library
    ctypes.util.find_library = (lambda n:
        _OUR_DLL if (n and ('libusb0' in n.lower() or n.lower() in ('usb-0.1.dll', 'usb.dll')))
        else _orig_find(n))

import usb.core
import usb.util
import usb.backend.libusb0


# === KONSTANTY DUML ===

DJI_VID = 0x2CA3
MINI4_PID = 0x0020
DUML_INTERFACE_NUM = 4
EP_OUT = 0x04
EP_IN = 0x85

SRC_DH = 0x03         # nasza tozsamosc (DroneHacks-like)
DST_FC = 0x0A         # Flight Controller
DST_FC_BCAST = 0xAA   # broadcast do FC

CMD_SET_OSD = (0x03, 0x43)        # heartbeat OSD General Data
CMD_SET_PROBE = (0x03, 0xDF)      # pre-init probe
CMD_SET_INIT = (0x03, 0xE0)       # init sequence
CMD_SET_PARAM_INFO = (0x03, 0xE1) # parameter info request/response
CMD_SET_PARAM_VAL = (0x03, 0xE2)  # parameter value request/response
CMD_SET_PARAM_SET = (0x03, 0xE3)  # SET parameter

FLAG_REQUEST = 0x40       # FC -> DH (FC zadaje pytanie)
FLAG_RESPONSE = 0x80      # DH -> FC (DH odpowiada lub inicjuje)
FLAG_BROADCAST = 0x00     # heartbeat


# Statyczny payload heartbeat (z capture #69)
HEARTBEAT_PAYLOAD = bytes.fromhex(
    "000000000000000000000000000000000000000000000000"
    "ffff00009a058600007080000000000031010000000f8a20"
    "5d00008900000000000000000000000000180000000700"
)
INIT_E0_PAYLOAD_1 = bytes.fromhex("000000006eb1b2b999050000")
INIT_E0_PAYLOAD_2 = bytes.fromhex("0900")


# === CRC ===

def crc8_dji(data, init=0x77):
    crc = init
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8C if crc & 1 else crc >> 1
    return crc & 0xFF


def crc16_kermit(data, init=0x3692):
    crc = init
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = (crc >> 1) ^ 0x8408 if crc & 1 else crc >> 1
    return crc & 0xFFFF


# === FRAME ===

@dataclass
class DumlFrame:
    src: int
    dst: int
    seq: int
    flags: int
    cmd_set: int
    cmd_id: int
    payload: bytes
    raw: bytes = field(default=b'')

    def __repr__(self):
        return (f"<Frame {self.src:02X}->{self.dst:02X} "
                f"seq={self.seq:04X} flags={self.flags:02X} "
                f"cs={self.cmd_set:02X} ci={self.cmd_id:02X} "
                f"plen={len(self.payload)}>")


def build_frame(src, dst, seq, flags, cmd_set, cmd_id, payload):
    total_len = 13 + len(payload)
    version = 1
    len_lo = total_len & 0xFF
    len_hi_ver = ((total_len >> 8) & 0x03) | (version << 2)
    header3 = bytes([0x55, len_lo, len_hi_ver])
    crc8 = crc8_dji(header3)
    body = (header3 + bytes([crc8]) + bytes([src, dst]) +
            struct.pack('<H', seq) +
            bytes([flags, cmd_set, cmd_id]) + payload)
    crc16 = crc16_kermit(body)
    return body + struct.pack('<H', crc16)


def parse_frame(buf, start=0):
    """Zwraca (offset, length, frame) lub (None, 0, None)."""
    i = start
    while i < len(buf) - 12:
        if buf[i] == 0x55:
            length = buf[i+1] | ((buf[i+2] & 0x03) << 8)
            if 13 <= length <= 1023 and i + length <= len(buf):
                if crc8_dji(buf[i:i+3]) == buf[i+3]:
                    crc16_actual = crc16_kermit(buf[i:i+length-2])
                    crc16_expected = struct.unpack('<H', buf[i+length-2:i+length])[0]
                    if crc16_actual == crc16_expected:
                        return i, length, DumlFrame(
                            src=buf[i+4], dst=buf[i+5],
                            seq=struct.unpack('<H', buf[i+6:i+8])[0],
                            flags=buf[i+8],
                            cmd_set=buf[i+9], cmd_id=buf[i+10],
                            payload=bytes(buf[i+11:i+length-2]),
                            raw=bytes(buf[i:i+length]),
                        )
        i += 1
    return None, 0, None


# === BAZA PARAMETROW ===

class ParamDB:
    """Lokalna baza 800+ parametrow z capture #69."""

    def __init__(self, db_path):
        with open(db_path, 'r', encoding='utf-8') as fp:
            data = json.load(fp)
        self.firmware = data['firmware']
        self.params_by_idx = {p['index']: p for p in data['parameters']}
        self.params_by_name = {p['name']: p for p in data['parameters']}

    def __len__(self):
        return len(self.params_by_idx)

    def get_by_index(self, idx):
        return self.params_by_idx.get(idx)

    def get_by_name(self, name):
        return self.params_by_name.get(name)

    def search(self, pattern):
        pattern = pattern.lower()
        return [p for p in self.params_by_idx.values()
                if pattern in p['name'].lower()]


def encode_value(value, type_, size):
    """Zakoduj wartosc do bytes wedlug type/size."""
    if type_ == 8 and size == 4:
        return struct.pack('<f', float(value))
    if type_ == 9 and size == 8:
        return struct.pack('<d', float(value))
    # int* (type 0/1/2/4/5)
    return int(value).to_bytes(size, 'little', signed=False)


def decode_value(raw_bytes, type_, size):
    """Zdekoduj raw bytes do wartosci wedlug type/size."""
    if type_ == 8 and size == 4:
        return struct.unpack('<f', raw_bytes[:4])[0]
    if type_ == 9 and size == 8:
        return struct.unpack('<d', raw_bytes[:8])[0]
    return int.from_bytes(raw_bytes[:size], 'little', signed=False)


def build_param_info_response(param):
    """Zbuduj payload dla CMD_SET_PARAM_INFO response (DH->FC, flags=0x80).

    Format: [status:4B=0][idx:2B][type:2B][size:2B]
            [cur:4B][min:4B][max:4B][name:N][\\x00]
    """
    cur = bytes.fromhex(param['current_raw'])
    mn = bytes.fromhex(param['min_raw'])
    mx = bytes.fromhex(param['max_raw'])

    # Padding do 4B
    cur = (cur + b'\x00' * 4)[:4]
    mn = (mn + b'\x00' * 4)[:4]
    mx = (mx + b'\x00' * 4)[:4]

    name_bytes = param['name'].encode('ascii') + b'\x00'

    return (struct.pack('<I', 0) +              # status
            struct.pack('<H', param['index']) + # idx
            struct.pack('<H', param['type']) +  # type
            struct.pack('<H', param['size']) +  # size
            cur + mn + mx +
            name_bytes)


def build_param_value_response(param):
    """Zbuduj payload dla CMD_SET_PARAM_VAL response (DH->FC).

    Format: [status:4B=0][idx:2B][value:size_bytes]
    """
    cur = bytes.fromhex(param['current_raw'])
    cur = (cur + b'\x00' * 4)[:param['size']]

    return (struct.pack('<I', 0) +
            struct.pack('<H', param['index']) +
            cur)


# === KLIENT ===

class DumlClient:
    def __init__(self, db_path, audit_path=None, verbose=False):
        self.db = ParamDB(db_path)
        self.verbose = verbose
        self.audit_path = audit_path
        self.audit_lock = threading.Lock()

        self.dev = None
        self.seq = 0xA000
        self.seq_lock = threading.Lock()

        self.running = False
        self.threads = []

        # Queue dla parsowanych ramek z RX
        self.rx_queue = queue.Queue(maxsize=10000)
        self.frame_listeners = []  # callbacks(frame)

        # Statystyki
        self.stats = {
            'tx_count': 0,
            'rx_count': 0,
            'frames_parsed': 0,
            'param_info_responded': 0,
            'param_value_responded': 0,
        }

    # --- AUDIT LOG ---

    def audit(self, event_type, **details):
        """Zapisz event do audit JSONL."""
        if not self.audit_path:
            return
        entry = {
            'ts': datetime.now().isoformat(),
            'event': event_type,
            **details,
        }
        with self.audit_lock:
            with open(self.audit_path, 'a', encoding='utf-8') as fp:
                fp.write(json.dumps(entry, default=str) + '\n')

    # --- USB CONNECT ---

    def connect(self):
        backend = usb.backend.libusb0.get_backend()
        if backend is None:
            raise RuntimeError("Backend libusb0 nieaktywny - sprawdz libusb0.dll")

        # Znajdz MI_04
        for dev in usb.core.find(find_all=True, idVendor=DJI_VID,
                                  idProduct=MINI4_PID, backend=backend):
            try:
                cfg = dev.get_active_configuration()
                for iface in cfg:
                    if iface.bInterfaceNumber == DUML_INTERFACE_NUM:
                        self.dev = dev
                        break
                if self.dev:
                    break
            except Exception:
                pass

        if not self.dev:
            raise RuntimeError("Nie znaleziono Mini 4 Pro MI_04 - czy dron podlaczony?")

        try:
            self.dev.set_configuration(1)
        except usb.core.USBError:
            pass

        usb.util.claim_interface(self.dev, DUML_INTERFACE_NUM)
        self._log(f"[USB] Podlaczono do MI_04 (bus={self.dev.bus} addr={self.dev.address})")
        self.audit('connect', bus=self.dev.bus, addr=self.dev.address)

    def disconnect(self):
        if self.dev:
            try:
                usb.util.release_interface(self.dev, DUML_INTERFACE_NUM)
            except Exception:
                pass
            try:
                usb.util.dispose_resources(self.dev)
            except Exception:
                pass
            self._log("[USB] Rozlaczono")
            self.audit('disconnect')
            self.dev = None

    # --- TX/RX ---

    def _next_seq(self):
        with self.seq_lock:
            s = self.seq
            self.seq = (self.seq + 1) & 0xFFFF
            return s

    def _send_raw(self, frame_bytes):
        try:
            self.dev.write(EP_OUT, frame_bytes, timeout=500)
            self.stats['tx_count'] += 1
            return True
        except usb.core.USBError as e:
            self._log(f"[TX BLAD] {e}")
            return False

    def send(self, dst, flags, cmd_set, cmd_id, payload, seq=None):
        if seq is None:
            seq = self._next_seq()
        frame = build_frame(SRC_DH, dst, seq, flags, cmd_set, cmd_id, payload)
        if self.verbose:
            self._log(f"[TX] dst=0x{dst:02X} seq=0x{seq:04X} flags=0x{flags:02X} "
                      f"cmd=0x{cmd_set:02X}/0x{cmd_id:02X} plen={len(payload)}")
        return seq if self._send_raw(frame) else None

    # --- WATKI ---

    def _heartbeat_loop(self):
        toggle = 0
        while self.running:
            dst = DST_FC if toggle == 0 else DST_FC_BCAST
            self.send(dst, FLAG_BROADCAST, *CMD_SET_OSD, HEARTBEAT_PAYLOAD)
            toggle ^= 1
            time.sleep(0.25)

    def _rx_loop(self):
        buf = bytearray()
        while self.running:
            try:
                data = self.dev.read(EP_IN, 1024, timeout=200)
                if data:
                    buf.extend(data)
                    self.stats['rx_count'] += len(data)

                    while True:
                        pos, length, f = parse_frame(bytes(buf))
                        if f is None:
                            break
                        self.stats['frames_parsed'] += 1
                        try:
                            self.rx_queue.put_nowait(f)
                        except queue.Full:
                            pass
                        del buf[:pos + length]

                    if len(buf) > 8192:
                        del buf[:6000]
            except usb.core.USBError as e:
                if 'timeout' not in str(e).lower():
                    pass

    def _responder_loop(self):
        """Slucha kolejki ramek, odpowiada na pytania FC."""
        while self.running:
            try:
                f = self.rx_queue.get(timeout=0.2)
            except queue.Empty:
                continue

            # Wywolaj listenery (np. dla monitora)
            for cb in self.frame_listeners:
                try:
                    cb(f)
                except Exception as e:
                    self._log(f"[LISTENER ERR] {e}")

            # Odpowiadaj na pytania FC->DH
            if f.src == DST_FC and f.dst == SRC_DH and f.flags == FLAG_REQUEST:
                self._handle_fc_request(f)

    def _handle_fc_request(self, f):
        if (f.cmd_set, f.cmd_id) == CMD_SET_PARAM_INFO:
            # FC pyta o parametr po idx
            if len(f.payload) >= 4:
                idx = struct.unpack('<H', f.payload[2:4])[0]
                param = self.db.get_by_index(idx)
                if param:
                    payload = build_param_info_response(param)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_INFO,
                              payload, seq=f.seq)
                    self.stats['param_info_responded'] += 1
                    if self.verbose:
                        self._log(f"  [INFO] idx={idx} '{param['name']}' -> resp")
                else:
                    # Pusta odpowiedz (jak DH dla nieznanych)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_INFO,
                              struct.pack('<H', idx), seq=f.seq)

        elif (f.cmd_set, f.cmd_id) == CMD_SET_PARAM_VAL:
            # FC pyta o wartosc parametru
            if len(f.payload) >= 6:
                idx = struct.unpack('<H', f.payload[4:6])[0]
                param = self.db.get_by_index(idx)
                if param:
                    payload = build_param_value_response(param)
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_VAL,
                              payload, seq=f.seq)
                    self.stats['param_value_responded'] += 1
                else:
                    self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_VAL,
                              struct.pack('<H', idx), seq=f.seq)

    # --- SERWER (full DH-equivalent) ---

    def start_full_session(self):
        """Uruchom heartbeat + RX + responder = jak Drone Hacks."""
        self.running = True

        t_rx = threading.Thread(target=self._rx_loop, daemon=True, name='rx')
        t_rx.start()
        self.threads.append(t_rx)

        t_resp = threading.Thread(target=self._responder_loop, daemon=True, name='responder')
        t_resp.start()
        self.threads.append(t_resp)

        t_hb = threading.Thread(target=self._heartbeat_loop, daemon=True, name='heartbeat')
        t_hb.start()
        self.threads.append(t_hb)

        self._log(f"[CLIENT] Sesja uruchomiona (heartbeat + RX + responder)")
        self._log(f"[CLIENT] Baza: {len(self.db)} parametrow (FW {self.db.firmware})")

    def stop(self):
        self.running = False
        for t in self.threads:
            t.join(timeout=1)
        self.threads = []

    def send_init_sequence(self):
        """Wyslij init: 0xDF + 0xE0 + 0xE0 (jak DH na starcie)."""
        self._log("[INIT] Wysylam init sequence...")
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PROBE, b'\x00')
        time.sleep(0.05)
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_INIT, INIT_E0_PAYLOAD_1)
        time.sleep(0.05)
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_INIT, INIT_E0_PAYLOAD_2)
        self.audit('init_sent')

    # --- SET PARAMETER ---

    def set_param(self, idx_or_name, value, dry_run=False):
        """Wyslij SET (cmd 0xE3) dla parametru."""
        if isinstance(idx_or_name, int):
            param = self.db.get_by_index(idx_or_name)
        else:
            param = self.db.get_by_name(idx_or_name)

        if not param:
            raise ValueError(f"Parametr nie znaleziony: {idx_or_name}")

        idx = param['index']
        type_ = param['type']
        size = param['size']

        # Sprawdz zakres
        if type_ == 8:
            mn = struct.unpack('<f', bytes.fromhex(param['min_raw']))[0]
            mx = struct.unpack('<f', bytes.fromhex(param['max_raw']))[0]
        else:
            mn = int.from_bytes(bytes.fromhex(param['min_raw'])[:size], 'little')
            mx = int.from_bytes(bytes.fromhex(param['max_raw'])[:size], 'little')

        if value < mn or value > mx:
            raise ValueError(f"Wartosc {value} poza zakresem [{mn}, {mx}] "
                             f"dla {param['name']}")

        # Buduj payload SET (z params_v148_set.py):
        # [status:4B=0][idx:2B][value:size]
        value_bytes = encode_value(value, type_, size)
        payload = struct.pack('<I', 0) + struct.pack('<H', idx) + value_bytes

        self._log(f"[SET] {param['name']} (idx={idx}) <- {value}")
        self._log(f"      type={type_} size={size} payload={payload.hex()}")

        if dry_run:
            self._log("      [DRY RUN - bez wyslania]")
            return

        # Najpierw 0xDF probe
        self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PROBE, b'\x00')
        time.sleep(0.05)

        # Wlasciwy SET 0xE3
        seq = self.send(DST_FC, FLAG_RESPONSE, *CMD_SET_PARAM_SET, payload)

        self.audit('set_param', idx=idx, name=param['name'],
                   value=value, payload=payload.hex(), seq=seq)

    # --- HELPERS ---

    def _log(self, msg):
        if self.verbose or 'BLAD' in msg or 'ERR' in msg:
            print(msg)


# === CLI ===

def cmd_list(args, db):
    """Wypisz wszystkie parametry."""
    params = sorted(db.params_by_idx.values(), key=lambda p: p['index'])
    print(f"# Baza: {db.firmware}, parametrow: {len(params)}\n")
    print(f"{'idx':>5}  {'type':>4}  {'size':>4}  {'name':<40}  current")
    print("-" * 80)
    for p in params:
        cur = decode_value(bytes.fromhex(p['current_raw']), p['type'], p['size'])
        print(f"{p['index']:>5}  {p['type']:>4}  {p['size']:>4}  "
              f"{p['name']:<40}  {cur}")


def cmd_search(args, db):
    """Szukaj parametrow po fragmencie nazwy."""
    matches = db.search(args.pattern)
    print(f"# Znaleziono {len(matches)} parametrow dla '{args.pattern}'\n")
    print(f"{'idx':>5}  {'type':>4}  {'size':>4}  {'name':<40}  current")
    print("-" * 80)
    for p in sorted(matches, key=lambda p: p['index']):
        cur = decode_value(bytes.fromhex(p['current_raw']), p['type'], p['size'])
        print(f"{p['index']:>5}  {p['type']:>4}  {p['size']:>4}  "
              f"{p['name']:<40}  {cur}")


def cmd_monitor(args, db):
    """Pasywny monitor - tylko sluchaj."""
    client = DumlClient(args.db, audit_path=args.audit, verbose=True)
    client.connect()
    try:
        # Tylko RX
        client.running = True
        rx_thread = threading.Thread(target=client._rx_loop, daemon=True)
        rx_thread.start()

        print(f"[MONITOR] Slucham {args.duration}s...")
        from collections import Counter
        cnt = Counter()
        end = time.time() + args.duration
        while time.time() < end:
            try:
                f = client.rx_queue.get(timeout=0.5)
                cnt[(f.src, f.dst, f.cmd_set, f.cmd_id)] += 1
            except queue.Empty:
                pass

        print(f"\n[MONITOR] Statystyki:")
        for (src, dst, cs, ci), c in cnt.most_common(20):
            print(f"  0x{src:02X}->0x{dst:02X}  cmd 0x{cs:02X}/0x{ci:02X}  count={c}")
    finally:
        client.running = False
        client.disconnect()


def cmd_serve(args, db):
    """Pelna sesja DH-equivalent z diagnostyka."""
    from collections import Counter
    rx_histogram = Counter()
    rx_lock = threading.Lock()

    def listener(f):
        with rx_lock:
            rx_histogram[(f.src, f.dst, f.cmd_set, f.cmd_id, f.flags)] += 1

    client = DumlClient(args.db, audit_path=args.audit, verbose=False)  # verbose=False
    client.frame_listeners.append(listener)
    client.connect()
    try:
        client.start_full_session()
        print("[CLIENT] Heartbeat 250ms + RX + responder uruchomione")
        time.sleep(2)
        client.send_init_sequence()
        print("[INIT] Wyslano probe + 2x init E0")

        print(f"\n[SERVE] Sesja aktywna. Ctrl+C zeby zakonczyc.")
        try:
            iteration = 0
            while True:
                time.sleep(5)
                iteration += 1
                s = client.stats
                print(f"\n[t={iteration*5:>3}s] TX={s['tx_count']:>4}  "
                      f"RX={s['rx_count']:>6}B  frames={s['frames_parsed']:>5}  "
                      f"info_resp={s['param_info_responded']}  "
                      f"value_resp={s['param_value_responded']}")
                # Top 8 ramek
                with rx_lock:
                    snap = rx_histogram.most_common(8)
                print("  Top RX (src->dst, cs, ci, fl):")
                for (src, dst, cs, ci, fl), cnt in snap:
                    print(f"    0x{src:02X}->0x{dst:02X}  cmd 0x{cs:02X}/0x{ci:02X}  "
                          f"flg=0x{fl:02X}  count={cnt}")
                # Filtruj tylko te skierowane DO NAS
                with rx_lock:
                    to_us = [(k, v) for k, v in rx_histogram.items() if k[1] == 0x03]
                if to_us:
                    print("  RAMKI DO NAS (dst=0x03):")
                    for (src, dst, cs, ci, fl), cnt in to_us:
                        print(f"    0x{src:02X}->0x{dst:02X}  cmd 0x{cs:02X}/0x{ci:02X}  "
                              f"flg=0x{fl:02X}  count={cnt}")
                else:
                    print("  RAMKI DO NAS: brak (FC nas ignoruje)")
        except KeyboardInterrupt:
            print("\n[SERVE] Konczenie...")
            print(f"\n=== FINAL HISTOGRAM ===")
            with rx_lock:
                for (src, dst, cs, ci, fl), cnt in rx_histogram.most_common():
                    print(f"  0x{src:02X}->0x{dst:02X}  cmd 0x{cs:02X}/0x{ci:02X}  "
                          f"flg=0x{fl:02X}  count={cnt}")
    finally:
        client.stop()
        client.disconnect()


def cmd_set(args, db):
    """SET pojedynczego parametru."""
    client = DumlClient(args.db, audit_path=args.audit, verbose=True)
    client.connect()
    try:
        # Krotka sesja: heartbeat 2s + init + SET
        client.start_full_session()
        time.sleep(2)
        client.send_init_sequence()
        time.sleep(0.5)

        # Konwersja value
        try:
            value = float(args.value)
            if value == int(value):
                value = int(value)
        except ValueError:
            value = args.value

        client.set_param(args.param, value, dry_run=args.dry_run)
        time.sleep(1.0)  # poczekaj na ACK
    finally:
        client.stop()
        client.disconnect()



def cmd_capture(args, db):
    """Pasywny capture wszystkich ramek do pliku JSONL.

    Mozemy potem analizowac payloady dekodera telemetrii.
    """
    import threading
    out_path = args.output
    print(f"[CAPTURE] Zapisuje ramki do {out_path}")
    print(f"[CAPTURE] Czas: {args.duration}s")

    fp = open(out_path, 'w', encoding='utf-8')
    write_lock = threading.Lock()
    frame_count = 0

    def listener(f):
        nonlocal frame_count
        entry = {
            'ts': time.time(),
            'src': f.src, 'dst': f.dst,
            'seq': f.seq, 'flags': f.flags,
            'cmd_set': f.cmd_set, 'cmd_id': f.cmd_id,
            'payload_hex': f.payload.hex(),
            'plen': len(f.payload),
        }
        with write_lock:
            fp.write(json.dumps(entry) + '\n')
            frame_count += 1

    client = DumlClient(args.db, audit_path=None, verbose=False)
    client.frame_listeners.append(listener)
    client.connect()
    try:
        # Tylko RX (bez heartbeatu) - czysta obserwacja
        client.running = True
        rx_thread = threading.Thread(target=client._rx_loop, daemon=True)
        rx_thread.start()
        responder_thread = threading.Thread(target=client._responder_loop, daemon=True)
        responder_thread.start()

        end = time.time() + args.duration
        while time.time() < end:
            time.sleep(1.0)
            print(f"  t={time.time() - (end - args.duration):.1f}s  frames={frame_count}")
    finally:
        client.running = False
        time.sleep(0.5)
        client.disconnect()
        with write_lock:
            fp.close()
        print(f"\n[CAPTURE] Zapisano {frame_count} ramek do {out_path}")
        print(f"[CAPTURE] Rozmiar: {os.path.getsize(out_path)} B")



def cmd_probe(args, db):
    """Test rozne komendy DUML i czekaj na odpowiedz."""
    import threading
    from collections import Counter

    rx_lock = threading.Lock()
    received = []  # wszystkie odebrane ramki

    def listener(f):
        with rx_lock:
            received.append(f)

    client = DumlClient(args.db, audit_path=args.audit, verbose=False)
    client.frame_listeners.append(listener)
    client.connect()
    try:
        # Tylko RX (BEZ heartbeatu i bez respondera!)
        # Probujemy "neutralne" komendy ktore nie wymagaja sesji
        client.running = True
        rx_thread = threading.Thread(target=client._rx_loop, daemon=True)
        rx_thread.start()
        time.sleep(0.5)  # kalmuna

        # Lista probek - rozne komendy ktore mogly by dac odpowiedz bez handshaku
        probes = [
            # cmd_set=0x00 (general), cmd_id=0x01 - GET_DEVICE_VERSION
            ("GET_VERSION (00/01)",   0x00, 0x01, b''),
            # 0x00/0x07 - GET_PRODUCT_TYPE
            ("GET_PRODUCT (00/07)",   0x00, 0x07, b''),
            # 0x0E - eye? (collision avoidance?)
            ("EYE_STATUS  (0E/01)",   0x0E, 0x01, b''),
            # 0x0E/0x16 - get visual front
            ("VIS_FRONT   (0E/16)",   0x0E, 0x16, b''),
            # 0x05 (gimbal)
            ("GIM_GETPOSE (05/30)",   0x05, 0x30, b''),
            # 0x03 (FC) - zapytaj o status
            ("FC_GETSTATE (03/02)",   0x03, 0x02, b''),
            # 0x03 (FC) - get product state
            ("FC_GETPSTATE(03/06)",   0x03, 0x06, b''),
            # 0x0D (battery)
            ("BAT_INFO    (0D/02)",   0x0D, 0x02, b''),
            ("BAT_HISTORY (0D/03)",   0x0D, 0x03, b''),
        ]

        results = []
        for label, cs, ci, payload in probes:
            with rx_lock:
                marker = len(received)
            print(f"\n--- {label} ---")
            seq = client.send(DST_FC, FLAG_REQUEST, cs, ci, payload)
            if seq is None:
                print("  TX BLAD")
                continue
            print(f"  TX seq=0x{seq:04X}")
            time.sleep(0.5)
            with rx_lock:
                new = received[marker:]
            # Sprawdz czy ktoras pasuje seq+cs+ci
            matches = [f for f in new if f.seq == seq]
            if matches:
                for m in matches:
                    print(f"  >>> ODP src=0x{m.src:02X} flags=0x{m.flags:02X} "
                          f"cs=0x{m.cmd_set:02X} ci=0x{m.cmd_id:02X} plen={len(m.payload)}")
                    print(f"      payload: {m.payload.hex()}")
                    results.append((label, True, m))
            else:
                # Pokaz inne ramki ktore moga byc reakcja
                relevant = [f for f in new if (f.cmd_set == cs and f.cmd_id == ci)]
                if relevant:
                    print(f"  Brak match seq, ale {len(relevant)} ramek z {hex(cs)}/{hex(ci)}:")
                    for m in relevant[:3]:
                        print(f"    src=0x{m.src:02X} dst=0x{m.dst:02X} seq=0x{m.seq:04X} "
                              f"plen={len(m.payload)}")
                else:
                    print(f"  Brak odpowiedzi (odebrano {len(new)} innych ramek)")
                results.append((label, False, None))

        print(f"\n\n=== PODSUMOWANIE ===")
        for label, ok, _ in results:
            print(f"  {'✓' if ok else '✗'} {label}")

    finally:
        client.running = False
        time.sleep(0.5)
        client.disconnect()


def main():
    parser = argparse.ArgumentParser(
        description='DUML Client dla DJI Mini 4 Pro FW 01.00.1100',
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--db', default=r'C:\Users\klaud\OneDrive\Pulpit\dji_duml_research\param_db_v1100.json',
                        help='Sciezka do bazy parametrow JSON')
    parser.add_argument('--audit', default='audit.jsonl',
                        help='Sciezka do audit log JSONL')

    sub = parser.add_subparsers(dest='cmd', required=True)

    sp_list = sub.add_parser('list', help='Wypisz wszystkie parametry')
    sp_search = sub.add_parser('search', help='Szukaj parametrow po fragmencie nazwy')
    sp_search.add_argument('pattern')

    sp_mon = sub.add_parser('monitor', help='Pasywny monitor ramek')
    sp_mon.add_argument('--duration', type=float, default=10.0)

    sp_serve = sub.add_parser('serve', help='Pelna sesja DH-equivalent')

    sp_set = sub.add_parser('set', help='Ustaw wartosc parametru')
    sp_set.add_argument('param', help='Indeks lub nazwa parametru')
    sp_set.add_argument('value', help='Nowa wartosc')
    sp_set.add_argument('--dry-run', action='store_true')

    sp_cap = sub.add_parser('capture', help='Pasywny capture ramek do JSONL')
    sp_cap.add_argument('--duration', type=float, default=10.0)
    sp_cap.add_argument('--output', default='capture_rx.jsonl')

    sp_probe = sub.add_parser('probe', help='Testuj rozne komendy DUML')

    args = parser.parse_args()

    # Wczytaj baze (potrzebna dla list/search bez polaczenia)
    db = ParamDB(args.db)

    if args.cmd == 'list':
        cmd_list(args, db)
    elif args.cmd == 'search':
        cmd_search(args, db)
    elif args.cmd == 'monitor':
        cmd_monitor(args, db)
    elif args.cmd == 'serve':
        cmd_serve(args, db)
    elif args.cmd == 'set':
        try:
            args.param = int(args.param)
        except ValueError:
            pass  # zostaw jako string (nazwa)
        cmd_set(args, db)
    elif args.cmd == 'capture':
        cmd_capture(args, db)
    elif args.cmd == 'probe':
        cmd_probe(args, db)


if __name__ == '__main__':
    main()

```

### ARCHITECTURE_android.md

```
# ARCHITECTURE — DJI Mini 4 Pro Diagnostic Companion

**Target hardware:** DJI Mini 4 Pro (aircraft FW `01.00.1100`) + DJI RC 2 (`08.01.0100`)
**Scope:** research and diagnostic only. No official SDK assumed.
**Front-ends:** Android (field companion) + Windows 10 (workstation).
**Legal framing:** interoperability reverse engineering under EU Directive 2009/24/EC Art. 6.

---

## 0. Platform reality check (this drives every other decision)

Two hard constraints override the naive "run it on the RC 2" goal and shape the whole design.

**(a) The RC 2 cannot host custom software.** It runs a closed Android 11 fork with no ADB,
no sideloading, no USB host/OTG mode, and a locked SELinux policy. Field evidence confirms it:
the RC 2 USB menu exposes only *charge this device* and *file transfer* — there is no host mode
and no developer options. **Consequence: the companion app runs on a separate Android device
(reference target: Pixel), never on the RC 2.** The RC 2 is treated as an opaque RF black box.

**(b) No SDK means no tap into the live RF link.** Full in-flight flight-controller (FC)
telemetry travels OcuSync → RC 2 → DJI Fly. Without the Mobile SDK or injection into the RC 2,
no ground device can read that stream in real time. This forces a **three-path acquisition model**
rather than a single "connect and read everything live" assumption.

Honest capability matrix — what is actually obtainable and when:

| Data | Path | Live in-flight? |
|---|---|---|
| Position / altitude / velocity / operator location / RID serial | **BLE Remote ID broadcast** (ASTM F3411, service `0xFFFA`) | **Yes** (~1 Hz, regulatory-mandated) |
| Full FC telemetry + 804 parameters + OSD General Data | **USB-C DUML** (phone as USB host, tethered) | No — bench / ground only |
| Everything, workstation-grade | **Windows USB DUML** (existing Tauri / WebUSB tool) | No — bench / ground only |
| Full-fidelity post-flight telemetry | **Flight-log extraction over USB** (`.txt` / `DAT`) | Post-flight |

**Design conclusion:** the phone's *live* dashboard is fed by **BLE Remote ID**. Full-telemetry,
parameter, and log work is **USB-tethered** (phone-as-host or Windows). Any claim that a phone
pulls full live OSD mid-flight without SDK or RC 2 injection is incorrect.

---

## 1. High-level architecture (text diagram)

```
                     ┌─────────────────────────────────────────────┐
                     │            SHARED RUST CORE (duml-core)       │
                     │  frame codec │ CRC8/16 │ param DB │ OSD decode│
                     │  RID decoder │ log parser │ audit engine      │
                     └───────┬───────────────────────────────┬──────┘
                     uniffi  │                        cargo-ndk│
              ┌──────────────┴─────────┐        ┌─────────────┴───────────────┐
              │   WINDOWS (Tauri)      │        │   ANDROID (Kotlin/Compose)   │
              │   workstation, USB     │        │   field companion, Pixel     │
              └──────────┬─────────────┘        └──────┬───────────────┬───────┘
                         │ WinUSB / WebUSB             │ USB host       │ BLE
                         │                             │ (OTG)          │ scanner
                    ┌────┴────┐                   ┌────┴────┐      ┌────┴─────┐
                    │ MI_04   │                   │ MI_04   │      │ RID adv. │
                    │ EP4/85  │                   │ EP4/85  │      │ 0xFFFA   │
                    └────┬────┘                   └────┬────┘      └────┬─────┘
                         │ tethered USB                │ tethered       │ passive RF
                    ┌────┴──────────── AIRCRAFT (FC) ──┴────┐           │
                    │  DUML over USB │ OSD push │ params     │      (broadcast,
                    └────────────────────────────────────────┘       no pairing)

   RC 2  ── OcuSync ── AIRCRAFT   (opaque; not a software target)
```

Internal layering, identical on both front-ends:

```
 ACQUISITION   →  PROCESSING     →  STORAGE         →  UI
 (transport)      (decode/fuse)     (Room/SQLite)      (Compose/Tauri)
 usb-host         frame parser      flights table      dashboard
 ble-scanner      osd decoder       samples table      map overlay
 log-reader       rid decoder       params snapshot    signal panel
                  fusion/smoothing  audit.jsonl        log analyzer
```

The `duml-core` Rust crate is the single source of truth for the wire protocol. Both front-ends
call into it via FFI, so a protocol fix is written and tested once.

---

## 2. Communication channels — feasibility assessment

### 2.1 DJI Mobile SDK V5 — effectively unavailable
MSDK requires DJI's application scaffolding, and RC-based operation needs an environment that
cannot be installed on the RC 2. It is **not** a dependency of this project. Treated as a hard "no".

### 2.2 Reverse-engineered DUML over USB — primary tethered path
Already validated on Windows (WebUSB, interface 4, EP_OUT `0x04` / EP_IN `0x85`, corrected
FC-initiated token handshake). On Android, the phone acts as **USB host** to the aircraft's USB-C
port through `android.hardware.usb` (`UsbManager` → `UsbDeviceConnection` → `bulkTransfer`).
Same wire protocol, same Rust codec, no RC 2 in the path.

### 2.3 BLE Remote ID — primary live path
OpenDroneID / ASTM F3411 broadcast, service UUID `0xFFFA`, no pairing, fully passive. Works on any
Android BLE scanner. This is the only legitimate airborne live feed available without an SDK, and
it is continuous because the ~900 g MTOM Mini 4 Pro is subject to EU Direct Remote ID.

### 2.4 RC 2 internal interfaces — dead end
Charge-only USB, no host mode, no ADB, locked SELinux. Documented and closed as a channel.

---

## 3. Reverse-engineering strategy

### 3.1 Safe traffic capture
- **Windows USB (established):** Wireshark + USBPcap on MI_04, validated against the golden
  reference capture `#69` (`capture_v148_full69.pcap`). The `dji-dumlv1-flyc.lua` dissector plus the
  192-entry `cmd_descriptions.json` decode command IDs. `#69` remains the regression baseline.
- **Android USB:** log raw `bulkTransfer` buffers to JSONL *before* decode (the `cmd_capture`
  pattern), then diff against the Windows captures to prove identical framing across host stacks.
- **BLE:** nRF Connect / Wireshark + `btsnoop_hci.log` to reverse the RID advertising layout;
  cross-check against OpenDroneID message types 0–5.
- **Flight logs:** pull aircraft `.txt` / `DAT` logs over USB (mass storage or DUML file transfer)
  and decode post-flight.

Never transmit on the RF link to "probe" — all capture is passive or bench-tethered.

### 3.2 Likely data formats
- **DUML binary frames** —
  `0x55 | len | ver | crc8 | src | dst | seq | flags | cmd_set | cmd_id | payload | crc16`.
  CRC8 poly `0x8C` (init `0x77`), CRC16 kermit poly `0x8408` (init `0x3692`). Not protobuf.
- **OSD General Data `0x43`** — fixed-layout binary **push stream**, not request/response.
  Confirmed: a `send_and_wait` model times out; it must be consumed passively off EP_IN.
- **Remote ID** — packed C structs per ASTM F3411, little-endian, scaled integers.
- **DAT flight logs** — versioned binary records; some fields are obfuscated or encrypted
  depending on firmware version.

---

## 4. Modules

### Acquisition
- `UsbHostTransport` (Android) / `WinUsbTransport` (Windows): enumerate MI_04, claim interface 4,
  bulk read/write. Enforce the mandatory init order (Connect → Initialize in rapid succession) and
  hard-reset recovery on timeout (USB unplug → aircraft power cycle → RC 2 live view → replug).
- `BleRidScanner`: filtered scan on `0xFFFA`, one callback per advertisement.
- `LogReader`: pull and stream-decode flight logs.

### Processing
- `FrameCodec` (Rust): build/parse frames, verify CRC8 + CRC16.
- `OsdDecoder`: consume the `0x43` push stream → structured `TelemetrySample`.
- `RidDecoder`: ASTM messages → position/vector sample.
- `TelemetryFusion`: merge USB-OSD (bench) or RID (live) into one timeline; light smoothing for map.

### Storage
- Room / SQLite tables: `flights`, `samples`, `param_snapshots`, `signal_events`.
- `audit.jsonl`: append-only; every SET carries a task-ID; the regulatory family adds a formal
  tasking gate on top.
- Export: CSV and JSON.

### UI
See §6.

---

## 5. Features (with implementation approach)

### 5.1 Live telemetry dashboard
Source = BLE Remote ID when airborne, or USB-OSD when tethered on the bench. The decoder emits an
immutable `TelemetryState` per tick over a Kotlin `StateFlow`; Compose diffs it into
recomposition-cheap cards (altitude, groundspeed, satellites, distance-from-operator, RID serial,
RSSI, battery). A ring buffer of the last *N* samples feeds sparklines. Target: under 100 ms from
decode to render; decode runs off the UI thread.

### 5.2 Flight-log parser
Post-flight, open a `DAT` / `.txt` log, stream records into normalized `samples`, and reconstruct
the 3D track, battery curve, control inputs (where present), and event markers (RTH, signal loss).
Export the reconstructed timeline as CSV/JSON. This path recovers the full fidelity that the live
BLE path cannot provide.

### 5.3 Signal-analysis module
Inputs: RID RSSI plus, when tethered, OSD link fields. Compute a rolling SNR estimate, detect
dropouts, and build a per-position RSSI heatmap on the map. "Interference estimate" is the variance
and gradient of RSSI versus distance measured against a free-space reference curve — anomalous
attenuation is flagged. No transmission; purely passive inference.

### 5.4 Map integration (offline + online)
MapLibre GL Native. Offline uses pre-cached MBTiles for the area of operations; online uses raster
or vector tiles when connectivity is available. Layers: live/track polyline, operator marker,
RID-derived aircraft marker, RSSI heatmap, and a **read-only** NFZ / geo overlay (informational,
sourced from compliance data such as DroneTower / PansaUTM) — never a control surface.

### 5.5 Location-dependent parameters (GATED — no bypass path)
Exposed **read-only by default.** Any SET on the regulatory family (`ce_*`, country/region,
geo-enforcement) is hard-gated:

1. requires an open task-ID before execution;
2. writes an `audit.jsonl` entry both pre- and post-operation;
3. defaults to dry-run;
4. the actual enum values are sourced from the vetted `param_db` under authorization — **not
   hardcoded in the application**, and the app ships **no** CE→FCC region-switch recipe.

This mirrors the project's existing governance exactly and keeps the tool defensible under the
Art. 6 framing. Rationale, stated plainly: switching a unit physically operating in PL/EU into a
US/FCC regime is not a cosmetic "country code" change — it raises transmit EIRP above the permitted
ceiling (100 mW @ 2.4 GHz, 25 mW @ 5.8 GHz), invalidates the unit's CE conformity, and alters or
disables EU geo-enforcement. That is an *operating* act, not an *interoperability* act, so it stays
behind formal tasking and outside the shipped feature set. Non-regulatory location parameters
(home-point behavior, warning distances, etc.) use the normal task-ID audit path without the extra
regulatory gate.

---

## 6. UI/UX

Pilot-first, glanceable, low-latency.

- **Primary row (always visible):** altitude, distance, groundspeed, satellites, link RSSI,
  battery. Large numerals, high contrast, sunlight-readable dark theme.
- **Secondary:** map with track and operator marker.
- **On-demand:** log analyzer, signal panel, parameter browser (read-only unless a task is open).

**Latency discipline:** decode in Rust off the UI thread → a single immutable `TelemetryState`
per tick → Compose diff. No per-field allocations in the hot loop. Coalesce BLE callbacks to
≤ 10 Hz for UI updates so scan bursts never stall recomposition.

---

## 7. Suggested technology stack

- **Shared core:** Rust crate `duml-core` — codec, decoders, audit engine. `uniffi` for bindings,
  `cargo-ndk` to build the Android `.so`.
- **Android:** Kotlin + Jetpack Compose, Hilt (DI), Room (persistence), Kotlin Flows,
  `android.hardware.usb` (host), the platform BLE scanner API, MapLibre GL Native, Vico (charts).
- **Windows:** Rust + Tauri (already working), WinUSB / WebUSB transport.
- **Tooling:** Wireshark + USBPcap, `dji-dumlv1-flyc.lua`, `pnputil`, nRF Connect.

**Sideloading constraints (Android):** distribute as a self-signed APK; the target device must have
"install unknown apps" enabled for the chosen source — there is no Play Store path for a tool of
this class. USB-host access additionally requires the `USB_DEVICE_ATTACHED` intent-filter plus a
runtime permission grant per device. The RC 2 remains ineligible to run the APK at all (see §0).

---

## 8. Risks and limitations

- **Firmware encryption / obfuscation** — some DAT log fields and future FW payloads may be
  encrypted; decode coverage is best-effort and pinned to a firmware version.
- **No live full telemetry without SDK / RC 2** — accept "BLE-RID live + USB-tethered full" as the
  ceiling; do not design around a capability that does not exist.
- **DJI ecosystem lock-in** — the protocol can shift between firmware releases; every FW bump
  requires re-validation against a fresh capture baseline.
- **Legal** — interoperability RE is defensible under 2009/24/EC Art. 6; *operating* a modified
  regulatory / RF state in PL/EU is not, and stays behind formal tasking. Passive RID and RSSI
  monitoring is clean. Experimental RF work follows the separate Art. 153 PKE authorization track.
- **USB-stack fragility** — Android USB-host sessions disconnect mid-flight-of-work; they need the
  same hard-reset recovery discipline already proven on the Windows path.

---

## 9. Sample pseudocode

### 9.1 DUML frame parse (core)
```
fn parse_frame(buf) -> Option<Frame>:
    find 0x55 at i
    length = buf[i+1] | ((buf[i+2] & 0x03) << 8)
    require 13 <= length <= 1023 and i + length <= len(buf)
    require crc8(buf[i..i+3]) == buf[i+3]                     # poly 0x8C, init 0x77
    require crc16(buf[i..i+length-2]) == le16(buf[i+length-2..])   # poly 0x8408, init 0x3692
    return Frame {
        src, dst, seq, flags, cmd_set, cmd_id,
        payload = buf[i+11 .. i+length-2]
    }
```

### 9.2 OSD `0x43` push-stream consumer (live telemetry, tethered)
```
loop:                                        # 0x43 is a PUSH stream — never send_and_wait
    frame = rx_queue.pop()
    if (frame.cmd_set, frame.cmd_id) != (0x03, 0x43): continue
    p = frame.payload
    sample = TelemetrySample {
        lat      = i32_le(p, LAT_OFF)  * 1e-7,     # byte offsets: validate against capture #69
        lon      = i32_le(p, LON_OFF)  * 1e-7,
        alt_m    = i16_le(p, ALT_OFF)  * 0.1,
        vx,vy,vz = i16_le(p, ...)      * 0.01,     # m/s
        n_sats   = u8(p, SATS_OFF),
        battery  = u8(p, BATT_OFF),                # percent
    }
    emit(sample)                                   # -> Flow -> UI
```

### 9.3 Remote ID Location message (live, phone-only, passive)
```
on_ble_advertisement(adv):
    sd = adv.service_data[0xFFFA]
    if sd is None: return
    msg_type = sd[0] >> 4
    if msg_type != 0x1: return                     # 1 = Location / Vector
    lat    = i32_le(sd, 5)  * 1e-7
    lon    = i32_le(sd, 9)  * 1e-7
    alt_p  = (i16_le(sd, 15) * 0.5) - 1000         # pressure altitude, m
    height = (i16_le(sd, 17) * 0.5) - 1000         # AGL, m
    spd    = decode_speed(sd[3])                   # scaled per spec table
    track  = decode_track(sd, 2)                   # deg
    emit_live(lat, lon, height, spd, track, rssi = adv.rssi)
```

### 9.4 Regulatory SET — gated (illustrative; ships without FCC values)
```
fn set_regulatory(idx, value, task_id):
    require task_id is OPEN                         # hard gate, no bypass
    audit("regulatory_set_attempt", idx, value, task_id, ts)
    if dry_run: return DRY
    resp = duml_set(idx, value)                     # value from vetted DB, not from the app
    audit("regulatory_set_result", idx, resp.status, task_id, ts)
```

---

## 10. Phased roadmap (hardware-verify before advancing)

- **A0** — Rust core compiles to an Android `.so`; unit-test the codec against capture `#69`.
- **A1** — BLE Remote ID scanner + live decode → dashboard, no drone tether. First real live data.
- **A2** — USB-host transport on the phone; replicate init + `0x43` OSD push consume, verified
  against the Windows capture.
- **A3** — Room persistence + CSV/JSON export + `audit.jsonl`.
- **A4** — MapLibre offline/online + track and RSSI overlays.
- **A5** — Flight-log parser (post-flight DAT/txt).
- **A6** — Read-only parameter browser; regulatory family locked behind the task-ID gate.

Each phase gates on a confirmed hardware test before the next begins.

```

