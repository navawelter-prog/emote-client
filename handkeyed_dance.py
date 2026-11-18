#!/usr/bin/env python3
"""
Erzeugt dance.emote aus von Hand abgelesenen Posen (alle 0,25 s ein Schluesselbild),
die aus dem Tanzvideo abgeleitet wurden. Das ist eine Annaeherung.
Fuer eine genaue Erfassung: video_to_emote.py mit dem MediaPipe-Modell benutzen.

Benutzung:  python handkeyed_dance.py ../src/main/resources/assets/emoteclient/emotes/dance.emote
Benoetigt:  numpy, scipy
"""
import math
import sys

import numpy as np
from scipy.interpolate import PchipInterpolator

FPS = 15
KEY_DT = 0.25  # Sekunden zwischen den abgelesenen Posen

# Richtungen im Koerper des Tanzenden: (nach aussen, nach oben, nach vorn)
ARM = {
    "DOWN": (0.12, -1.0, 0.0),
    "OUT_LOW": (0.7, -0.7, 0.05),
    "OUT_SIDE": (0.95, 0.3, 0.25),
    "REACH_OUT": (0.8, 0.45, 0.25),
    "JAZZ_UP": (0.65, 0.55, 0.15),
    "HAIR": (0.25, 0.8, 0.45),
    "CHEST": (-0.25, -0.45, 0.85),
    "COLLAR": (-0.15, -0.1, 0.95),
    "STOMACH": (-0.45, -0.7, 0.65),
    "HIP": (0.55, -0.8, -0.1),
}
LEG_STAND = (0.05, -1.0, 0.0)
LEG_WIDE = (0.3, -0.95, 0.0)
LEG_KICK = (0.15, -0.5, -0.85)

# (rechter Arm, linker Arm, Bein-Aktion, breiter Stand)   KL = linkes Bein kickt nach hinten
FRAMES = [
    ("OUT_LOW", "OUT_SIDE", "", 0), ("DOWN", "HIP", "KL", 0), ("CHEST", "COLLAR", "", 0),
    ("HIP", "HAIR", "KR", 0), ("DOWN", "OUT_SIDE", "", 0), ("HIP", "OUT_SIDE", "KL", 0),
    ("STOMACH", "STOMACH", "", 1), ("CHEST", "HAIR", "KR", 0), ("STOMACH", "STOMACH", "", 1),
    ("CHEST", "STOMACH", "KL", 1), ("HIP", "REACH_OUT", "KL", 0), ("DOWN", "DOWN", "", 1),
    ("CHEST", "CHEST", "", 1), ("CHEST", "CHEST", "KR", 0), ("CHEST", "CHEST", "", 0),
    ("STOMACH", "CHEST", "KL", 0), ("OUT_SIDE", "OUT_SIDE", "KR", 0), ("JAZZ_UP", "JAZZ_UP", "", 0),
    ("OUT_LOW", "OUT_LOW", "KL", 0), ("DOWN", "DOWN", "", 0), ("CHEST", "CHEST", "KR", 0),
    ("COLLAR", "STOMACH", "", 0), ("CHEST", "HIP", "KL", 0), ("CHEST", "CHEST", "KL", 0),
    ("COLLAR", "HAIR", "", 1), ("CHEST", "CHEST", "KR", 0), ("CHEST", "STOMACH", "", 0),
    ("CHEST", "CHEST", "KL", 0), ("CHEST", "CHEST", "", 0), ("CHEST", "CHEST", "KR", 0),
    ("STOMACH", "CHEST", "", 0),
]


def to_model(vec, side):
    """(aussen, oben, vorn) -> Modellachsen. side: -1 rechte Koerperseite, +1 linke."""
    out, up, fwd = vec
    return np.array([side * out, -up, -fwd])


def limb(v):
    v = v / np.linalg.norm(v)
    return [math.asin(max(-1.0, min(1.0, v[2]))), 0.0, math.atan2(-v[0], v[1])]


def build_keys():
    keys = []
    for i, (ra, la, act, wide) in enumerate(FRAMES):
        stand = LEG_WIDE if wide else LEG_STAND
        r_leg = LEG_KICK if act == "KR" else stand
        l_leg = LEG_KICK if act == "KL" else stand
        # Oberkoerper dreht leicht zur Seite des Kicks, Kopf neigt sich im Wechsel
        twist = 0.22 if act == "KL" else -0.22 if act == "KR" else 0.0
        tilt = 0.14 if i % 2 == 0 else -0.14
        row = [
            [0.04, twist * 0.8, tilt],                      # Kopf
            [0.0, twist, 0.0],                              # Koerper
            limb(to_model(ARM[ra], -1)),                    # rechter Arm
            limb(to_model(ARM[la], +1)),                    # linker Arm
            limb(to_model(r_leg, -1)),                      # rechtes Bein
            limb(to_model(l_leg, +1)),                      # linkes Bein
        ]
        keys.append(row)
    return np.array(keys)


def main(path):
    keys = build_keys()
    n = len(keys)
    for part in range(6):
        for ax in range(3):
            keys[:, part, ax] = np.unwrap(keys[:, part, ax])
    t_keys = np.arange(n) * KEY_DT
    t_out = np.arange(0, t_keys[-1] + 1e-6, 1.0 / FPS)
    out = np.zeros((len(t_out), 6, 3))
    for part in range(6):
        for ax in range(3):
            out[:, part, ax] = PchipInterpolator(t_keys, keys[:, part, ax])(t_out)

    # Uebergang vom letzten zum ersten Bild, damit die Schleife nicht springt
    k = int(round(0.4 * FPS))
    last, first = out[-1], out[0]
    first = last + (((first - last) + math.pi) % (2 * math.pi) - math.pi)
    extra = np.array([last + (first - last) * (j / (k + 1)) for j in range(1, k + 1)])
    out = np.concatenate([out, extra])

    with open(path, "w", encoding="utf-8") as f:
        f.write("# emote v1: head body right_arm left_arm right_leg left_leg (je x y z, Radiant)\n")
        f.write(f"fps {FPS}\nloop 1\n")
        for frame in out:
            f.write(" ".join(f"{v:.3f}" for v in frame.reshape(-1)) + "\n")
    print(f"{len(out)} Keyframes -> {path} ({len(out) / FPS:.1f} s)")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "dance.emote")
