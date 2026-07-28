# Wind Turbine — ComputerCraft API

← [Back to the README](../README.md) · [Integrations](integrations.md) · [Getting started](getting-started.md)

The Wind Turbine is a ComputerCraft peripheral of type `electricity_wind_turbine`.
Place a computer against the **base block** of the turbine — the model is a tall
tower, but the block entity only occupies the block at its foot — or attach a
Wired Modem to that block to reach it over network cable.

```lua
local t = peripheral.find("electricity_wind_turbine")
```

Reads are served from an immutable snapshot the turbine publishes once per tick,
so they never block and never mix values from two different ticks. Methods that
*change* the turbine run on the server thread instead, because they mark the block
entity dirty and push state to clients.

---

## Mekanism-compatible methods

These carry the names Mekanism uses on its own generators, so a program written
against a Mekanism generator reads this turbine unchanged. Energy is in Joules.

| Function | Returns | Notes |
|---|---|---|
| `getProductionRate()` | number | Joules produced in the last tick, gross |
| `getMaxOutput()` | number | ceiling on Joules handed out per tick |
| `getEnergy()` | number | Joules still unclaimed in this tick's budget |
| `getMaxEnergy()` | number | same as `getMaxOutput()` |
| `getEnergyNeeded()` | number | always `0`; the turbine is a source |
| `getEnergyFilledPercentage()` | number | `0`–`1` |
| `isBlacklistedDimension()` | boolean | always `false`; this mod has no dimension list |

One kW is 125 J/tick. The rate comes from the Power Box's own 50 FE per kW and
Mekanism's default 2.5 J per FE, so every part of the mod agrees on the scale.

## Control

| Function | Returns | Notes |
|---|---|---|
| `stop()` | – | applies the brake: rotor stops, blades feather, output goes to zero |
| `start()` | – | releases a `stop()`; does **not** override redstone or a wind cut-out |
| `isStopped()` | boolean | stopped specifically by a computer command |
| `isRunning()` | boolean | rotor turning and allowed to generate |
| `isWindCutOut()` | boolean | the machine stopped itself; the wind exceeded 22 m/s |
| `isStoppedByRedstone()` | boolean | the redstone mode and signal are holding it down |
| `getRedstoneMode()` | string | `"DISABLED"`, `"HIGH"` or `"LOW"` |
| `setRedstoneMode(mode)` | – | same names Mekanism uses; throws on anything else |
| `getActivePowerLimit()` | number | curtailment setpoint in kW |
| `setActivePowerLimit(kW)` | – | clamped to `0`–`78.75`; `0` curtails fully without braking |

Redstone modes: `DISABLED` ignores redstone, `HIGH` runs only while powered, `LOW`
runs only while unpowered. Mekanism's fourth mode, `PULSE`, is rejected rather
than silently accepted — a generator runs continuously and has nothing to pulse.

The three stop reasons are reported separately on purpose. A program that shuts a
turbine down needs to tell its own command apart from the machine protecting
itself in a gale, so `stop()` and a cut-out never look the same.

Curtailment and braking are different: a curtailed turbine keeps turning and
reports `isRunning() == true`, it just holds its output at the setpoint.

## Telemetry

`getTelemetry()` returns every signal at once as a table keyed by tag — one
tick-consistent snapshot. Each tag also has its own getter, named `get` plus the
capitalised tag: `getWindSpeed()`, `getGearBoxOilTemp()`, `getVibYDirection()`.

`getTelemetryKinds()` returns a table of tag to `"MEASURED"`, `"DERIVED"` or
`"SIMULATED"`. Check it before treating a number as ground truth.

- **MEASURED** — read from mod state or the world.
- **DERIVED** — computed from measured values by a relation that holds on a real
  machine. The relations are exact; the constants inside them (690 V, 50 Hz, a
  37.5 gearbox ratio) are chosen, not modelled.
- **SIMULATED** — instrumentation the mod does not model. Every value tracks load
  and ambient temperature through a first-order lag, reaching 63% of a step in
  about 25 seconds, so they ramp like a thermal mass. They behave correctly but
  they do not *mean* anything: there is no energy balance behind them, and **no
  failure modes**, so an alarm written against them will never fire.

### Measured — 14

