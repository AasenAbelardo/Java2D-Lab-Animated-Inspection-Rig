# Inspection Rig

Aasen Sofia Abelardo

A rail-mounted inspection rig drawn with Java 2D. The carriage is local geometry placed by an `AffineTransform`. The rotor is a child transform that spins and scales about its hub. The tether is an integer Bresenham line from the fixed beacon to the rounded rotor tip.

## Build and run

```bash
javac InspectionRig.java
java InspectionRig
```

Uses only the Java standard library. One source file: `InspectionRig.java`.

## Controls

- Space pauses or resumes
- R resets time to 0 and resumes
- 1, 2, 3, 4 pause at t = 0, 1, 4, and 6 seconds

The drawing area is 720 × 440. The panel shows the wrapped time and the rounded tip coordinates.

## Observed tip coordinates

These come from the same rotor transform that draws the blade.

| Key | Time | Rig anchor | Observed tip | Expected tip |
|-----|------|------------|--------------|--------------|
| 1 | 0 s | (160, 220) | (200, 182) | (200, 182) |
| 2 | 1 s | (260, 220) | (260, 227) | (260, 227) |
| 3 | 4 s | (560, 220) | (620, 182) | (620, 182) |
| 4 | 6 s | (360, 220) | (310, 182) | (310, 182) |

Screenshots: `screenshot_1s.png` (t = 1 s) and `screenshot_6s.png` (t = 6 s).

## Bresenham checks

| Segment | Expected count | Actual count | Endpoints |
|---------|----------------|--------------|-----------|
| (20, 20) to (27, 23) | 8 | 8 | (20, 20) → (27, 23) |
| reverse (27, 23) to (20, 20) | 8 | 8 | (27, 23) → (20, 20) |
| (30, 15) to (26, 28) | 14 | 14 | (30, 15) → (26, 28) |
| reverse (26, 28) to (30, 15) | 14 | 14 | (26, 28) → (30, 15) |
| (5, 5) to (5, 5) | 1 | 1 | (5, 5) |

Shallow pixels: (20,20) (21,20) (22,21) (23,21) (24,22) (25,22) (26,23) (27,23). The reverse visits that same set and keeps both endpoints.

Steep pixels: (30,15) (30,16) (29,17) (29,18) (29,19) (28,20) (28,21) (28,22) (28,23) (27,24) (27,25) (27,26) (26,27) (26,28). The reverse is the same pixels in the opposite order.

Plotting each segment with `fillRect(x, y, 1, 1)` produced the same counts: 8, 8, 14, 14, and 1.

## Transform order

The carriage transform is only a translation to (160 + 400p, 220), so the housing and wheels stay upright. The rotor transform is that translation composed with a move to the hub (0, −38), then a clockwise rotation of 90t degrees and a uniform scale of 1 + 0.5p. Scale and rotation run while the hub is still the local origin, so the tip orbits the hub and the hub point itself does not move; that same composite maps rotor-local (40, 0) to the screen tip, which is rounded for the tether.
