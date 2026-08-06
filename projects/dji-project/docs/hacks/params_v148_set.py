#!/usr/bin/env python3
"""
duml/params_v148_set.py - dekoder ramek SET dla protokołu Drone Hacks v1.48.

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
