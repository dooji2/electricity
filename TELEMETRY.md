# Wind Turbine — ComputerCraft API

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
| `activePower` | kW | 0 – 78.75 | zero below 3 m/s, plateau from 12, zero above 22 |
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
| `bladePitchAngle` | ° | 0 – 90 | 0 up to 12 m/s, ramps to 25 by 22, 90 when braked |
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
