# DJI Hacks — Indeks kodów i skryptów

## DJI Mini 4 Pro

### params_v148_set.py
Dekoder ramek SET dla protokołu Drone Hacks v1.48.

**Specyfikacja:**
- Reverse engineered z capture_v148_full69.pcap
- Firmware: Mini 4 Pro FW 01.00.1100
- Komunikacja: DroneHacks (src=0x03) <-> FC (dst=0x0A)

**Protokół transakcji SET:**
- DH -> FC: cmd 0xDF payload=00 (pre-set probe)
- DH -> FC: cmd 0xE3 payload=set_req (właściwy SET)
- FC -> DH: cmd 0xDF (resp) payload=01000000 (pre-set ACK)
- FC -> DH: cmd 0xE3 (resp) payload=set_resp (SET ACK + echo)

**Layout payload 0xE3:**
- Request (10/8/7B): [status:4B=0] [idx:2B] [value:Nbytes]
- Response: [status:4B] [idx:2B] [value:Nbytes]
- Status response: 0x00000001 = OK, inne wartości = błąd

**Klasy Python:**
- `SetRequest` — ramka requestu SET
- `SetResponse` — ramka response SET
- `parse_set_request()` — parser payload 0xE3
- `parse_set_response()` — parser response
- `build_set_request()` — builder requestu

**Użycie:**
```python
# Buduj request SET
payload = build_set_request(index=1104, value=30.0, size=4, is_float=True)

# Parsuj response
resp = parse_set_response(raw_payload)
if resp.is_ok:
    print(f"Parameter {resp.index} = {resp.value_float}")
```

## DJI RC2
Brak danych hacków (projekt pusty).