| Tag | Unit | Range | Driver |
|---|---|---|---|
| `windSpeed` | m/s | 0.2 – ~45.6 | sustained wind blended with the gust in proportion to turbulence |
| `windDir` | ° | 0 – 360 | wind field direction |
| `nacelleDir` | ° | 0 – 360 | actual yaw; follows `windDir` at 0.25°/tick past a 7.5° deadband |
| `rotorRpm` | rpm | 0 – 40 | rotor speed |
| `activePower` | kW | 0 – 78.75 | see the power curve below |
| `activePowerLimit` | kW | 0 – 78.75 | curtailment setpoint |
| `powerLimitationActive` | boolean | – | braked, pitching out, or curtailed |
| `running` | boolean | – | rotor turning and generating |
| `windCutOut` | boolean | – | stopped by wind above cut-out |
| `stoppedByComputer` | boolean | – | stopped by `stop()` |
| `stoppedByRedstone` | boolean | – | held down by the redstone mode |
| `ambientTemp` | °C | −37 – +41 | biome, height, day cycle, precipitation |
| `turbulence` | – | 0.03 – 1.0 | weather model |
| `yawCableTwist` | ° | unbounded | net yaw rotation; random-walks around zero |

### Derived — 16

| Tag | Unit | Range | Relation |
|---|---|---|---|
| `generatorRpm` | rpm | 0 – 1500 | `rotorRpm × 37.5`, synchronous for 4 poles at 50 Hz |
| `pf` | – | 0 – 0.98 | `0.90 + 0.08 × load`, zero with no output |
| `apparentPower` | kVA | 0 – 80.36 | `P / PF` |
| `reactivePower` | kvar | 0 – 15.99 | `√(S² − P²)` |
| `f` | Hz | 49.96 – 50.04 | 50 nominal |
| `v12` `v23` `v31` | V | 679 – 692 | 690 nominal, −1.2% at full load, phase imbalance |
| `i1` `i2` `i3` | A | 0 – 68.5 | `S / (√3 · V)` |
| `bladePitchAngle` | ° | 0 – 90 | 0 to 12 m/s, 25° by 22, 60° by 25, 90° braked |
| `bladePitchAngle1..3` | ° | 0 – 90 | collective ± 0.25, clamped to the mechanical travel |
| `airPressure` | hPa | 951 – 1029 | falls with height, wind and weather |

### Simulated — 33

Temperatures are given as rise above ambient; each one starts at ambient on a
cold load and warms from there.

| Tag | Rise | Max |
|---|---|---|
| `generatorL1Temp` `generatorL2Temp` `generatorL3Temp` | + 0 … 110 | 151 |
| `genBearTempBS` `genBearTempDEnd` | + 0 … 75 | 116 |
| `mvTrafoTempAreaCoil` | + 0 … 75 | 116 |
| `gearBearTemp1Gen` `gearBearTemp2Rot` | + 0 … 70 | 111 |
| `gearBoxOilTemp` | + 0 … 60 | 101 |
| `gearBoxOilTempSump` | + 0 … 46 | 87 |
| `mainBearTemp1` | + 0 … 45 | 86 |
| `genCWTempGenOutlt` | + 0 … 43 | 84 |
| `airTempPwrCabPwrFld` | + 0 … 36 | 77 |
| `hydOilTemp` | + 0 … 30 | 71 |
| `airTempPwrCabCtrlFld` | + 0 … 25 | 66 |
| `genCWTempGenInlt` | + 0 … 23 | 64 |
| `nacelleTemp` | + 0 … 20 | 61 |
| `airTempCtrlCab` | + 0 … 16 | 57 |
| `pitch1MotorTemp` `pitch2MotorTemp` `pitch3MotorTemp` | + 0 … 14.5 | 55.5 |
| `pitch1BoxTemp` `pitch2BoxTemp` `pitch3BoxTemp` | + 0 … 10.5 | 51.5 |
| `airTempTowerBott` | + 0 … 7 | 48 |

Windings and the transformer scale with the **square** of apparent power, because
copper loss follows current; everything else scales linearly with load.
`genCWTempGenOutlt` is always above `genCWTempGenInlt`.

| Tag | Unit | Range | Driver |
|---|---|---|---|
| `hydrSystemPress` | bar | 188.8 – 211.2 | `190 + 20 × load` |
| `hydrMainBrakesPressure` | bar | 3.7 – 179.5 | ~4 normally, ~178 only when braked |
| `yawHAccuPress` | bar | 147 – 155 | `148 + 6 × load` |
| `yawHydrBrkPress` | bar | 11.6 – 59.4 | ~12 still, ~58 while yawing |
| `gearBoxOilPressPmp` | bar | 0 – 6.58 | zero with the rotor stopped |
| `gearBoxOilPress` | bar | 0 – 3.56 | zero with the rotor stopped |
| `vibYDirection` | mm/s | 0.28 – 4.52 | `0.4 + 1.8 × load + 2.5 × turbulence` |
| `vibZDirection` | mm/s | 0.20 – 3.66 | `0.3 + 1.5 × load + 2.0 × turbulence` |

