# DieselOBD recorded-data replay (LoToTi field-test build)

## Data source, licensing and attribution

**DieselOBD / OBD-Dataset**, Karim Lounis, Hadjer Mekki and Wissam Hadjab. Dataset: https://github.com/AbouAbdallah-Lounis/OBD-Dataset . CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . Cite: *DieselOBD: A Real-World Dataset of OBD-II Parameters and Diagnostic Trouble Codes for Diesel Vehicle Fault Diagnosis* (2025). Attribution and license also appear in the app.

LoToTi uses `MASTER_TEST_X.xlsx`, SHA-256 `9f4047f5964a450e03fa96de4b4927c09171998713e4222e5285cae3a0c1b1d4`. The source is **not modified**; 320 evenly spaced samples per label-contiguous block were taken without changing row order and compacted into gzip/Base64 Java source in `RecordedVehicleReplay.java`. No raw private VINs, calibration IDs or timestamped location traces are distributed. Dataset labels are not evidence of a vehicle currently connected to the app.

## Mapping and limitations

- The test spreadsheet has 5 disjoint labeled fault groups mapped using exact combinations of DTC columns: Peugeot Expert 2014 (`P0113 P0102 P0234 P0406`), Chevrolet Captiva 2011 (`P0107 P0069 P0089`), Kia Sportage 2017 (`P0403 P0404 P2015 P2009`), Citroën Berlingo 2014 (`P0113 P0102 P2562`), Peugeot 2008 2022 (`P0562`). Source README lists Peugeot `P1351` as well, absent from processed columns; it is retained as *documented reported DTC* rather than a measured label.
- A separate block with **no fault labels** is offered as an **unattributed healthy reference**, not falsely assigned to any of the three healthy vehicle makes in the README. `P0000` labels are not assumed to name a particular car.
- The processed files have **no timestamps, VINs, calibration IDs, actual ECU names, unique vehicle IDs, verified ignition states, pending/permanent distinctions, recorded freeze frames, and no real operating history in seconds**. Playback ticks **every 500 ms (invented clock)** and loops over source-row ordering. This is **not time-accurate reproduction**, nor proof the original split preserves exact acquisition order.
- Heuristic unit conversions based on numeric magnitude: ECT/IAT/AAT `°F → °C`; MAP/BARO `psi → kPa`; VSS `mph → km/h`; MAF `lb/s → g/s`. The README's nominal units conflict with observed values, so **these conversions are INFERRED, not verified against source metadata**. LOAD_PCT, RPM and VPWR are passed unchanged subject to OBD encoding quantization. The FRP column is present but **not transmitted** because its scale cannot be trusted. Unknown fuel level, STFT, throttle, oil temperature, vehicle mileage and engine runtime remain unavailable rather than invented. The generic physics model remains available separately.
- There is **no measured MIL/readiness state**; the synthetic Mode 01 status carries the published DTC count but the app masks its dummy MIL/readiness fields and reports MIL as **unknown**. Only confirmed-code responses are generated; pending/permanent are empty because status is not documented. Freeze-frame data are deliberately absent; documented codes cannot be erased from a replay.
- The VIN `L0T0T` prefix and ECU/calibration identifiers are synthetic emulator markers. They are **not true vehicle VINs**, and the app AI explicitly sees dataset-replay provenance.

## Reproducibility

The generated file is deterministic, from evenly spaced row indices `round(i*(N-1)/319)` across each contiguous labeled block from `MASTER_TEST_X.xlsx`, for `i` in `0..319`. Reconstructed data pass through the regular AndrOBD PID decoder. Tests check raw-row counts, fault-group profiles, supported PID boundaries, lack of fabricated freeze frames, and preservation of the original generic demo.

If the owner needs original capture timing or certified units, acquire the original session metadata and adjust the mapping before treating this as a high-fidelity bench replay.