All readings are rounded to two decimals.

---

## Power curve

### The x axis is not `windSpeed`

`getWindSpeed()` reports the **raw** wind at the nacelle. The rotor is driven by the
**aligned** wind — the component left after the yaw error — and there is no tag for
it, so a curve plotted against `windSpeed` comes out as a cloud of points rather
than a curve. The same raw speed produces different power depending on how far the
nacelle is turned away.

```
alignedWind = windSpeed × cos³(Δ),   Δ = the smaller angle between windDir and nacelleDir
```

Both headings are on a 0–360 compass, so `Δ = |windDir − nacelleDir|`, wrapped to
180 if it comes out larger.

| Yaw error Δ | cos³(Δ) | Power kept |
|---|---|---|
| 0° | 1.0000 | 100% |
| 5° | 0.9886 | 98.9% |
| 7.5° | 0.9746 | 97.5% |
| 10° | 0.9551 | 95.5% |
| 15° | 0.9012 | 90.1% |
| 20° | 0.8298 | 83.0% |
| 30° | 0.6495 | 65.0% |
| 45° | 0.3536 | 35.4% |
| 60° | 0.1250 | 12.5% |
| 75° | 0.0173 | 1.7% |
| ≥ 90° | 0 | 0% |

Yaw only corrects past a 7.5° deadband and then at 0.25°/tick, so in steady wind the
nacelle sits up to 7.5° off and gives up about 2.5% without ever correcting. Expect
that as a persistent floor on measured points, not as noise.

### Thresholds

Five of them, and they do not all read the same speed. Cut-in, rated and the storm
onset read the **aligned** wind; the shutdown latch and its re-arm read the **raw**
wind, because a machine protects itself from the wind that hits it, not from the
useful component.

| Threshold | Speed | Reads | Boundary | Behaviour |
|---|---|---|---|---|
| Cut-in | 3 m/s | aligned | generates at `v ≥ 3` | steps straight to 4.922 kW |
| Rated | 12 m/s | aligned | plateau at `v ≥ 12` | 78.750 kW, blades start pitching out |
| Storm onset | 22 m/s | aligned | full output to `v < 22` | derates, stays on load |
| Shutdown | 25 m/s | raw | brakes at `v ≥ 25` | brake on, blades feathered, latching |
| Re-arm | 20 m/s | raw | releases at `v ≤ 20` | latch clears |

21.99 m/s still gives full output; 22.00 gives 80%. 24.99 still generates; 25.00 is
braked. A latched shutdown clears only once the raw wind is back at or below 20.

Because the shutdown reads raw wind while the curve is indexed on aligned wind, a
badly yawed turbine can brake while its aligned speed is only 15 m/s or so. The
plateau will look like it ends early and at inconsistent places. That is the latch,
not noise.

### Formula

```
P(v) = 0                                    v < 3          off
P(v) = 140 · (min(v,12)/16)²                3 ≤ v < 22     rise, then plateau from 12
P(v) = 78.75 · (1 − 0.2·(v − 21))           22 ≤ v < 25    storm derating
P(v) = 0                                    v ≥ 25         braked
```

`v` is the aligned wind in m/s, `P` in kW. `140 · (12/16)² = 78.75`, the rated
output.

The derating sheds a fifth of rated per m/s, landing on 80%, 60% and 40% at 22, 23
and 24. It is a continuous ramp rather than a step per whole m/s: stepping would put
three fresh discontinuities into the curve, which is what derating exists to avoid.

Three discontinuities remain, all deliberate:

- **at 3** — 0 jumps to 4.922 kW as the generator connects. Do not interpolate
  between 2.9 and 3.0.
- **at 22** — the plateau drops to 80%, 78.750 → 63.000, because the ramp starts at
  80% rather than 100%.
- **at 25** — the brake takes it from about 19.7 kW to zero.

Curtailment applies **after** the derating, as a floor-taking minimum: at 22 m/s with
`setActivePowerLimit(50)` the output is `min(63.000, 50) = 50`.

### Values

At zero yaw error, which is how a power curve is conventionally specified.
`descending` is the branch after a latched shutdown: the latch clears only at 20, so
coming down from a storm the whole 20–25 band reads zero and the derating band is
never traversed downward.

| v (m/s) | kW | % rated | derate | descending |
|---|---|---|---|---|
| 0.0 – 2.5 | 0 | 0% | — | 0 |
| 3.0 | 4.922 | 6.25% | 1.00 | 4.922 |
| 3.5 | 6.699 | 8.51% | 1.00 | 6.699 |
| 4.0 | 8.750 | 11.11% | 1.00 | 8.750 |
| 4.5 | 11.074 | 14.06% | 1.00 | 11.074 |
| 5.0 | 13.672 | 17.36% | 1.00 | 13.672 |
| 5.5 | 16.543 | 21.01% | 1.00 | 16.543 |
| 6.0 | 19.688 | 25.00% | 1.00 | 19.688 |
| 6.5 | 23.105 | 29.34% | 1.00 | 23.105 |
| 7.0 | 26.797 | 34.03% | 1.00 | 26.797 |
| 7.5 | 30.762 | 39.06% | 1.00 | 30.762 |
| 8.0 | 35.000 | 44.44% | 1.00 | 35.000 |
| 8.5 | 39.512 | 50.17% | 1.00 | 39.512 |
| 9.0 | 44.297 | 56.25% | 1.00 | 44.297 |
| 9.5 | 49.355 | 62.67% | 1.00 | 49.355 |
| 10.0 | 54.688 | 69.44% | 1.00 | 54.688 |
| 10.5 | 60.293 | 76.56% | 1.00 | 60.293 |
| 11.0 | 66.172 | 84.03% | 1.00 | 66.172 |
| 11.5 | 72.324 | 91.84% | 1.00 | 72.324 |
| 12.0 – 20.0 | 78.750 | 100% | 1.00 | 78.750 |
| 20.5 – 21.5 | 78.750 | 100% | 1.00 | **0** |
| 22.0 | 63.000 | 80% | 0.80 | 0 |
| 22.5 | 55.125 | 70% | 0.70 | 0 |
| 23.0 | 47.250 | 60% | 0.60 | 0 |
| 23.5 | 39.375 | 50% | 0.50 | 0 |
| 24.0 | 31.500 | 40% | 0.40 | 0 |
| 24.5 | 23.625 | 30% | 0.30 | 0 |
| 25.0 and above | 0 | 0% | 0.00 | 0 |

Half of rated falls at 8.5 m/s. Telemetry rounds to two decimals, so measured values
will not match the third decimal here.

<details>
<summary>Same data as CSV, 0.5 m/s bins</summary>

```csv
wind_ms,power_kw,percent_rated,derating,power_kw_descending
0.0,0.000,0.00,1.00,0.000
0.5,0.000,0.00,1.00,0.000
1.0,0.000,0.00,1.00,0.000
1.5,0.000,0.00,1.00,0.000
2.0,0.000,0.00,1.00,0.000
2.5,0.000,0.00,1.00,0.000
3.0,4.922,6.25,1.00,4.922
3.5,6.699,8.51,1.00,6.699
4.0,8.750,11.11,1.00,8.750
4.5,11.074,14.06,1.00,11.074
5.0,13.672,17.36,1.00,13.672
5.5,16.543,21.01,1.00,16.543
6.0,19.688,25.00,1.00,19.688
6.5,23.105,29.34,1.00,23.105
7.0,26.797,34.03,1.00,26.797
7.5,30.762,39.06,1.00,30.762
8.0,35.000,44.44,1.00,35.000
8.5,39.512,50.17,1.00,39.512
9.0,44.297,56.25,1.00,44.297
9.5,49.355,62.67,1.00,49.355
10.0,54.688,69.44,1.00,54.688
10.5,60.293,76.56,1.00,60.293
11.0,66.172,84.03,1.00,66.172
11.5,72.324,91.84,1.00,72.324
12.0,78.750,100.00,1.00,78.750
12.5,78.750,100.00,1.00,78.750
13.0,78.750,100.00,1.00,78.750
13.5,78.750,100.00,1.00,78.750
14.0,78.750,100.00,1.00,78.750
14.5,78.750,100.00,1.00,78.750
15.0,78.750,100.00,1.00,78.750
15.5,78.750,100.00,1.00,78.750
16.0,78.750,100.00,1.00,78.750
16.5,78.750,100.00,1.00,78.750
17.0,78.750,100.00,1.00,78.750
17.5,78.750,100.00,1.00,78.750
18.0,78.750,100.00,1.00,78.750
18.5,78.750,100.00,1.00,78.750
19.0,78.750,100.00,1.00,78.750
19.5,78.750,100.00,1.00,78.750
20.0,78.750,100.00,1.00,78.750
20.5,78.750,100.00,1.00,0.000
21.0,78.750,100.00,1.00,0.000
21.5,78.750,100.00,1.00,0.000
22.0,63.000,80.00,0.80,0.000
22.5,55.125,70.00,0.70,0.000
23.0,47.250,60.00,0.60,0.000
23.5,39.375,50.00,0.50,0.000
24.0,31.500,40.00,0.40,0.000
24.5,23.625,30.00,0.30,0.000
25.0,0.000,0.00,0.00,0.000
25.5,0.000,0.00,0.00,0.000
26.0,0.000,0.00,0.00,0.000
```

</details>

### In Lua

The closed form is exact everywhere, so prefer it over interpolating the bins:

```lua
local function powerAt(v)                        -- v = aligned wind in m/s
  if v < 3 or v >= 25 then return 0.0 end
  local n = math.min(v, 12) / 16
  local p = 140 * n * n
  if v >= 22 then p = p * (1 - 0.2 * (v - 21)) end
  return p
end

local function alignedWind(d)                    -- d = getTelemetry()
  local delta = math.abs(d.windDir - d.nacelleDir)
  if delta > 180 then delta = 360 - delta end
  if delta >= 90 then return 0 end
  local c = math.cos(math.rad(delta))
  return d.windSpeed * c * c * c
end
```

### Measuring it in game

Four things will falsify a measured curve:

1. A curtailment setpoint below rated clips the top. Call `setActivePowerLimit(78.75)`
   before collecting.
2. Samples with `running == false` have to be discarded, or the brake and any
   commanded stop pollute the zero.
3. Bin on `alignedWind(d)`, never on `d.windSpeed`.
4. Ascending and descending samples belong on separate branches between 20 and 25,
   because of the latch.

The weather model moves the wind slowly, so filling the 3–12 m/s range takes a long
time in real terms, and anything above 22 needs a storm.

### Known deviation from real physics

Wind power goes as the cube of speed, `P = ½ρAv³Cp`. This curve is **quadratic**, so
below rated it is much fuller than a real machine's:

| v | this mod (v²) | cubic law, same rated point | difference |
|---|---|---|---|
| 4 | 8.75 | 2.92 | +5.83 |
| 6 | 19.69 | 9.84 | +9.84 |
| 8 | 35.00 | 23.33 | +11.67 |
| 10 | 54.69 | 45.57 | +9.11 |
| 12 | 78.75 | 78.75 | 0 |

That is the mod's own choice, inherited from the original generation formula, not
something the integration introduced.

---

## Examples

Live dashboard:

```lua
local t = peripheral.find("electricity_wind_turbine")
while true do
  term.clear()
  term.setCursorPos(1, 1)
  local d = t.getTelemetry()
  print(("%-8s %s"):format("state", d.running and "running" or "stopped"))
  print(("%-8s %.1f kW / %.1f"):format("power", d.activePower, d.activePowerLimit))
  print(("%-8s %.1f rpm -> %.0f rpm"):format("rotor", d.rotorRpm, d.generatorRpm))
  print(("%-8s %.1f m/s at %.0f deg"):format("wind", d.windSpeed, d.windDir))
  print(("%-8s %.0f deg"):format("nacelle", d.nacelleDir))
  print(("%-8s %.1f deg"):format("pitch", d.bladePitchAngle))
  print(("%-8s %.1f C oil, %.1f C L1"):format("temp", d.gearBoxOilTemp, d.generatorL1Temp))
  sleep(1)
end
```

Stop every turbine on the network when the wind gets rough, and release them when
it settles:

```lua
for _, name in ipairs(peripheral.getNames()) do
  if peripheral.getType(name) == "electricity_wind_turbine" then
    local t = peripheral.wrap(name)
    if t.getTurbulence() > 0.7 then t.stop() else t.start() end
  end
end
```

Curtail the farm to a total setpoint:

```lua
local turbines = { peripheral.find("electricity_wind_turbine") }
local target = 200                                  -- kW for the whole farm
for _, t in ipairs(turbines) do
  t.setActivePowerLimit(target / #turbines)
end
```

Only alarm on things that can actually happen. The simulated temperatures have no
failure modes and will never run away, so a watchdog belongs on the measured
signals:

```lua
local d = t.getTelemetry()
if d.windCutOut then print("shut down: wind above cut-out") end
if math.abs(d.windDir - d.nacelleDir) > 30 then print("yaw not tracking") end
if math.abs(d.yawCableTwist) > 720 then print("cable twist: two turns") end
```
